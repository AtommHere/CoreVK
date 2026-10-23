package net.corevk.corevk.uniform.expr;

import net.corevk.corevk.uniform.expr.kroppeb.stareval.element.ExpressionElement;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.expression.Expression;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.expression.VariableExpression;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.function.FunctionContext;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.function.FunctionReturn;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.function.Type;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.parser.Parser;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.resolver.ExpressionResolver;
import net.corevk.corevk.uniform.UniformCatalog;
import net.corevk.corevk.uniform.UniformShape;
import net.corevk.corevk.uniform.UniformSource;
import net.corevk.corevk.uniform.Val;
import net.corevk.corevk.uniform.WorldState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.joml.Vector4i;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CustomUniforms implements FunctionContext, FrameClock {

	
	private static final Map<String, Type> TYPES = Map.of(
			"bool", Type.Boolean,
			"float", Type.Float,
			"int", Type.Int,
			"vec2", VectorType.VEC2,
			"vec3", VectorType.VEC3,
			"vec4", VectorType.VEC4);

	private final Map<String, Input> engineInputs = new LinkedHashMap<>();
	private final Map<String, Derived> declared = new LinkedHashMap<>();
	private final Map<Node, List<Node>> dependsOn = new LinkedHashMap<>();

	
	private final Set<String> exposedNames = new LinkedHashSet<>();

	private final List<Node> order = new ArrayList<>();
	private final Set<Node> live = new LinkedHashSet<>();

	
	private final List<String> problems = new ArrayList<>();

	private WorldState world;
	private float deltaSeconds;

	private CustomUniforms() {
	}

	public static Builder builder() {
		return new BuilderImpl();
	}

	
	public void update(WorldState world, float dt) {
		this.world = world;
		this.deltaSeconds = dt;

		List<Node> failed = null;
		for (Node node : this.order) {
			try {
				node.refresh();
			} catch (RuntimeException e) {
				if (failed == null) {
					failed = new ArrayList<>();
				}

				failed.add(node);
				this.problems.add(node.name + ": no longer evaluated, it threw on frame "
						+ world.frameCounter() + ", " + reason(e));
			}
		}

		if (failed != null) {
			drop(failed);
		}
	}

	
	public List<String> drainProblems() {
		if (this.problems.isEmpty()) {
			return List.of();
		}

		List<String> drained = List.copyOf(this.problems);
		this.problems.clear();

		return drained;
	}

	
	private void drop(List<Node> failed) {
		Set<Node> dead = new LinkedHashSet<>(failed);

		boolean growing = true;
		while (growing) {
			growing = false;
			for (Node node : this.order) {
				if (dead.contains(node)) {
					continue;
				}

				for (Node dependency : this.dependsOn.getOrDefault(node, List.of())) {
					if (dead.contains(dependency)) {
						dead.add(node);
						this.problems.add(node.name + ": no longer evaluated either, it reads "
								+ dependency.name);
						growing = true;
						break;
					}
				}
			}
		}

		this.live.removeAll(dead);
		this.order.removeAll(dead);
	}

	@Override
	public float deltaSeconds() {
		return this.deltaSeconds;
	}

	
	public UniformSource source(String name) {
		Derived node = this.declared.get(name);
		if (node == null || !this.live.contains(node)) {
			return null;
		}

		return (_, out) -> node.writeInto(out);
	}

	
	public UniformShape shape(String name) {
		Derived node = this.declared.get(name);

		return node == null ? null : Type.convert(node.type);
	}

	
	public Set<String> exposed() {
		Set<String> names = new LinkedHashSet<>();
		for (String name : this.declared.keySet()) {
			if (source(name) != null) {
				names.add(name);
			}
		}

		return Collections.unmodifiableSet(names);
	}

	
	public UniformCatalog layerOn(UniformCatalog engine) {
		UniformCatalog.Builder builder = UniformCatalog.builder(engine);
		for (String name : exposed()) {
			builder.add(name, shape(name), source(name));
		}

		return builder.build();
	}

	@Override
	public boolean hasVariable(String name) {
		return this.engineInputs.containsKey(name) || this.declared.containsKey(name);
	}

	@Override
	public Expression getVariable(String name) {
		Node node = this.engineInputs.get(name);
		if (node == null) {
			node = this.declared.get(name);
		}
		if (node == null) {
			throw new IllegalStateException("Unknown variable: " + name);
		}

		return node;
	}

	public interface Builder {

		
		Builder declare(String name, String type, String expression, boolean exposed);

		
		CustomUniforms build(UniformCatalog engine, List<String> problems);
	}

	
	private record Declaration(String name, Type type, ExpressionElement tree, boolean exposed) {
	}

	private static final class BuilderImpl implements Builder {

		private final Map<String, Declaration> declarations = new LinkedHashMap<>();
		private final List<String> refused = new ArrayList<>();

		@Override
		public Builder declare(String name, String type, String expression, boolean exposed) {
			if (this.declarations.containsKey(name)) {
				// The first one wins, as it does in CoreVK. Two live declarations of one name mean
				// the conditionals around them were not read, which is a bug upstream of here.
				this.refused.add(name + ": declared more than once, the later one is ignored");
				return this;
			}

			Type declared = TYPES.get(type);
			if (declared == null) {
				this.refused.add(name + ": " + type + " is not a type a custom uniform may take");
				return this;
			}

			ExpressionElement tree;
			try {
				tree = Parser.parse(expression, ExprGrammar.options);
			} catch (Exception e) {
				this.refused.add(name + ": " + reason(e) + " (= " + expression + ")");
				return this;
			}

			this.declarations.put(name, new Declaration(name, declared, tree, exposed));

			return this;
		}

		@Override
		public CustomUniforms build(UniformCatalog engine, List<String> problems) {
			problems.addAll(this.refused);

			CustomUniforms uniforms = new CustomUniforms();
			uniforms.resolve(engine, this.declarations, problems);

			return uniforms;
		}
	}

	private void resolve(UniformCatalog engine, Map<String, Declaration> declarations,
			List<String> problems) {
		ExpressionResolver resolver = new ExpressionResolver(ExprFunctions.functions,
				name -> typeOf(engine, declarations, name));

		for (Declaration declaration : declarations.values()) {
			if (engine.source(declaration.name()) != null) {
				problems.add(declaration.name() + ": shadows a value the engine already answers");
				continue;
			}

			try {
				Expression expression = resolver.resolveExpression(declaration.type(), declaration.tree());
				this.declared.put(declaration.name(),
						new Derived(declaration.name(), declaration.type(), expression));
				if (declaration.exposed()) {
					this.exposedNames.add(declaration.name());
				}
			} catch (Exception e) {
				problems.add(declaration.name() + ": " + reason(e));
			}
		}

		sort(problems);
		prune();
	}

	
	private Type typeOf(UniformCatalog engine, Map<String, Declaration> declarations, String name) {
		Input input = this.engineInputs.get(name);
		if (input != null) {
			return input.type;
		}

		UniformSource source = engine.source(name);
		UniformShape shape = engine.natural(name);
		if (source != null && shape != null) {
			Type type = Type.of(shape);
			if (type != null) {
				this.engineInputs.put(name, new Input(name, type, source));
				return type;
			}
		}

		Declaration declaration = declarations.get(name);

		return declaration == null ? null : declaration.type();
	}

	
	private void sort(List<String> problems) {
		Map<Node, List<Node>> requiredBy = new LinkedHashMap<>();
		Map<Node, Integer> remaining = new LinkedHashMap<>();
		Set<Node> broken = new LinkedHashSet<>();

		for (Node node : this.engineInputs.values()) {
			requiredBy.put(node, new ArrayList<>());
		}
		for (Node node : this.declared.values()) {
			requiredBy.put(node, new ArrayList<>());
		}

		FunctionReturn scratch = new FunctionReturn();
		for (Derived node : this.declared.values()) {
			Set<VariableExpression> variables = new LinkedHashSet<>();
			node.expression.listVariables(variables);

			List<Node> dependencies = new ArrayList<>();
			for (VariableExpression variable : variables) {
				if (variable.partialEval(this, scratch) instanceof Node dependency) {
					dependencies.add(dependency);
				} else {
					broken.add(node);
				}
			}

			if (dependencies.isEmpty()) {
				continue;
			}

			this.dependsOn.put(node, dependencies);
			remaining.put(node, dependencies.size());
			for (Node dependency : dependencies) {
				requiredBy.get(dependency).add(node);
			}
		}

		Deque<Node> free = new ArrayDeque<>();
		for (Node node : requiredBy.keySet()) {
			if (!remaining.containsKey(node)) {
				free.add(node);
			}
		}

		while (!free.isEmpty()) {
			Node node = free.removeLast();
			if (broken.contains(node)) {
				broken.addAll(requiredBy.get(node));
			} else {
				this.order.add(node);
			}

			for (Node dependent : requiredBy.get(node)) {
				if (remaining.merge(dependent, -1, Integer::sum) == 0) {
					remaining.remove(dependent);
					free.add(dependent);
				}
			}
		}

		for (Node node : broken) {
			problems.add(node.name + ": dropped, it or something it reads could not be resolved");
		}

		// Anything the walk never freed is in a cycle, or hangs off one.
		for (Node node : remaining.keySet()) {
			problems.add(node.name + ": dropped, it takes part in a circular reference");
		}
	}

	
	private void prune() {
		Deque<Node> pending = new ArrayDeque<>();
		for (String name : this.exposedNames) {
			Derived node = this.declared.get(name);
			if (node != null && this.order.contains(node)) {
				pending.add(node);
			}
		}

		while (!pending.isEmpty()) {
			Node node = pending.removeLast();
			if (this.live.add(node)) {
				pending.addAll(this.dependsOn.getOrDefault(node, List.of()));
			}
		}

		this.order.retainAll(this.live);
	}

	private static String reason(Throwable e) {
		String message = e.getMessage();

		return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
	}

	
	private abstract static class Node implements VariableExpression {

		final String name;
		final Type type;
		final Object object;

		private boolean booleanValue;
		private int intValue;
		private float floatValue;

		Node(String name, Type type) {
			this.name = name;
			this.type = type;
			this.object = carrier(type);
		}

		// The same identity ladder as Type.convert, and safe for the same narrow reason: both kinds
		// of node are built from a fixed set of constants. A Derived takes its type from TYPES
		// above, which names six and no ivec or matrix among them; an Input takes Type.of(shape),
		// and that is what makes the last five branches here reachable at all. A type equal but not
		// identical would fall through to null and the uniform would carry nothing, silently.
		@SuppressWarnings("ReferenceEquality")
		private static Object carrier(Type type) {
			if (type == VectorType.VEC2) return new Vector2f();
			if (type == VectorType.VEC3) return new Vector3f();
			if (type == VectorType.VEC4) return new Vector4f();
			if (type == VectorType.I_VEC2) return new Vector2i();
			if (type == VectorType.I_VEC3) return new Vector3i();
			if (type == VectorType.I_VEC4) return new Vector4i();
			if (type == MatrixType.MAT3) return new Matrix3f();
			if (type == MatrixType.MAT4) return new Matrix4f();

			return null;
		}

		abstract void refresh();

		@Override
		@SuppressWarnings("ReferenceEquality")
		public final void evaluateTo(FunctionContext context, FunctionReturn functionReturn) {
			if (this.type == Type.Boolean) {
				functionReturn.booleanReturn = this.booleanValue;
			} else if (this.type == Type.Int) {
				functionReturn.intReturn = this.intValue;
			} else if (this.type == Type.Float) {
				functionReturn.floatReturn = this.floatValue;
			} else {
				functionReturn.objectReturn = this.object;
			}
		}

		@SuppressWarnings("ReferenceEquality")
		final void writeInto(Val out) {
			if (this.type == Type.Boolean) {
				out.set(this.booleanValue);
			} else if (this.type == Type.Int) {
				out.set(this.intValue);
			} else if (this.type == Type.Float) {
				out.set(this.floatValue);
			} else if (this.object instanceof Vector2f v) {
				out.set(v.x, v.y);
			} else if (this.object instanceof Vector3f v) {
				out.set(v.x, v.y, v.z);
			} else if (this.object instanceof Vector4f v) {
				out.set(v.x, v.y, v.z, v.w);
			}
		}

		final void setBoolean(boolean value) {
			this.booleanValue = value;
		}

		final void setInt(int value) {
			this.intValue = value;
		}

		final void setFloat(float value) {
			this.floatValue = value;
		}
	}

	
	private final class Input extends Node {

		private final UniformSource source;
		private final Val value = new Val();

		private Input(String name, Type type, UniformSource source) {
			super(name, type);
			this.source = source;
		}

		@Override
		@SuppressWarnings("ReferenceEquality")
		void refresh() {
			this.source.read(CustomUniforms.this.world, this.value);

			if (this.type == Type.Int) {
				setInt(this.value.i(0));
			} else if (this.type == Type.Float) {
				setFloat(this.value.f(0));
			} else if (this.object instanceof Vector2f v) {
				v.set(this.value.f(0), this.value.f(1));
			} else if (this.object instanceof Vector3f v) {
				v.set(this.value.f(0), this.value.f(1), this.value.f(2));
			} else if (this.object instanceof Vector4f v) {
				v.set(this.value.f(0), this.value.f(1), this.value.f(2), this.value.f(3));
			} else if (this.object instanceof Vector2i v) {
				v.set(this.value.i(0), this.value.i(1));
			} else if (this.object instanceof Vector3i v) {
				v.set(this.value.i(0), this.value.i(1), this.value.i(2));
			} else if (this.object instanceof Vector4i v) {
				v.set(this.value.i(0), this.value.i(1), this.value.i(2), this.value.i(3));
			} else if (this.object instanceof Matrix3f m) {
				m.set(this.value.mat3());
			} else if (this.object instanceof Matrix4f m) {
				m.set(this.value.mat4());
			}
		}
	}

	
	private final class Derived extends Node {

		private final Expression expression;
		private final FunctionReturn held = new FunctionReturn();

		private Derived(String name, Type type, Expression expression) {
			super(name, type);
			this.expression = expression;
		}

		@Override
		@SuppressWarnings("ReferenceEquality")
		void refresh() {
			this.expression.evaluateTo(CustomUniforms.this, this.held);

			if (this.type == Type.Boolean) {
				setBoolean(this.held.booleanReturn);
			} else if (this.type == Type.Int) {
				setInt(this.held.intReturn);
			} else if (this.type == Type.Float) {
				setFloat(this.held.floatReturn);
			} else if (this.object instanceof Vector2f v) {
				v.set((Vector2f) this.held.objectReturn);
			} else if (this.object instanceof Vector3f v) {
				v.set((Vector3f) this.held.objectReturn);
			} else if (this.object instanceof Vector4f v) {
				v.set((Vector4f) this.held.objectReturn);
			}
		}
	}
}
