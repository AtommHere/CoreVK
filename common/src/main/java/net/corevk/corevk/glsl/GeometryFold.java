package net.corevk.corevk.glsl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GeometryFold {

	
	private static final String RATE = "ofEdgeRate";

	
	private static final List<String> RATE_NAMES =
			List.of(RATE, "ofAx", "ofAy", "ofBx", "ofBy", "ofXx", "ofXy", "ofYy");

	
	private static final Set<String> QUALIFIERS = Set.of("flat", "smooth", "noperspective",
			"centroid", "highp", "mediump", "lowp");

	
	private static final Set<String> RATE_OF = Set.of("float", "vec2", "vec3", "vec4");

	
	private static final Set<String> RATE_OVER = Set.of("vec2", "vec3", "vec4");

	
	private static final String[] EDGE = {"abs", "(", null, "[", null, "]", "-", null, "[", null, "]",
		")", "/", "distance", "(", null, "[", null, "]", ",", null, "[", null, "]", ")"};

	
	private static final int RATE_TOKENS = 4 + 3 * EDGE.length + 4;

	private GeometryFold() {
	}

	
	public record Result(String fragment, String refusal) {

		public static Result refused(String refusal) {
			return new Result(null, refusal);
		}

		public boolean folded() {
			return this.fragment != null;
		}
	}

	
	public static Result fold(String geometry, String fragment) {
		try {
			return new Result(rewrite(new Stage(new Code(geometry)), new Code(fragment)), null);
		} catch (Refused refused) {
			return Result.refused(refused.getMessage());
		}
	}

	
	private static final class Refused extends Exception {

		private Refused(String clause) {
			super(clause, null, false, false);
		}
	}

	
	private record Varying(String type, List<String> qualifiers) {
	}

	
	private record Rate(String type, String of, String over, String overType) {
	}

	
	private record Edge(String of, String over, int first, int second) {
	}

	
	private record Statement(int start, int end, int body) {
	}

	
	private record Edit(int from, int to, String text) {
	}

	
	private static final class Code {

		private final List<GlslLexer.Token> all;

		private final List<Integer> at = new ArrayList<>();

		private Code(String source) {
			this.all = GlslLexer.lex(source);
			for (int index = 0; index < this.all.size(); index++) {
				GlslLexer.Token token = this.all.get(index);
				if (token.directive() == null && !token.trivia()
						&& token.kind() != GlslLexer.Kind.NEWLINE) {
					this.at.add(index);
				}
			}
		}

		private int size() {
			return this.at.size();
		}

		private String text(int index) {
			return (index >= 0 && index < this.at.size()) ? this.all.get(this.at.get(index)).text() : "";
		}

		private boolean is(int index, String text) {
			return text(index).equals(text);
		}

		private boolean identifier(int index) {
			return index >= 0 && index < this.at.size()
					&& this.all.get(this.at.get(index)).kind() == GlslLexer.Kind.IDENTIFIER;
		}

		
		private int closing(int open) {
			String opening = text(open);
			String close = switch (opening) {
				case "(" -> ")";
				case "[" -> "]";
				case "{" -> "}";
				default -> "";
			};
			int depth = 0;
			for (int index = open; index < this.at.size(); index++) {
				if (is(index, opening)) {
					depth++;
				} else if (is(index, close)) {
					depth--;
					if (depth == 0) {
						return index;
					}
				}
			}

			return -1;
		}

		
		private String join(int from, int to) {
			StringBuilder text = new StringBuilder();
			for (int index = from; index < to; index++) {
				if (index > from) {
					text.append(' ');
				}

				text.append(text(index));
			}

			return text.toString();
		}

		
		private String source(int from, int to) {
			StringBuilder text = new StringBuilder();
			for (int index = this.at.get(from); index < this.at.get(to); index++) {
				text.append(this.all.get(index).text());
			}

			return text.toString();
		}

		
		private Map<String, String> uniforms() throws Refused {
			Map<String, String> uniforms = new HashMap<>();
			for (Statement statement : fileScope()) {
				int uniform = statement.start();
				while (uniform < statement.end() && !is(uniform, "uniform") && !is(uniform, "{")) {
					uniform++;
				}

				if (statement.body() >= 0 || !is(uniform, "uniform")) {
					continue;
				}

				if (identifier(uniform + 1) && is(uniform + 2, "{")) {
					int close = closing(uniform + 2);
					if (close + 1 != statement.end()) {
						continue;
					}

					int member = uniform + 3;
					while (member < close) {
						if (identifier(member) && identifier(member + 1) && is(member + 2, ";")) {
							uniforms.put(text(member + 1), text(member));
						}

						while (member < close && !is(member, ";")) {
							member++;
						}

						member++;
					}
				} else if (identifier(uniform + 1) && identifier(uniform + 2)
						&& uniform + 3 == statement.end()) {
					uniforms.put(text(uniform + 2), text(uniform + 1));
				}
			}

			return uniforms;
		}

		
		private List<Statement> fileScope() throws Refused {
			List<Statement> statements = new ArrayList<>();
			int start = 0;
			int index = 0;
			while (index < size()) {
				String text = text(index);
				if (text.equals("(") || text.equals("[") || text.equals("{")) {
					int close = closing(index);
					if (close < 0) {
						throw new Refused("leaves a bracket open");
					}

					if (text.equals("{") && index > start && is(index - 1, ")")) {
						statements.add(new Statement(start, close, index));
						start = close + 1;
					}

					index = close + 1;
					continue;
				}

				if (text.equals(";")) {
					if (index > start) {
						statements.add(new Statement(start, index, -1));
					}

					start = index + 1;
				}

				index++;
			}

			return statements;
		}
	}

	
	private static final class Stage {

		private final Code code;

		private final Map<String, Varying> inputs = new LinkedHashMap<>();

		private final Map<String, Varying> outputs = new LinkedHashMap<>();

		
		private final Map<String, String> copies = new LinkedHashMap<>();

		
		private final Set<String> wide = new LinkedHashSet<>();

		
		private final Map<String, Rate> rates = new LinkedHashMap<>();

		
		private String counter;

		
		private String positionLocal;

		
		private final List<String> prologue = new ArrayList<>();

		
		private final Map<String, String> uniforms;

		
		private final Map<String, String> reads = new LinkedHashMap<>();

		
		private final Set<String> declared = new HashSet<>();

		private Stage(Code code) throws Refused {
			this.code = code;
			this.uniforms = code.uniforms();
			boolean triangles = false;
			int vertices = -1;
			int body = -1;
			int end = -1;
			for (Statement statement : code.fileScope()) {
				int start = statement.start();
				if (!code.is(start + 1, "main")) {
					declare(statement);
				}

				if (statement.body() >= 0) {
					if (code.is(start + 1, "main")) {
						if (body >= 0 || !code.is(start, "void") || !code.is(start + 2, "(")
								|| !code.is(start + 3, ")") || statement.body() != start + 4) {
							throw new Refused("declares its main in a shape the fold does not read");
						}

						body = statement.body() + 1;
						end = statement.end();
					}

					continue;
				}

				if (code.is(start, "layout")) {
					int close = code.closing(start + 1);
					if (!code.is(start + 1, "(") || close < 0 || close >= statement.end()) {
						throw new Refused("writes a layout the fold does not read");
					}

					String storage = code.join(close + 1, statement.end());
					if (storage.equals("in")) {
						if (!code.join(start + 2, close).equals("triangles")) {
							throw new Refused("takes something other than triangles in");
						}

						triangles = true;
					} else if (storage.equals("out")) {
						vertices = strip(code.join(start + 2, close));
					} else if (storage.startsWith("in ") || storage.startsWith("out ")
							|| storage.contains(" in ") || storage.contains(" out ")) {
						throw new Refused("places a varying by layout");
					}

					continue;
				}

				varying(statement);
			}

			if (!triangles) {
				throw new Refused("takes something other than triangles in");
			}

			if (vertices != 3) {
				throw new Refused("emits a strip of other than the three corners it takes in");
			}

			if (body < 0) {
				throw new Refused("has no main the fold can find");
			}

			main(body, end);
		}

		
		private static int strip(String layout) {
			boolean strip = false;
			int vertices = -1;
			for (String argument : layout.split(",", -1)) {
				String written = argument.trim();
				if (written.equals("triangle_strip")) {
					strip = true;
				} else if (written.matches("max_vertices = [0-9]{1,9}")) {
					vertices = Integer.parseInt(written.substring(written.lastIndexOf(' ') + 1));
				} else {
					return -1;
				}
			}

			return strip ? vertices : -1;
		}

		
		private void declare(Statement statement) {
			int start = statement.start();
			if (this.code.is(start, "struct")) {
				this.declared.add(this.code.text(start + 1));
				return;
			}

			for (int index = start; index < statement.end(); index++) {
				if (this.code.is(index, "=") || this.code.is(index, "{")) {
					return;
				}

				if (this.code.is(index, "(")) {
					if (this.code.identifier(index - 1)) {
						this.declared.add(this.code.text(index - 1));
					}

					return;
				}
			}
		}

		
		private static boolean plain(Varying varying) {
			List<String> qualifiers = varying.qualifiers();
			return !qualifiers.contains("flat") && !qualifiers.contains("noperspective")
					&& !qualifiers.contains("centroid");
		}

		
		private void varying(Statement statement) throws Refused {
			int start = statement.start();
			int end = statement.end();
			int storage = -1;
			for (int index = start; index < end; index++) {
				if (this.code.is(index, "(")) {
					return;
				}

				if (storage < 0 && (this.code.is(index, "in") || this.code.is(index, "out"))) {
					storage = index;
				}
			}

			if (storage < 0) {
				return;
			}

			List<String> qualifiers = new ArrayList<>();
			for (int index = start; index < storage; index++) {
				if (!QUALIFIERS.contains(this.code.text(index))) {
					throw new Refused("declares a varying in a shape the fold does not read");
				}

				qualifiers.add(this.code.text(index));
			}

			int name = storage + 2;
			if (!this.code.identifier(storage + 1) || !this.code.identifier(name)) {
				throw new Refused("declares a varying in a shape the fold does not read");
			}

			boolean input = this.code.is(storage, "in");
			boolean unsized = this.code.is(name + 2, "]") && name + 3 == end;
			boolean sized = this.code.is(name + 2, "3") && this.code.is(name + 3, "]")
					&& name + 4 == end;
			boolean shaped = input
					? this.code.is(name + 1, "[") && (unsized || sized)
					: name + 1 == end;
			if (!shaped) {
				throw new Refused("declares a varying in a shape the fold does not read");
			}

			Varying varying = new Varying(this.code.text(storage + 1), List.copyOf(qualifiers));
			if ((input ? this.inputs : this.outputs).put(this.code.text(name), varying) != null) {
				throw new Refused("declares " + this.code.text(name) + " twice");
			}
		}

		
		private void main(int body, int end) throws Refused {
			int index = body;
			while (!this.code.is(index, "for")) {
				if (index >= end) {
					throw new Refused("never loops over its three corners");
				}

				int semicolon = statementEnd(index, end);
				before(index, semicolon);
				index = semicolon + 1;
			}

			index = loop(index, end);
			if (!this.code.is(index, "EndPrimitive") || !this.code.is(index + 1, "(")
					|| !this.code.is(index + 2, ")") || !this.code.is(index + 3, ";")
					|| index + 4 != end) {
				throw new Refused("does more after its loop than end the primitive");
			}

			for (String output : this.outputs.keySet()) {
				if (!this.copies.containsKey(output) && !this.wide.contains(output)) {
					throw new Refused("never writes " + output);
				}
			}
		}

		
		private int statementEnd(int from, int end) throws Refused {
			for (int index = from; index < end; index++) {
				if (this.code.is(index, "(") || this.code.is(index, "[")) {
					index = this.code.closing(index);
					if (index < 0) {
						break;
					}
				} else if (this.code.is(index, ";")) {
					return index;
				} else if (this.code.is(index, "{")) {
					break;
				}
			}

			throw new Refused("does more before its loop than work out a value for the whole triangle");
		}

		
		private void before(int from, int semicolon) throws Refused {
			// The counter of the loop that follows, declared without a value: it holds nothing yet.
			if (this.counter == null && this.code.is(from, "int") && this.code.identifier(from + 1)
					&& from + 2 == semicolon) {
				this.counter = this.code.text(from + 1);

				return;
			}

			if (this.code.identifier(from) && this.code.identifier(from + 1)
					&& this.code.is(from + 2, "=")) {
				String type = this.code.text(from);
				String name = this.code.text(from + 1);
				Rate rate = rate(from + 3, semicolon);
				if (rate == null || !rate.type().equals(type) || this.inputs.containsKey(name)
						|| this.outputs.containsKey(name) || this.rates.containsKey(name)) {
					throw new Refused("works a value out of the whole triangle that a pixel cannot");
				}

				this.rates.put(name, rate);
				this.prologue.add(type + " " + name + " = " + RATE + "(dFdx(" + rate.of() + "), dFdy("
						+ rate.of() + "), dFdx(" + rate.over() + "), dFdy(" + rate.over() + "));");

				return;
			}

			String target = this.code.text(from);
			if (!this.outputs.containsKey(target) || !this.code.is(from + 1, "=")
					|| this.code.is(from + 2, "=")) {
				throw new Refused("does more before its loop than work out a value for the whole triangle");
			}

			for (int index = from + 2; index < semicolon; index++) {
				if (!this.code.identifier(index) || this.code.is(index - 1, ".")) {
					continue;
				}

				String name = this.code.text(index);
				if (this.code.is(index + 1, "(")) {
					if (this.declared.contains(name)) {
						throw new Refused("calls " + name + ", a function of its own, before its loop");
					}

					continue;
				}

				if (name.equals("true") || name.equals("false") || this.rates.containsKey(name)
						|| this.wide.contains(name)) {
					continue;
				}

				String type = this.uniforms.get(name);
				if (type == null || type.contains("sampler") || type.contains("image")) {
					throw new Refused("works a value out of the whole triangle that a pixel cannot");
				}

				this.reads.put(name, type);
			}

			this.wide.add(target);
			this.prologue.add(this.code.source(from, semicolon) + ";");
		}

		
		private Rate rate(int from, int to) {
			if (to - from != RATE_TOKENS || !this.code.is(from, "max") || !this.code.is(from + 1, "(")
					|| !this.code.is(from + 2, "max") || !this.code.is(from + 3, "(")) {
				return null;
			}

			int second = from + 4 + EDGE.length + 1;
			int third = second + EDGE.length + 2;
			if (!this.code.is(second - 1, ",") || !this.code.is(third - 2, ")")
					|| !this.code.is(third - 1, ",") || !this.code.is(to - 1, ")")) {
				return null;
			}

			Edge[] edges = {edge(from + 4), edge(second), edge(third)};
			int spanned = 0;
			for (Edge edge : edges) {
				if (edge == null || !edge.of().equals(edges[0].of())
						|| !edge.over().equals(edges[0].over())) {
					return null;
				}

				int pair = Math.min(edge.first(), edge.second()) * 3
						+ Math.max(edge.first(), edge.second());
				spanned |= 1 << pair;
			}

			Varying of = this.inputs.get(edges[0].of());
			Varying over = this.inputs.get(edges[0].over());
			// The three edges of the triangle, each once: corners 0 and 1, 0 and 2, 1 and 2.
			int triangle = (1 << 1) | (1 << 2) | (1 << 5);
			if (spanned != triangle || of == null || over == null || !plain(of) || !plain(over)
					|| edges[0].of().equals(edges[0].over()) || !RATE_OF.contains(of.type())
					|| !RATE_OVER.contains(over.type())) {
				return null;
			}

			return new Rate(of.type(), edges[0].of(), edges[0].over(), over.type());
		}

		
		private Edge edge(int at) {
			for (int index = 0; index < EDGE.length; index++) {
				if (EDGE[index] != null && !this.code.is(at + index, EDGE[index])) {
					return null;
				}
			}

			String of = this.code.text(at + 2);
			String over = this.code.text(at + 15);
			String first = this.code.text(at + 4);
			String second = this.code.text(at + 9);
			if (!of.equals(this.code.text(at + 7)) || !over.equals(this.code.text(at + 20))
					|| !first.equals(this.code.text(at + 17))
					|| !second.equals(this.code.text(at + 22))) {
				return null;
			}

			int a = corner(first);
			int b = corner(second);

			if (a < 0 || b < 0 || a == b) {
				return null;
			}

			return new Edge(of, over, a, b);
		}

		private static int corner(String written) {
			return switch (written) {
				case "0" -> 0;
				case "1" -> 1;
				case "2" -> 2;
				default -> -1;
			};
		}

		
		private int loop(int at, int end) throws Refused {
			boolean declaredHere = this.code.is(at + 2, "int");
			int first = declaredHere ? at + 3 : at + 2;
			String counter = this.code.text(first);
			int step = first + 8;
			boolean head = this.code.is(at + 1, "(")
					&& (declaredHere || counter.equals(this.counter))
					&& this.code.identifier(first) && this.code.is(first + 1, "=")
					&& this.code.is(first + 2, "0") && this.code.is(first + 3, ";")
					&& this.code.is(first + 4, counter) && this.code.is(first + 5, "<")
					&& this.code.is(first + 6, "3") && this.code.is(first + 7, ";");
			boolean increments = this.code.join(step, step + 3).equals(counter + " + +")
					|| this.code.join(step, step + 3).equals("+ + " + counter);
			int close = this.code.closing(step + 4);
			if (!head || !increments || !this.code.is(step + 3, ")") || !this.code.is(step + 4, "{")
					|| close < 0 || close >= end) {
				throw new Refused("loops over something other than its three corners");
			}

			boolean position = false;
			int index = step + 5;
			while (index < close) {
				if (this.code.is(index, "EmitVertex") && this.code.is(index + 1, "(")
						&& this.code.is(index + 2, ")") && this.code.is(index + 3, ";")
						&& index + 4 == close && position) {
					return close + 1;
				}

				if (!position && this.code.join(index, index + 9)
						.equals("gl_Position = gl_in [ " + counter + " ] . gl_Position ;")) {
					position = true;
					index += 9;
					continue;
				}

				// The same hand on through a local: the corner's position is read into it and
				// written out of it with nothing in between, anything else being refused below.
				if (!position && this.positionLocal == null && this.code.is(index, "vec4")
						&& this.code.identifier(index + 1) && this.code.join(index + 2, index + 10)
								.equals("= gl_in [ " + counter + " ] . gl_Position ;")) {
					this.positionLocal = this.code.text(index + 1);
					index += 10;
					continue;
				}

				if (!position && this.positionLocal != null && this.code.join(index, index + 4)
						.equals("gl_Position = " + this.positionLocal + " ;")) {
					position = true;
					index += 4;
					continue;
				}

				String output = this.code.text(index);
				String input = this.code.text(index + 2);
				if (this.outputs.containsKey(output) && this.inputs.containsKey(input)
						&& this.code.join(index + 1, index + 7)
								.equals("= " + input + " [ " + counter + " ] ;")) {
					if (this.copies.containsKey(output) || this.wide.contains(output)
							|| this.copies.containsValue(input)) {
						throw new Refused("writes " + output + " or reads " + input
								+ " more than once");
					}

					if (!this.outputs.get(output).type().equals(this.inputs.get(input).type())) {
						throw new Refused("copies " + input + " into a varying of another type");
					}

					this.copies.put(output, input);
					index += 7;
					continue;
				}

				break;
			}

			throw new Refused("does something other than copy each corner in its loop");
		}
	}

	
	private static String rewrite(Stage stage, Code code) throws Refused {
		Set<String> spelled = new HashSet<>();
		for (GlslLexer.Token token : code.all) {
			if (token.kind() == GlslLexer.Kind.IDENTIFIER) {
				spelled.add(token.text());
			}
		}

		for (String name : stage.inputs.keySet()) {
			// Copied into an output of its own name, which is the fragment stage's input already:
			// the vertex stage's output reaches it under that name with nothing to rename.
			if (spelled.contains(name) && !name.equals(stage.copies.get(name))) {
				throw new Refused("hands on " + name + ", a name the fragment stage already spells");
			}
		}

		for (String name : RATE_NAMES) {
			if (spelled.contains(name)) {
				throw new Refused("would be folded under " + name
						+ ", a name the fragment stage already spells");
			}
		}

		Map<String, Statement> declared = new HashMap<>();
		Set<String> twice = new HashSet<>();
		int main = -1;
		for (Statement statement : code.fileScope()) {
			int start = statement.start();
			if (statement.body() >= 0) {
				if (code.join(start, statement.body() + 1).equals("void main ( ) {")) {
					if (main >= 0) {
						throw new Refused("meets a fragment stage with two main functions");
					}

					main = start;
				}

				continue;
			}

			int storage = start;
			while (storage < statement.end() && QUALIFIERS.contains(code.text(storage))) {
				storage++;
			}

			if (code.is(storage, "in") && code.identifier(storage + 1)
					&& code.identifier(storage + 2) && storage + 3 == statement.end()
					&& declared.put(code.text(storage + 2), statement) != null) {
				twice.add(code.text(storage + 2));
			}
		}

		if (main < 0) {
			throw new Refused("meets a fragment stage whose main the fold cannot find");
		}

		Map<String, String> uniforms = code.uniforms();
		for (Map.Entry<String, String> read : stage.reads.entrySet()) {
			if (!read.getValue().equals(uniforms.get(read.getKey()))) {
				throw new Refused("reads " + read.getKey() + " before its loop, which the fragment stage "
						+ "does not declare as the same uniform");
			}
		}

		List<Edit> edits = new ArrayList<>();
		StringBuilder copies = new StringBuilder();
		for (Map.Entry<String, Varying> output : stage.outputs.entrySet()) {
			String name = output.getKey();
			Statement statement = declared.get(name);
			if (statement == null || twice.contains(name)) {
				throw new Refused("hands on " + name
						+ ", which the fragment stage does not declare once as a plain input");
			}

			int semicolon = statement.end();
			String type = code.text(semicolon - 2);
			if (!type.equals(output.getValue().type())) {
				throw new Refused("hands on " + name + " as a type the fragment stage does not read");
			}

			String input = stage.copies.get(name);
			if (name.equals(input)) {
				// The stage hands the corner on under the name it came in by, as a block of one
				// name on both sides flattens to (Eclipse's DATA): the declaration stands as it is.
				continue;
			}

			if (input == null) {
				edits.add(new Edit(statement.start(), semicolon, type + " " + name + ";"));
			} else {
				edits.add(new Edit(semicolon - 1, semicolon - 1, input));
				edits.add(new Edit(semicolon, semicolon, "; " + type + " " + name + ";"));
				copies.append(name).append(" = ").append(input).append("; ");
			}
		}

		// A copied input is interpolated under the fragment stage's declaration of the name it is
		// copied to, so that declaration is the one a rate over it has to find plain.
		for (Rate rate : stage.rates.values()) {
			for (Map.Entry<String, String> copy : stage.copies.entrySet()) {
				if (!copy.getValue().equals(rate.of()) && !copy.getValue().equals(rate.over())) {
					continue;
				}

				for (int index = declared.get(copy.getKey()).start(); QUALIFIERS.contains(code.text(index));
						index++) {
					String qualifier = code.text(index);
					if (qualifier.equals("flat") || qualifier.equals("noperspective")
							|| qualifier.equals("centroid")) {
						throw new Refused("measures " + copy.getValue() + " across the triangle, which the "
								+ "fragment stage reads " + qualifier);
					}
				}
			}
		}

		StringBuilder head = new StringBuilder("\n");
		for (Map.Entry<String, Varying> input : stage.inputs.entrySet()) {
			if (stage.copies.containsValue(input.getKey())) {
				continue;
			}

			String type = input.getValue().type();
			List<String> qualifiers = input.getValue().qualifiers();
			// An integer reaching a fragment stage has to be flat, which a stage between the two
			// was free not to say.
			if (!qualifiers.contains("flat") && (type.startsWith("int") || type.startsWith("uint")
					|| type.startsWith("ivec") || type.startsWith("uvec"))) {
				head.append("flat ");
			}

			qualifiers.forEach(qualifier -> head.append(qualifier).append(' '));
			head.append("in ").append(type).append(' ').append(input.getKey()).append(";\n");
		}

		Map<String, Rate> signatures = new LinkedHashMap<>();
		for (Rate rate : stage.rates.values()) {
			signatures.putIfAbsent(rate.type() + " " + rate.overType(), rate);
		}

		for (Rate rate : signatures.values()) {
			// The length of the gradient of the first varying over the plane the second one spans,
			// from how far one pixel step moves each: the step pair's Gram matrix inverted, as a
			// quadratic form of the first varying's two steps.
			String of = rate.type();
			String over = rate.overType();
			head.append(of).append(' ').append(RATE).append('(').append(of).append(" ofAx, ")
					.append(of).append(" ofAy, ").append(over).append(" ofBx, ").append(over)
					.append(" ofBy) { float ofXx = dot(ofBx, ofBx); float ofXy = dot(ofBx, ofBy); "
							+ "float ofYy = dot(ofBy, ofBy); return sqrt(max((ofYy * ofAx * ofAx "
							+ "- 2.0 * ofXy * ofAx * ofAy + ofXx * ofAy * ofAy) / max(ofXx * ofYy "
							+ "- ofXy * ofXy, 1e-30), ")
					.append(of).append("(0.0))); }\n");
		}

		edits.add(new Edit(main, main, head + "void"));
		edits.add(new Edit(main + 4, main + 4,
				"{ { " + copies + String.join(" ", stage.prologue) + " }"));
		edits.sort(Comparator.comparingInt(Edit::from));

		StringBuilder text = new StringBuilder();
		int next = 0;
		int last = -1;
		for (Edit edit : edits) {
			if (edit.from() <= last) {
				throw new Refused("meets a fragment stage it cannot rewrite in one reading");
			}

			int from = code.at.get(edit.from());
			for (int index = next; index < from; index++) {
				text.append(code.all.get(index).text());
			}

			text.append(edit.text());
			next = code.at.get(edit.to()) + 1;
			last = edit.to();
		}

		for (int index = next; index < code.all.size(); index++) {
			text.append(code.all.get(index).text());
		}

		return text.toString();
	}
}
