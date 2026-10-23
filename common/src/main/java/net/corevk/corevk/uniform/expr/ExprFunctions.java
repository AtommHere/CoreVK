package net.corevk.corevk.uniform.expr;

import net.corevk.corevk.uniform.expr.kroppeb.stareval.expression.Expression;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.function.AbstractTypedFunction;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.function.B2BFunction;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.function.BB2BFunction;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.function.F2FFunction;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.function.F2IFunction;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.function.FF2BFunction;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.function.FF2FFunction;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.function.FFF2BFunction;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.function.FFF2FFunction;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.function.FunctionContext;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.function.FunctionResolver;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.function.FunctionReturn;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.function.I2IFunction;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.function.II2BFunction;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.function.II2IFunction;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.function.III2BFunction;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.function.III2IFunction;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.function.Type;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.function.TypedFunction.Parameter;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.function.TypedFunction;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.function.V2FFunction;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.function.V2IFunction;
import net.corevk.corevk.uniform.Smoothed;
import net.corevk.corevk.uniform.values.FrameSmoothed;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.joml.Vector4i;

import java.util.Arrays;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class ExprFunctions {
	public static final FunctionResolver functions;
	static final FunctionResolver.Builder builder = new FunctionResolver.Builder();

	static {
		{

			{

				ExprFunctions.<I2IFunction>addVectorizable("negate", (a) -> -a);
				ExprFunctions.<F2FFunction>add("negate", (a) -> -a);

				ExprFunctions.addUnaryOpJOML("negate", VectorType.VEC2, Vector2f::negate);
				ExprFunctions.addUnaryOpJOML("negate", VectorType.VEC3, Vector3f::negate);
				ExprFunctions.addUnaryOpJOML("negate", VectorType.VEC4, Vector4f::negate);
			}
		}
		{

			{

				ExprFunctions.<II2IFunction>addVectorizable("add", Integer::sum);
				ExprFunctions.<FF2FFunction>add("add", Float::sum);

				ExprFunctions.addBinaryOpJOML("add", VectorType.VEC2, Vector2f::add);
				ExprFunctions.addBinaryOpJOML("add", VectorType.VEC3, Vector3f::add);
				ExprFunctions.addBinaryOpJOML("add", VectorType.VEC4, Vector4f::add);
			}

			{

				ExprFunctions.<II2IFunction>addVectorizable("subtract", (a, b) -> a - b);
				ExprFunctions.<FF2FFunction>add("subtract", (a, b) -> a - b);

				ExprFunctions.addBinaryOpJOML("subtract", VectorType.VEC2, Vector2f::sub);
				ExprFunctions.addBinaryOpJOML("subtract", VectorType.VEC3, Vector3f::sub);
				ExprFunctions.addBinaryOpJOML("subtract", VectorType.VEC4, Vector4f::sub);
			}

			{

				ExprFunctions.<II2IFunction>addVectorizable("multiply", (a, b) -> a * b);
				ExprFunctions.<FF2FFunction>add("multiply", (a, b) -> a * b);

				ExprFunctions.addBinaryOpJOML("multiply", VectorType.VEC2, Vector2f::mul);
				ExprFunctions.addBinaryOpJOML("multiply", VectorType.VEC3, Vector3f::mul);
				ExprFunctions.addBinaryOpJOML("multiply", VectorType.VEC4, Vector4f::mul);
			}

			{

				ExprFunctions.<FF2FFunction>add("divide", (a, b) -> a / b);

				ExprFunctions.addBinaryOpJOML("divide", VectorType.VEC2, Vector2f::div);
				ExprFunctions.addBinaryOpJOML("divide", VectorType.VEC3, Vector3f::div);
				ExprFunctions.addBinaryOpJOML("divide", VectorType.VEC4, Vector4f::div);
			}

			{

				ExprFunctions.<II2IFunction>addVectorizable("remainder", (a, b) -> a % b);
				ExprFunctions.<FF2FFunction>add("remainder", (a, b) -> a % b);

			}
		}
		{
			ExprFunctions.<II2BFunction>addBooleanVectorizable("equals", (a, b) -> a == b);
			ExprFunctions.<FF2BFunction>add("equals", (a, b) -> a == b);

			ExprFunctions.addBinaryToBooleanOpJOML("equal", VectorType.VEC2, false, Vector2f::equals);
			ExprFunctions.addBinaryToBooleanOpJOML("equal", VectorType.VEC3, false, Vector3f::equals);
			ExprFunctions.addBinaryToBooleanOpJOML("equal", VectorType.VEC4, false, Vector4f::equals);

			ExprFunctions.<II2BFunction>addBooleanVectorizable("notEquals", (a, b) -> a != b);
			ExprFunctions.<FF2BFunction>add("notEquals", (a, b) -> a != b);

			ExprFunctions.addBinaryToBooleanOpJOML("equal", VectorType.VEC2, true, Vector2f::equals);
			ExprFunctions.addBinaryToBooleanOpJOML("equal", VectorType.VEC3, true, Vector3f::equals);
			ExprFunctions.addBinaryToBooleanOpJOML("equal", VectorType.VEC4, true, Vector4f::equals);

			ExprFunctions.<II2BFunction>add("lessThanOrEquals", (a, b) -> a <= b);
			ExprFunctions.<FF2BFunction>add("lessThanOrEquals", (a, b) -> a <= b);

			ExprFunctions.<II2BFunction>add("moreThanOrEquals", (a, b) -> a >= b);
			ExprFunctions.<FF2BFunction>add("moreThanOrEquals", (a, b) -> a >= b);

			ExprFunctions.<II2BFunction>add("lessThan", (a, b) -> a < b);
			ExprFunctions.<FF2BFunction>add("lessThan", (a, b) -> a < b);

			ExprFunctions.<II2BFunction>add("moreThan", (a, b) -> a > b);
			ExprFunctions.<FF2BFunction>add("moreThan", (a, b) -> a > b);
		}
		{

			ExprFunctions.<BB2BFunction>addVectorizable("equals", (a, b) -> a == b);
			ExprFunctions.<BB2BFunction>addVectorizable("notEquals", (a, b) -> a != b);
			ExprFunctions.<BB2BFunction>addVectorizable("and", (a, b) -> a && b);
			ExprFunctions.<BB2BFunction>addVectorizable("or", (a, b) -> a || b);
			ExprFunctions.<B2BFunction>addVectorizable("not", (a) -> !a);
		}

		{

			{

				ExprFunctions.<F2FFunction>add("torad", (a) -> (float) Math.toRadians(a));
				ExprFunctions.<F2FFunction>add("todeg", (a) -> (float) Math.toDegrees(a));

				ExprFunctions.<F2FFunction>add("radians", (a) -> (float) Math.toRadians(a));
				ExprFunctions.<F2FFunction>add("degrees", (a) -> (float) Math.toDegrees(a));

				ExprFunctions.<F2FFunction>add("sin", (a) -> (float) Math.sin(a));
				ExprFunctions.<F2FFunction>add("cos", (a) -> (float) Math.cos(a));
				ExprFunctions.<F2FFunction>add("tan", (a) -> (float) Math.tan(a));
				ExprFunctions.<F2FFunction>add("asin", (a) -> (float) Math.asin(a));
				ExprFunctions.<F2FFunction>add("acos", (a) -> (float) Math.acos(a));
				ExprFunctions.<F2FFunction>add("atan", (a) -> (float) Math.atan(a));
				ExprFunctions.<FF2FFunction>add("atan", (y, x) -> (float) Math.atan2(y, x));

				ExprFunctions.<FF2FFunction>add("atan2", (y, x) -> (float) Math.atan2(y, x));
			}
			{

				ExprFunctions.<FF2FFunction>add("pow", (a, b) -> (float) Math.pow(a, b));
				ExprFunctions.<F2FFunction>add("exp", (a) -> (float) Math.exp(a));
				ExprFunctions.<F2FFunction>add("log", (a) -> (float) Math.log(a));

				ExprFunctions.<F2FFunction>add("exp2", (a) -> (float) Math.pow(2, a));
				ExprFunctions.<F2FFunction>add("log2", (a) -> (float) (Math.log(a) / Math.log(2)));

				ExprFunctions.<F2FFunction>add("sqrt", (a) -> (float) Math.sqrt(a));

				ExprFunctions.<F2FFunction>add("log10", (a) -> (float) Math.log10(a));

				ExprFunctions.<FF2FFunction>add("log",
					(base, value) -> (float) (Math.log(value) / Math.log(base)));

				ExprFunctions.<F2FFunction>add("exp10", (a) -> (float) Math.pow(10, a));

			}

			{

				ExprFunctions.<I2IFunction>addVectorizable("abs", Math::abs);
				ExprFunctions.<F2FFunction>add("abs", Math::abs);

				ExprFunctions.addUnaryOpJOML("abs", VectorType.VEC2, Vector2f::absolute);
				ExprFunctions.addUnaryOpJOML("abs", VectorType.VEC3, Vector3f::absolute);
				ExprFunctions.addUnaryOpJOML("abs", VectorType.VEC4, Vector4f::absolute);

				ExprFunctions.<F2FFunction>add("sign", Math::signum);

				ExprFunctions.<F2FFunction>add("signum", Math::signum);

				ExprFunctions.<F2FFunction>add("floor", (a) -> (float) Math.floor(a));
				ExprFunctions.<F2IFunction>add("floor", (a) -> (int) Math.floor(a));

				ExprFunctions.addUnaryOpJOML("floor", VectorType.VEC2, Vector2f::floor);
				ExprFunctions.addUnaryOpJOML("floor", VectorType.VEC3, Vector3f::floor);
				ExprFunctions.addUnaryOpJOML("floor", VectorType.VEC4, Vector4f::floor);

				ExprFunctions.<F2FFunction>add("ceil", (a) -> (float) Math.ceil(a));
				ExprFunctions.<F2IFunction>add("ceil", (a) -> (int) Math.ceil(a));

				ExprFunctions.addUnaryOpJOML("ceil", VectorType.VEC2, Vector2f::ceil);
				ExprFunctions.addUnaryOpJOML("ceil", VectorType.VEC3, Vector3f::ceil);
				ExprFunctions.addUnaryOpJOML("ceil", VectorType.VEC4, Vector4f::ceil);

				ExprFunctions.<F2FFunction>add("frac", (a) -> (float) (a - Math.floor(a)));

				ExprFunctions.<II2IFunction>addVectorizable("min", Math::min);
				ExprFunctions.<FF2FFunction>add("min", Math::min);

				ExprFunctions.addBinaryOpJOML("min", VectorType.VEC2, Vector2f::min);
				ExprFunctions.addBinaryOpJOML("min", VectorType.VEC3, Vector3f::min);
				ExprFunctions.addBinaryOpJOML("min", VectorType.VEC4, Vector4f::min);

				ExprFunctions.<II2IFunction>addVectorizable("max", Math::max);
				ExprFunctions.<FF2FFunction>add("max", Math::max);

				ExprFunctions.addBinaryOpJOML("max", VectorType.VEC2, Vector2f::max);
				ExprFunctions.addBinaryOpJOML("max", VectorType.VEC3, Vector3f::max);
				ExprFunctions.addBinaryOpJOML("max", VectorType.VEC4, Vector4f::max);

				{

					for (int length = 3; length <= 16; length++) {
						{

							Type[] inputs = new Type[length];
							Arrays.fill(inputs, Type.Float);
							ExprFunctions.add("min", new AbstractTypedFunction(Type.Float, inputs) {
								@Override
								public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
									params[0].evaluateTo(context, functionReturn);
									float min = functionReturn.floatReturn;
									for (int i = 1; i < params.length; i++) {
										params[1].evaluateTo(context, functionReturn);
										min = Math.min(min, functionReturn.floatReturn);
									}
									functionReturn.floatReturn = min;
								}
							});
						}
						{

							Type[] inputs = new Type[length];
							Arrays.fill(inputs, Type.Float);
							ExprFunctions.add("max", new AbstractTypedFunction(Type.Float, inputs) {
								@Override
								public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
									params[0].evaluateTo(context, functionReturn);
									float max = functionReturn.floatReturn;
									for (int i = 1; i < params.length; i++) {
										params[1].evaluateTo(context, functionReturn);
										max = Math.max(max, functionReturn.floatReturn);
									}
									functionReturn.floatReturn = max;
								}
							});
						}
						{

							Type[] inputs = new Type[length];
							Arrays.fill(inputs, Type.Int);
							ExprFunctions.addVectorizable("min", new AbstractTypedFunction(Type.Int, inputs) {
								@Override
								public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
									params[0].evaluateTo(context, functionReturn);
									int min = functionReturn.intReturn;
									for (int i = 1; i < params.length; i++) {
										params[1].evaluateTo(context, functionReturn);
										min = Math.min(min, functionReturn.intReturn);
									}
									functionReturn.intReturn = min;
								}
							});
						}
						{

							Type[] inputs = new Type[length];
							Arrays.fill(inputs, Type.Int);
							ExprFunctions.addVectorizable("max", new AbstractTypedFunction(Type.Int, inputs) {
								@Override
								public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
									params[0].evaluateTo(context, functionReturn);
									int max = functionReturn.intReturn;
									for (int i = 1; i < params.length; i++) {
										params[1].evaluateTo(context, functionReturn);
										max = Math.max(max, functionReturn.intReturn);
									}
									functionReturn.intReturn = max;
								}
							});
						}
					}
				}

				ExprFunctions.<III2IFunction>addVectorizable("clamp",
					(val, min, max) -> Math.max(min, Math.min(max, val)));
				ExprFunctions.<FFF2FFunction>add("clamp",
					(val, min, max) -> Math.max(min, Math.min(max, val)));
				ExprFunctions.addTernaryOpJOML("clamp", VectorType.VEC2, (val, min, max, dest) -> {
					val.min(max, dest);
					dest.max(min);
				});
				ExprFunctions.addTernaryOpJOML("clamp", VectorType.VEC3, (val, min, max, dest) -> {
					val.min(max, dest);
					dest.max(min);
				});
				ExprFunctions.addTernaryOpJOML("clamp", VectorType.VEC4, (val, min, max, dest) -> {
					val.min(max, dest);
					dest.max(min);
				});

				ExprFunctions.<FFF2FFunction>add("mix", (x, y, a) -> x + (y - x) * a);

				ExprFunctions.<II2IFunction>addVectorizable("edge", (edge, x) -> (x < edge) ? 0 : 1);
				ExprFunctions.<FF2FFunction>add("edge", (edge, x) -> (x < edge) ? 0 : 1);

			}

			{

			}

			{

			}

			{

			}

			{
				{

					ExprFunctions.<II2IFunction>addVectorizable("fmod", Math::floorMod);
					ExprFunctions.<FF2FFunction>add("fmod", (a, b) -> (a % b + b) % b);
				}
				{
					Random random = new Random();

					ExprFunctions.<V2IFunction>addVectorizable("randomInt", random::nextInt);
					ExprFunctions.<I2IFunction>addVectorizable("randomInt", random::nextInt);
					ExprFunctions.<II2IFunction>addVectorizable("randomInt", (a, b) -> random.nextInt(b - a) + a);

					ExprFunctions.<V2FFunction>add("random", random::nextFloat);
					ExprFunctions.<FF2FFunction>add("random", (min, max) ->
						min + random.nextFloat() * (max - min));
				}
				{

					for (Type.Primitive type : Type.AllPrimitives) {
						add("if", new AbstractTypedFunction(type, new Type[]{Type.Boolean, type, type}) {
							@Override
							public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
								params[0].evaluateTo(context, functionReturn);

								params[
									functionReturn.booleanReturn ? 1 : 2
									].evaluateTo(context, functionReturn);

							}
						});
					}

					for (Type type : VectorType.AllVectorTypes) {
						add("if", new AbstractTypedFunction(type, new Type[]{Type.Boolean, type, type}) {
							@Override
							public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
								params[0].evaluateTo(context, functionReturn);

								params[
									functionReturn.booleanReturn ? 1 : 2
									].evaluateTo(context, functionReturn);

							}
						});
					}

					{

						for (int length = 2; length <= 16; length++) {
							for (Type.Primitive type : Type.AllPrimitives) {
								Type[] params = new Type[length * 2 + 1];
								for (int i = 0; i < length * 2; i += 2) {
									params[i] = Type.Boolean;
									params[i + 1] = type;
								}
								params[length * 2] = type;
								int finalLength = length * 2;
								add("if", new AbstractTypedFunction(type, params) {
									@Override
									public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
										for (int i = 0; i < finalLength; i += 2) {
											params[i].evaluateTo(context, functionReturn);
											if (functionReturn.booleanReturn) {
												params[i + 1].evaluateTo(context, functionReturn);
												return;
											}
											params[finalLength].evaluateTo(context, functionReturn);
										}
									}
								});
							}
						}
					}
				}
				{

					for (int fadeArgs = 0; fadeArgs <= 2; fadeArgs++) {
						addSmooth(false, fadeArgs);
						addSmooth(true, fadeArgs);
					}
				}
			}
		}

		{
			addImplicitCast(Type.Int, Type.Float, r -> r.floatReturn = r.intReturn);

			addExplicitCast(Type.Float, Type.Int, r -> r.intReturn = (int) r.floatReturn);

			addImplicitCast(Type.Int, Type.Boolean, r -> r.booleanReturn = r.intReturn != 0);
		}

		{
			ExprFunctions.<III2BFunction>add("between", (a, min, max) -> a >= min && a <= max);
			ExprFunctions.<FFF2BFunction>add("between", (a, min, max) -> a >= min && a <= max);

			ExprFunctions.<FFF2BFunction>add("equals", (a, b, epsilon) -> Math.abs(a - b) <= epsilon);

			{

				for (int length = 2; length <= 32; length++) {
					Type[] params = new Type[length];
					Arrays.fill(params, Type.Float);
					int finalLength = length;
					ExprFunctions.add("in", new AbstractTypedFunction(
						Type.Boolean,
						params
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							float value = functionReturn.floatReturn;
							for (int i = 1; i < finalLength; i++) {
								params[i].evaluateTo(context, functionReturn);
								if (functionReturn.floatReturn == value) {
									functionReturn.booleanReturn = true;
									return;
								}
							}
							functionReturn.booleanReturn = false;
						}
					});
				}
			}
		}

		{
			for (Type.Primitive type : new Type.Primitive[]{Type.Boolean, Type.Int}) {
				for (int size = 2; size <= 4; size++) {
					TypedFunction function = new VectorConstructor(type, size);

					add(
						Character.toLowerCase(
							type.getClass().getSimpleName().charAt(0)
						) + "vec" + size, function);
				}
			}

			add("vec2", new AbstractTypedFunction(
				VectorType.VEC2,
				new Type[]{Type.Float, Type.Float}
			) {
				@Override
				public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
					params[0].evaluateTo(context, functionReturn);
					float x = functionReturn.floatReturn;

					params[1].evaluateTo(context, functionReturn);
					float y = functionReturn.floatReturn;

					functionReturn.objectReturn = new Vector2f(x, y);
				}
			});

			add("vec3", new AbstractTypedFunction(
				VectorType.VEC3,
				new Type[]{Type.Float, Type.Float, Type.Float}
			) {
				@Override
				public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
					params[0].evaluateTo(context, functionReturn);
					float x = functionReturn.floatReturn;

					params[1].evaluateTo(context, functionReturn);
					float y = functionReturn.floatReturn;

					params[2].evaluateTo(context, functionReturn);
					float z = functionReturn.floatReturn;

					functionReturn.objectReturn = new Vector3f(x, y, z);
				}
			});

			add("vec4", new AbstractTypedFunction(
				VectorType.VEC4,
				new Type[]{Type.Float, Type.Float, Type.Float, Type.Float}
			) {
				@Override
				public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
					params[0].evaluateTo(context, functionReturn);
					float x = functionReturn.floatReturn;

					params[1].evaluateTo(context, functionReturn);
					float y = functionReturn.floatReturn;

					params[2].evaluateTo(context, functionReturn);
					float z = functionReturn.floatReturn;

					params[3].evaluateTo(context, functionReturn);
					float w = functionReturn.floatReturn;

					functionReturn.objectReturn = new Vector4f(x, y, z, w);
				}
			});
		}

		{

			String[][] accessNames = new String[][]{
				new String[]{"0", "r", "x", "s"},
				new String[]{"1", "g", "y", "t"},
				new String[]{"2", "b", "z", "p"},
				new String[]{"3", "a", "w", "q"}
			};

			{

				for (String access : accessNames[0]) {
					ExprFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Float,
						new Type[]{VectorType.VEC2}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.floatReturn = ((Vector2f) functionReturn.objectReturn).x;
						}
					});

					ExprFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Int,
						new Type[]{VectorType.I_VEC2}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.intReturn = ((Vector2i) functionReturn.objectReturn).x;
						}
					});

					ExprFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Float,
						new Type[]{VectorType.VEC3}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.floatReturn = ((Vector3f) functionReturn.objectReturn).x;
						}
					});

					ExprFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Int,
						new Type[]{VectorType.I_VEC3}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.intReturn = ((Vector3i) functionReturn.objectReturn).x;
						}
					});

					ExprFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Float,
						new Type[]{VectorType.VEC4}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.floatReturn = ((Vector4f) functionReturn.objectReturn).x;
						}
					});

					ExprFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Int,
						new Type[]{VectorType.I_VEC4}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.intReturn = ((Vector4i) functionReturn.objectReturn).x;
						}
					});
				}
			}

			{

				for (String access : accessNames[1]) {
					ExprFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Float,
						new Type[]{VectorType.VEC2}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.floatReturn = ((Vector2f) functionReturn.objectReturn).y;
						}
					});

					ExprFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Int,
						new Type[]{VectorType.I_VEC2}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.intReturn = ((Vector2i) functionReturn.objectReturn).y;
						}
					});

					ExprFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Float,
						new Type[]{VectorType.VEC3}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.floatReturn = ((Vector3f) functionReturn.objectReturn).y;
						}
					});

					ExprFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Int,
						new Type[]{VectorType.I_VEC3}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.intReturn = ((Vector3i) functionReturn.objectReturn).y;
						}
					});

					ExprFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Float,
						new Type[]{VectorType.VEC4}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.floatReturn = ((Vector4f) functionReturn.objectReturn).y;
						}
					});

					ExprFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Int,
						new Type[]{VectorType.I_VEC4}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.intReturn = ((Vector4i) functionReturn.objectReturn).y;
						}
					});
				}
			}

			{

				for (String access : accessNames[2]) {
					ExprFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Float,
						new Type[]{VectorType.VEC3}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.floatReturn = ((Vector3f) functionReturn.objectReturn).z;
						}
					});

					ExprFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Int,
						new Type[]{VectorType.I_VEC3}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.intReturn = ((Vector3i) functionReturn.objectReturn).z;
						}
					});

					ExprFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Float,
						new Type[]{VectorType.VEC4}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.floatReturn = ((Vector4f) functionReturn.objectReturn).z;
						}
					});

					ExprFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Int,
						new Type[]{VectorType.I_VEC4}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.intReturn = ((Vector4i) functionReturn.objectReturn).z;
						}
					});
				}
			}

			{

				for (String access : accessNames[3]) {
					ExprFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Float,
						new Type[]{VectorType.VEC4}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.floatReturn = ((Vector4f) functionReturn.objectReturn).w;
						}
					});

					ExprFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
						Type.Int,
						new Type[]{VectorType.I_VEC4}
					) {
						@Override
						public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
							params[0].evaluateTo(context, functionReturn);
							functionReturn.intReturn = ((Vector4i) functionReturn.objectReturn).w;
						}
					});
				}
			}

			{

				for (int i = 0; i < 4; i++) {
					for (String access : accessNames[i]) {
						int finalI = i;
						ExprFunctions.add("<access$" + access + ">", new AbstractTypedFunction(
							VectorType.VEC4,
							new Type[]{MatrixType.MAT4}
						) {
							@Override
							public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
								params[0].evaluateTo(context, functionReturn);
								functionReturn.objectReturn = ((Matrix4f) functionReturn.objectReturn).getColumn(finalI, new Vector4f());
							}
						});
					}
				}
			}
		}

		functions = builder.build();
	}

	static <T extends TypedFunction> void addVectorized(String name, T function) {
		if (function.getReturnType() instanceof Type.Primitive) {
			add(name, new VectorizedFunction(function, 2));
			add(name, new VectorizedFunction(function, 3));
			add(name, new VectorizedFunction(function, 4));
		} else {
			throw new IllegalArgumentException(name + " is not vectorizable");
		}
	}

	static <T extends TypedFunction> void addVectorizable(String name, T function) {
		add(name, function);
		addVectorized(name, function);
	}

	static <T extends TypedFunction> void addBooleanVectorizable(String name, T function) {
		assert function.getReturnType().equals(Type.Boolean);
		add(name, function);
		if (function.getReturnType() instanceof Type.Primitive) {
			add(name, new BooleanVectorizedFunction(function, 2));
			add(name, new BooleanVectorizedFunction(function, 3));
			add(name, new BooleanVectorizedFunction(function, 4));
		} else {
			throw new IllegalArgumentException(name + " is not vectorizable");
		}
	}

	static <T> void addUnaryOpJOML(String name, VectorType.JOMLVector<T> type, BiConsumer<T, T> function) {
		builder.add(name, new AbstractTypedFunction(
			type,
			new Type[]{type}
		) {
			final private T vector = type.create();

			@SuppressWarnings("unchecked")
			@Override
			public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
				params[0].evaluateTo(context, functionReturn);
				T a = (T) functionReturn.objectReturn;

				function.accept(a, this.vector);
				functionReturn.objectReturn = this.vector;
			}
		});
	}

	static <T> void addBinaryOpJOML(String name, VectorType.JOMLVector<T> type, TriConsumer<T, T, T> function) {
		builder.add(name, new AbstractTypedFunction(
			type,
			new Type[]{type, type}
		) {
			final private T vector = type.create();

			@SuppressWarnings("unchecked")
			@Override
			public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
				params[0].evaluateTo(context, functionReturn);
				T a = (T) functionReturn.objectReturn;

				params[1].evaluateTo(context, functionReturn);
				T b = (T) functionReturn.objectReturn;

				function.accept(a, b, this.vector);
				functionReturn.objectReturn = this.vector;
			}
		});
	}

	static <T> void addTernaryOpJOML(String name, VectorType.JOMLVector<T> type, QuadConsumer<T, T, T, T> function) {
		builder.add(name, new AbstractTypedFunction(
			type,
			new Type[]{type, type, type}
		) {
			final private T vector = type.create();

			@SuppressWarnings("unchecked")
			@Override
			public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
				params[0].evaluateTo(context, functionReturn);
				T a = (T) functionReturn.objectReturn;

				params[1].evaluateTo(context, functionReturn);
				T b = (T) functionReturn.objectReturn;

				params[2].evaluateTo(context, functionReturn);
				T c = (T) functionReturn.objectReturn;

				function.accept(a, b, c, this.vector);
				functionReturn.objectReturn = this.vector;
			}
		});
	}

	static <T> void addBinaryToBooleanOpJOML(
		String name,
		VectorType.JOMLVector<T> type,
		boolean inverted,
		ObjectObject2BooleanFunction<T, T> function) {
		builder.add(name, new AbstractTypedFunction(
			type,
			new Type[]{type, type}
		) {
			@SuppressWarnings("unchecked")
			@Override
			public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
				params[0].evaluateTo(context, functionReturn);
				T a = (T) functionReturn.objectReturn;

				params[1].evaluateTo(context, functionReturn);
				T b = (T) functionReturn.objectReturn;

				functionReturn.objectReturn = function.apply(a, b) != inverted;
			}
		});
	}

	static <T extends TypedFunction> void add(String name, T function) {
		builder.add(name, function);
	}

	static void addCast(final String name, final Type from, final Type to, final Consumer<FunctionReturn> function) {
		add(name, new TypedFunction() {
			@Override
			public Type getReturnType() {
				return to;
			}

			@Override
			public Parameter[] getParameters() {
				return new Parameter[]{new Parameter(from)};
			}

			@Override
			public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
				params[0].evaluateTo(context, functionReturn);
				function.accept(functionReturn);
			}
		});
	}

	static void addImplicitCast(final Type from, final Type to, final Consumer<FunctionReturn> function) {
		addCast("<cast>", from, to, function);
		addExplicitCast(from, to, function);
	}

	static void addExplicitCast(final Type from, final Type to, final Consumer<FunctionReturn> function) {
		addCast("to" + to.getClass().getSimpleName(), from, to, function);
	}

	private static void addSmooth(boolean withId, int fadeArgs) {
		int base = withId ? 1 : 0;
		Parameter[] parameters = new Parameter[base + 1 + fadeArgs];
		if (withId) {
			parameters[0] = new Parameter(Type.Float, true);
		}
		for (int i = base; i < parameters.length; i++) {
			parameters[i] = new Parameter(Type.Float, false);
		}

		builder.addDynamicFunction("smooth", Type.Float, () ->
			new AbstractTypedFunction(Type.Float, parameters, withId ? 1 : 0, false) {

				private final Smoothed smoothed = FrameSmoothed.tracked();

				@Override
				public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn functionReturn) {
					params[base].evaluateTo(context, functionReturn);
					float target = functionReturn.floatReturn;

					float up = 1.0F;
					float down = 1.0F;
					if (fadeArgs >= 1) {
						params[base + 1].evaluateTo(context, functionReturn);
						up = functionReturn.floatReturn;
						down = up;
					}
					if (fadeArgs == 2) {
						params[base + 2].evaluateTo(context, functionReturn);
						down = functionReturn.floatReturn;
					}

					float dt = context instanceof FrameClock clock ? clock.deltaSeconds() : 0.0F;
					functionReturn.floatReturn = smoothed.updateAndGet(target, up, down, dt);
				}
			});
	}

	private ExprFunctions() {
	}

	interface ObjectObject2BooleanFunction<T, U> {
		boolean apply(T t, U u);
	}

	interface TriConsumer<T, U, V> {
		void accept(T t, U u, V v);
	}

	interface QuadConsumer<T, U, V, W> {
		void accept(T t, U u, V v, W w);
	}
}

