package net.corevk.corevk.uniform.expr.kroppeb.stareval.element.tree;

import net.corevk.corevk.uniform.expr.kroppeb.stareval.element.ExpressionElement;
import net.corevk.corevk.uniform.expr.kroppeb.stareval.parser.BinaryOp;

public record BinaryExpressionElement(BinaryOp op, ExpressionElement left,
									  ExpressionElement right) implements ExpressionElement {

	@Override
	public String toString() {
		return "BinaryExpr{ {" + this.left + "} " + this.op + " {" + this.right + "} }";
	}
}
