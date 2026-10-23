package net.corevk.corevk.uniform.expr.kroppeb.stareval.element.token;

import net.corevk.corevk.uniform.expr.kroppeb.stareval.element.ExpressionElement;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.element.PriorityOperatorElement;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.element.tree.UnaryExpressionElement;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.parser.UnaryOp;

public class UnaryOperatorToken extends Token implements PriorityOperatorElement {
	private final UnaryOp op;

	public UnaryOperatorToken(UnaryOp op) {
		this.op = op;
	}

	@Override
	public String toString() {
		return "UnaryOp{" + this.op + "}";
	}

	@Override
	public int getPriority() {
		return -1;
	}

	@Override
	public UnaryExpressionElement resolveWith(ExpressionElement right) {
		return new UnaryExpressionElement(this.op, right);
	}
}
