package phenan.prj.body

import phenan.prj._
import phenan.prj.ir._

trait ExpressionOperandParsersModule {
  this: StatementParsersModule with ExpressionParsersModule with TypeParsersModule
    with CommonParsersModule with ContextSensitiveParsersModule
    with ExpectedTypeInferencer with JTypeLoader with Environments with DSLEnvironments with EnvModifyStrategy
    with IRStatements with IRExpressions with JModules with JMembers =>

  trait ExpressionOperandParsers {
    this: StatementParsers with ExpressionParsers with TypeParsers with CommonParsers with ContextSensitiveParsers =>

    def getExpressionOperandParser(param: JParameter, pri: Option[JPriority], binding: Map[String, MetaArgument], eop: ExpressionOperator): ContextSensitiveParser[ParsedArgument] = {
      for {
        expectedType <- inferExpectedType(param, binding, eop.method)
        contexts     <- inferContexts(param.contexts, binding, eop.method)
        withoutCts   <- inferContexts(param.withoutCts, binding, eop.method)
      } yield {
        val parser =
          if (boxedVoidType == expectedType && contexts.nonEmpty) getStatementExpressionParser(pri, eop.syntax.priority)
          else getExpressionParser(expectedType, pri, eop.syntax.priority)

        parser.withLocalContexts(contexts).withoutLocalContexts(withoutCts).argumentFor(param, binding)
      }
    }.getOrElse(ContextSensitiveParser.failure[ParsedArgument]("fail to parse operand expression"))

    def getMetaExpressionOperandParser(param: JParameter, pri: Option[JPriority], binding: Map[String, MetaArgument], eop: ExpressionOperator): ContextSensitiveParser[MetaArgument] = {
      inferExpectedType(param, binding, eop.method) match {
        case Some(e) => getMetaArgumentParser(e, pri, eop)
        case None => ContextSensitiveParser.failure[MetaArgument]("fail to parse meta expression")
      }
    }

    def getMetaValueExpressionParser(name: String, mv: MetaArgument, pri: Option[JPriority], binding: Map[String, MetaArgument], eop: ExpressionOperator): ContextSensitiveParser[Map[String, MetaArgument]] = mv match {
      case t: JRefType => typeParsers.refType ^? { case v if t == v => t } ^^^# binding
      case w: JWildcard => typeParsers.wildcard ^? { case v if w == v => w } ^^^# binding
      case r: MetaVariableRef => typeParsers.metaVariable ^? { case v if r == v => r } ^^^# binding
      case c: ConcreteMetaValue => getMetaArgumentParser(c.valueType, pri, eop) ^? { case v if c == v => v } ^^^ binding
      case u: JUnboundMetaVariable => getMetaArgumentParser(u.valueType, pri, eop) ^^ { arg => binding + (name -> arg) }
    }

    private def getMetaArgumentParser(metaType: JType, pri: Option[JPriority], eop: ExpressionOperator): ContextSensitiveParser[MetaArgument] = {
      if (typeType == metaType) typeParsers.metaValue.^#
      else getExpressionParser(metaType, pri, eop.syntax.priority) ^^ { arg => ConcreteMetaValue(arg, metaType) }
    }

    private def getStatementExpressionParser (pri: Option[JPriority], enclosingPriority: JPriority): ContextSensitiveParser[IRExpression] = {
      val boxed = getExpressionParser(boxedVoidType, pri, enclosingPriority)
      val unboxed = getExpressionParser(voidType, pri, enclosingPriority) ^^ { e => IRStatementExpression(IRExpressionStatement(e)) }
      val block = getStatementParsers(voidType).block ^^ IRStatementExpression
      boxed | unboxed | block
    }
  }
}