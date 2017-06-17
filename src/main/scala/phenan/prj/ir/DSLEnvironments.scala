package phenan.prj.ir

import phenan.prj._

import scalaz.Memo._

trait DSLEnvironments {
  this: MethodContextInferencer with OperatorPool with IRExpressions with Syntax with JModules with JMembers =>

  class DSLEnvironment(dsls: List[JClassModule], contexts: List[IRContextRef]) {
    def changeContext(activated: List[IRContextRef], deactivated: List[IRContextRef]): DSLEnvironment = {
      val cs = activated ++ contexts.diff(deactivated)
      if (cs == contexts) this
      else new DSLEnvironment(dsls, cs)
    }

    def expressionOperators(expected: JType, priority: JPriority): List[ExpressionOperator] = expressionOperatorsMap(expected).getOrElse(priority, Nil)

    def literalOperators(expected: JType, priority: JPriority): List[LiteralOperator] = literalOperatorsMap(expected).getOrElse(priority, Nil)

    def inferContexts(procedure: JProcedure, bind: Map[String, MetaArgument]): Option[List[IRContextRef]] = inferMethodContexts(procedure, bind, contexts).map(_._1)

    private val expressionOperatorsMap: JType => Map[JPriority, List[ExpressionOperator]] = mutableHashMapMemo { expected =>
      val fromDSL = for {
        dsl       <- dsls
        (s, m, e) <- dslExpressionOperators(dsl, expected)
        (cs, e2)  <- inferMethodContexts(m, e, contexts)
      } yield ExpressionOperator(s, e2, m, { (ma, args) => IRDSLOperation(m, ma, args, cs) })

      val fromContexts = for {
        context   <- contexts
        (s, m, e) <- contextExpressionOperators(context.contextType, expected)
        (cs, e2)  <- inferMethodContexts(m, e, contexts)
      } yield ExpressionOperator(s, e2, m, { (ma, args) => IRContextOperation(context, m, ma, args, cs) })

      (fromDSL ++ fromContexts).groupBy(_.syntax.priority)
    }

    private val literalOperatorsMap: JType => Map[JPriority, List[LiteralOperator]] = mutableHashMapMemo { expected =>
      val fromDSL = for {
        dsl       <- dsls
        (s, m, e) <- dslLiteralOperators(dsl, expected)
        (cs, e2)  <- inferMethodContexts(m, e, contexts)
      } yield LiteralOperator(s, e2, m, { (ma, args) => IRDSLOperation(m, ma, args, cs) })

      val fromContexts = for {
        context   <- contexts
        (s, m, e) <- contextLiteralOperators(context.contextType, expected)
        (cs, e2)  <- inferMethodContexts(m, e, contexts)
      } yield LiteralOperator(s, e2, m, { (ma, args) => IRContextOperation(context, m, ma, args, cs) })

      (fromDSL ++ fromContexts).groupBy(_.syntax.priority)
    }
  }

  case class ExpressionOperator(syntax: JExpressionSyntax, metaArgs: Map[String, MetaArgument], method: JMethod, semantics: (Map[String, MetaArgument], List[IRExpression]) => IRExpression)

  case class LiteralOperator(syntax: JLiteralSyntax, metaArgs: Map[String, MetaArgument], method: JMethod, semantics: (Map[String, MetaArgument], List[IRExpression]) => IRExpression)

}