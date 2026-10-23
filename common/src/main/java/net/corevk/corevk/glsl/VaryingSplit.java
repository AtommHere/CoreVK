package net.corevk.corevk.glsl;

import net.corevk.corevk.glsl.GlslLexer.Kind;
import net.corevk.corevk.glsl.GlslLexer.Token;
import net.corevk.corevk.glsl.GlslTranslator.FileScope;
import net.corevk.corevk.pack.model.ProgramStage;
import net.corevk.corevk.pack.source.IncludeExpander.ExpandedUnit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class VaryingSplit {

	
	private static final String MATRIX_COLUMN = "of_vmat_";

	
	private static final String STRUCT_MEMBER = "of_vstruct_";

	
	private static final String ARRAY_ELEMENT = "of_varr_";

	
	private static final Pattern ARRAY_SUFFIX = Pattern.compile("\\[\\s*([1-9]\\d{0,2})\\s*\\]$");

	
	private static final Set<String> STRUCT_MEMBER_TYPES = Set.of(
			"float", "vec2", "vec3", "vec4", "int", "ivec2", "ivec3", "ivec4",
			"uint", "uvec2", "uvec3", "uvec4", "double", "dvec2", "dvec3", "dvec4");

	private final TokenStream tokens;
	private final ExpandedUnit unit;
	private final ProgramStage stage;
	private final Declarations declarations;

	
	private final Map<String, String> macroAliases;

	
	private final List<SplitMatrix> matrices = new ArrayList<>();

	
	private final List<SplitStruct> structs = new ArrayList<>();

	
	private final List<SplitArray> arrays = new ArrayList<>();

	VaryingSplit(TokenStream tokens, ExpandedUnit unit, ProgramStage stage,
			Declarations declarations, Map<String, String> macroAliases) {
		this.tokens = tokens;
		this.unit = unit;
		this.stage = stage;
		this.declarations = declarations;
		this.macroAliases = macroAliases;
	}

	
	@FunctionalInterface
	interface Declarations {

		
		FileScope read(int keyword, Set<String> types);
	}

	
	void split() {
		splitMatrixVaryings();
		splitStructVaryings();
		splitArrayVaryings();
	}

	
	boolean any() {
		return !this.matrices.isEmpty() || !this.structs.isEmpty() || !this.arrays.isEmpty();
	}

	List<SplitMatrix> matrices() {
		return this.matrices;
	}

	List<SplitStruct> structs() {
		return this.structs;
	}

	List<SplitArray> arrays() {
		return this.arrays;
	}

	
	List<String> interfaceNames(boolean input) {
		List<String> names = new ArrayList<>();
		for (SplitMatrix split : this.matrices) {
			if (split.input() == input) {
				for (int column = 0; column < split.columns(); column++) {
					names.add(matrixColumnName(split.name(), column));
				}
			}
		}

		for (SplitStruct split : this.structs) {
			if (split.input() == input) {
				split.members().forEach(member ->
						names.add(structMemberName(split.name(), member.name())));
			}
		}

		for (SplitArray split : this.arrays) {
			if (split.input() == input) {
				for (int element = 0; element < split.size(); element++) {
					names.add(arrayElementName(split.name(), element));
				}
			}
		}

		return List.copyOf(names);
	}

	
	List<String> interfaceNamesOf(String name) {
		List<String> names = new ArrayList<>();
		for (SplitMatrix split : this.matrices) {
			if (split.name().equals(name)) {
				for (int column = 0; column < split.columns(); column++) {
					names.add(matrixColumnName(name, column));
				}
			}
		}

		for (SplitStruct split : this.structs) {
			if (split.name().equals(name)) {
				split.members().forEach(member ->
						names.add(structMemberName(name, member.name())));
			}
		}

		for (SplitArray split : this.arrays) {
			if (split.name().equals(name)) {
				for (int element = 0; element < split.size(); element++) {
					names.add(arrayElementName(name, element));
				}
			}
		}

		return List.copyOf(names);
	}

	
	List<String> splitInputs() {
		List<String> names = new ArrayList<>();
		this.matrices.forEach(split -> {
			if (split.input()) {
				names.add(split.name());
			}
		});
		this.structs.forEach(split -> {
			if (split.input()) {
				names.add(split.name());
			}
		});
		this.arrays.forEach(split -> {
			if (split.input()) {
				names.add(split.name());
			}
		});

		return List.copyOf(names);
	}

	
	boolean forgetInput(String name) {
		boolean any = this.matrices.removeIf(split -> split.input() && split.name().equals(name));
		any |= this.structs.removeIf(split -> split.input() && split.name().equals(name));
		any |= this.arrays.removeIf(split -> split.input() && split.name().equals(name));

		return any;
	}

	
	record SplitMatrix(String name, String matrixType, String columnType, int columns,
			String qualifier, boolean input) {
	}

	
	private void splitMatrixVaryings() {
		if (this.stage == ProgramStage.COMPUTE) {
			return;
		}

		int[] lines = this.tokens.lineNumbers();
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.directive() != null || !this.unit.isLive(lines[index])) {
				continue;
			}

			String storage = GlslTranslator.storageWord(token, this.macroAliases);
			if (storage == null) {
				continue;
			}

			boolean input = storage.equals("in");
			if (input && this.stage == ProgramStage.VERTEX) {
				continue;
			}

			FileScope declared = this.declarations.read(index, LegacyGlsl.TYPE_NAMES);
			if (declared == null) {
				continue;
			}

			MatrixColumns layout = matrixColumns(declared.type());
			if (layout == null) {
				continue;
			}

			boolean anyArray = declared.names().stream().anyMatch(name ->
					declared.declarators().getOrDefault(name, "").contains("["));
			if (anyArray) {
				continue;
			}

			this.tokens.blankRange(declared.start(), declared.end());

			for (String name : declared.names()) {
				this.matrices.add(new SplitMatrix(name, declared.type(), layout.columnType(),
						layout.columns(), declared.qualifier(), input));
			}
		}
	}

	
	record MatrixColumns(String columnType, int columns) {
	}

	
	private static MatrixColumns matrixColumns(String type) {
		String prefix = "";
		String rest = type;
		if (rest.startsWith("dmat")) {
			prefix = "d";
			rest = rest.substring(1);
		}

		if (!rest.startsWith("mat")) {
			return null;
		}

		rest = rest.substring(3);
		int columns;
		int rows;
		int by = rest.indexOf('x');
		try {
			if (by < 0) {
				columns = Integer.parseInt(rest);
				rows = columns;
			} else {
				columns = Integer.parseInt(rest.substring(0, by));
				rows = Integer.parseInt(rest.substring(by + 1));
			}
		} catch (NumberFormatException ignored) {
			return null;
		}

		if (columns < 2 || columns > 4 || rows < 2 || rows > 4) {
			return null;
		}

		return new MatrixColumns(prefix + "vec" + rows, columns);
	}

	static String matrixColumnName(String matrix, int column) {
		return MATRIX_COLUMN + matrix + "_" + column;
	}

	
	record StructMember(String type, String name) {
	}

	
	record SplitStruct(String name, String structType, List<StructMember> members,
			String qualifier, boolean input) {
	}

	
	private void splitStructVaryings() {
		if (this.stage == ProgramStage.COMPUTE) {
			return;
		}

		Map<String, List<StructMember>> definitions = structDefinitions();
		if (definitions.isEmpty()) {
			return;
		}

		int[] lines = this.tokens.lineNumbers();
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.directive() != null || !this.unit.isLive(lines[index])) {
				continue;
			}

			String storage = GlslTranslator.storageWord(token, this.macroAliases);
			if (storage == null) {
				continue;
			}

			boolean input = storage.equals("in");
			if (input && this.stage == ProgramStage.VERTEX) {
				continue;
			}

			// Read against the struct types the unit defines: the plain reader only knows the
			// language's own types, which is why a struct varying was never a declaration to it.
			FileScope declared = this.declarations.read(index, definitions.keySet());
			if (declared == null) {
				continue;
			}

			List<StructMember> members = definitions.get(declared.type());

			boolean anyArray = declared.names().stream().anyMatch(name ->
					declared.declarators().getOrDefault(name, "").contains("["));
			if (anyArray) {
				continue;
			}

			this.tokens.blankRange(declared.start(), declared.end());

			for (String name : declared.names()) {
				this.structs.add(new SplitStruct(name, declared.type(), members,
						declared.qualifier(), input));
			}
		}

		// The definition moves to the header with the global that carries the pack's name: the
		// wrapper that rebuilds the struct stands in the header, above the body, and the type has
		// to exist there. The body's own definition goes blank so the type is not defined twice.
		Set<String> moved = new HashSet<>();
		for (SplitStruct split : this.structs) {
			if (!moved.add(split.structType())) {
				continue;
			}

			int[] definition = definitionRange(split.structType());
			if (definition != null) {
				this.tokens.blankRange(definition[0], definition[1]);
			}
		}
	}

	
	private Map<String, List<StructMember>> structDefinitions() {
		Map<String, List<StructMember>> definitions = new LinkedHashMap<>();
		int[] lines = this.tokens.lineNumbers();
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (!token.identifier("struct") || token.directive() != null
					|| !this.unit.isLive(lines[index])) {
				continue;
			}

			int name = this.tokens.significantAfter(index);
			int open = this.tokens.significantAfter(name);
			if (name < 0 || open < 0 || this.tokens.get(name).kind() != Kind.IDENTIFIER
					|| !this.tokens.get(open).operator("{")) {
				continue;
			}

			int close = this.tokens.matchingBracket(open);
			int semicolon = this.tokens.significantAfter(close);
			// A definition that declares an instance in the same breath, "struct T { ... } t;",
			// is left alone: taking it out of the body would take the instance with it.
			if (close < 0 || semicolon < 0 || !this.tokens.get(semicolon).operator(";")) {
				continue;
			}

			List<StructMember> members = structMembers(open + 1, close - 1);
			if (members != null && !members.isEmpty()) {
				definitions.putIfAbsent(this.tokens.get(name).text(), members);
			}
		}

		return definitions;
	}

	
	private List<StructMember> structMembers(int start, int end) {
		List<StructMember> members = new ArrayList<>();
		List<Integer> statement = new ArrayList<>();
		for (int scan : this.tokens.significantRange(start, end)) {
			Token token = this.tokens.get(scan);
			if (!token.operator(";")) {
				statement.add(scan);
				continue;
			}

			if (statement.size() < 2) {
				return null;
			}

			String type = this.tokens.get(statement.get(0)).text();
			if (!STRUCT_MEMBER_TYPES.contains(type)) {
				return null;
			}

			for (int part = 1; part < statement.size(); part++) {
				Token piece = this.tokens.get(statement.get(part));
				if (piece.operator(",")) {
					continue;
				}

				if (piece.kind() != Kind.IDENTIFIER) {
					return null;
				}

				members.add(new StructMember(type, piece.text()));
			}

			statement.clear();
		}

		return statement.isEmpty() ? members : null;
	}

	
	private int[] definitionRange(String structType) {
		int[] lines = this.tokens.lineNumbers();
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (!token.identifier("struct") || token.directive() != null
					|| !this.unit.isLive(lines[index])) {
				continue;
			}

			int name = this.tokens.significantAfter(index);
			int open = this.tokens.significantAfter(name);
			if (name < 0 || open < 0 || !this.tokens.get(name).identifier(structType)
					|| !this.tokens.get(open).operator("{")) {
				continue;
			}

			int close = this.tokens.matchingBracket(open);
			int semicolon = this.tokens.significantAfter(close);
			if (close >= 0 && semicolon >= 0 && this.tokens.get(semicolon).operator(";")) {
				return new int[] {index, semicolon};
			}
		}

		return null;
	}

	static String structMemberName(String struct, String member) {
		return STRUCT_MEMBER + struct + "_" + member;
	}

	
	record SplitArray(String name, String type, int size, String qualifier, boolean input) {
	}

	
	private void splitArrayVaryings() {
		boolean output = this.stage == ProgramStage.VERTEX;
		if (!output && this.stage != ProgramStage.FRAGMENT) {
			return;
		}

		String wanted = output ? "out" : "in";
		int[] lines = this.tokens.lineNumbers();
		for (int index = 0; index < this.tokens.size(); index++) {
			Token token = this.tokens.get(index);
			if (token.directive() != null || !this.unit.isLive(lines[index])
					|| !wanted.equals(GlslTranslator.storageWord(token, this.macroAliases))) {
				continue;
			}

			FileScope declared = this.declarations.read(index, LegacyGlsl.TYPE_NAMES);
			if (declared == null || !STRUCT_MEMBER_TYPES.contains(declared.type())) {
				continue;
			}

			List<SplitArray> found = new ArrayList<>();
			for (String name : declared.names()) {
				String prefix = declared.type() + " " + name;
				String declarator = declared.declarators().getOrDefault(name, "");
				Matcher size = declarator.startsWith(prefix)
						? ARRAY_SUFFIX.matcher(declarator.substring(prefix.length()))
						: null;
				if (size == null || !size.matches()) {
					found.clear();
					break;
				}

				found.add(new SplitArray(name, declared.type(), Integer.parseInt(size.group(1)),
						declared.qualifier(), !output));
			}

			if (found.isEmpty()) {
				continue;
			}

			this.tokens.blankRange(declared.start(), declared.end());
			this.arrays.addAll(found);
		}
	}

	static String arrayElementName(String array, int element) {
		return ARRAY_ELEMENT + array + "_" + element;
	}

	
	String matrixPrologue() {
		StringBuilder assignments = new StringBuilder();
		for (SplitMatrix split : this.matrices) {
			if (!split.input()) {
				continue;
			}

			assignments.append(split.name()).append(" = ").append(split.matrixType()).append('(');
			for (int column = 0; column < split.columns(); column++) {
				if (column > 0) {
					assignments.append(", ");
				}

				assignments.append(matrixColumnName(split.name(), column));
			}

			assignments.append("); ");
		}

		return assignments.toString();
	}

	
	String matrixEpilogue() {
		StringBuilder assignments = new StringBuilder();
		for (SplitMatrix split : this.matrices) {
			if (split.input()) {
				continue;
			}

			for (int column = 0; column < split.columns(); column++) {
				assignments.append(matrixColumnName(split.name(), column)).append(" = ")
						.append(split.name()).append('[').append(column).append("]; ");
			}
		}

		return assignments.toString();
	}

	
	String structPrologue() {
		StringBuilder assignments = new StringBuilder();
		for (SplitStruct split : this.structs) {
			if (!split.input()) {
				continue;
			}

			assignments.append(split.name()).append(" = ").append(split.structType()).append('(');
			for (int member = 0; member < split.members().size(); member++) {
				if (member > 0) {
					assignments.append(", ");
				}

				assignments.append(structMemberName(split.name(),
						split.members().get(member).name()));
			}

			assignments.append("); ");
		}

		return assignments.toString();
	}

	
	String structEpilogue() {
		StringBuilder assignments = new StringBuilder();
		for (SplitStruct split : this.structs) {
			if (split.input()) {
				continue;
			}

			for (StructMember member : split.members()) {
				assignments.append(structMemberName(split.name(), member.name()))
						.append(" = ")
						.append(split.name()).append('.').append(member.name()).append("; ");
			}
		}

		return assignments.toString();
	}

	
	String arrayPrologue() {
		StringBuilder assignments = new StringBuilder();
		for (SplitArray split : this.arrays) {
			if (!split.input()) {
				continue;
			}

			for (int element = 0; element < split.size(); element++) {
				assignments.append(split.name()).append('[').append(element).append("] = ")
						.append(arrayElementName(split.name(), element)).append("; ");
			}
		}

		return assignments.toString();
	}

	
	String arrayEpilogue() {
		StringBuilder assignments = new StringBuilder();
		for (SplitArray split : this.arrays) {
			if (split.input()) {
				continue;
			}

			for (int element = 0; element < split.size(); element++) {
				assignments.append(arrayElementName(split.name(), element)).append(" = ")
						.append(split.name()).append('[').append(element).append("]; ");
			}
		}

		return assignments.toString();
	}
}
