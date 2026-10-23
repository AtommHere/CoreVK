package net.corevk.corevk.uniform.expr.kroppeb.stareval.function;

import net.corevk.corevk.uniform.expr.kroppeb.stareval.expression.Expression;

public interface FunctionContext {
	Expression getVariable(String name);

	boolean hasVariable(String name);
}
