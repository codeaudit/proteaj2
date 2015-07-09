package phenan.prj.ir

import phenan.prj.JType

sealed trait IRStatement {
  def activates: List[IRContextRef] = ???
  def deactivates: List[IRContextRef] = ???
}

case class IRBlock (statements: List[IRStatement]) extends IRStatement

case class IRLocalDeclarationStatement (declaration: IRLocalDeclaration) extends IRStatement

case class IRLocalDeclaration (localType: JType, declarators: List[IRVariableDeclarator]) extends IRStatement

case class IRVariableDeclarator (name: String, dim: Int, init: Option[IRExpression])

case class IRIfStatement (condition: IRExpression, thenStatement: IRStatement, elseStatement: Option[IRStatement]) extends IRStatement

case class IRWhileStatement (condition: IRExpression, statement: IRStatement) extends IRStatement

sealed trait IRForStatement extends IRStatement

case class IRNormalForStatement (local: IRLocalDeclaration, condition: Option[IRExpression], update: List[IRExpression], statement: IRStatement) extends IRForStatement
case class IRAncientForStatement (init: List[IRExpression], condition: Option[IRExpression], update: List[IRExpression], statement: IRStatement) extends IRForStatement
case class IREnhancedForStatement (elementType: JType, name: String, dim: Int, iterable: IRExpression, statement: IRStatement) extends IRForStatement

case class IRReturnStatement (expression: IRExpression) extends IRStatement

case class IRExpressionStatement (expression: IRExpression) extends IRStatement