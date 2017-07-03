package phenan.prj.body

import phenan.prj._
import phenan.prj.ir._

import scalaz.Memo._

trait LiteralOperatorParsersModule {
  this: LiteralOperandParsersModule with CommonParsersModule with ContextSensitiveParsersModule
    with Environments with DSLEnvironments with EnvModifyStrategy
    with IRs with IRExpressions with Syntax with JModules with JMembers with JErasedTypes with Application =>

  trait LiteralOperatorParsers {
    this: LiteralOperandParsers with CommonParsers with ContextSensitiveParsers =>

    def getLiteralOperatorParser(lop: LiteralOperator): ContextSensitiveScanner[IRExpression] = cached(lop).operator

    trait LiteralOperatorParsersInterface {
      def operator: ContextSensitiveScanner[IRExpression]
    }

    private val cached: LiteralOperator => LiteralOperatorParsersInterface = mutableHashMapMemo { new LiteralOperatorParsersImpl(_) }

    private class LiteralOperatorParsersImpl(lop: LiteralOperator) extends LiteralOperatorParsersInterface {
      lazy val operator: ContextSensitiveScanner[IRExpression] = constructParser(lop.syntax.pattern, lop.metaArgs, Nil)

      private def constructParser(pattern: List[JSyntaxElement], binding: Map[String, MetaArgument], operands: List[IRExpression]): ContextSensitiveScanner[IRExpression] = pattern match {
        case JOperand(param, p) :: rest =>
          getLiteralOperandParser(param, p, binding, lop) >> {
            case (bind, arg) => constructParser(rest, bind, arg :: operands)
          }
        case JOptionalOperand(param, p) :: rest =>
          (getLiteralOperandParser(param, p, binding, lop) | defaultArgumentParser(param, binding).^#) >> {
            case (bind, arg) => constructParser(rest, bind, arg :: operands)
          }
        case JRepetition0(param, p) :: rest =>
          rep0(param, p, binding, Nil) >> {
            case (bnd, args) => constructParser(rest, bnd, IRVariableArguments(args, param.genericType.bind(bnd)) :: operands)
          }
        case JRepetition1(param, p) :: rest =>
          rep1(param, p, binding, Nil) >> {
            case (bnd, args) => constructParser(rest, bnd, IRVariableArguments(args, param.genericType.bind(bnd)) :: operands)
          }
        case JMetaOperand(name, param, p) :: rest =>
          if (binding.contains(name)) getMetaValueLiteralParser(name, binding(name), p, binding, lop) >> { bind => constructParser(rest, bind, operands) }
          else getMetaLiteralOperandParser(param, p, binding, lop) >> { ma => constructParser(rest, binding + (name -> ma), operands) }
        case JMetaName(name, value, p) :: rest =>
          getMetaValueLiteralParser(name, value, p, binding, lop) >> { bind => constructParser(rest, bind, operands) }
        case JOperatorName(name) :: rest =>
          word(name) ~> constructParser(rest, binding, operands)
        case JRegexName(name) :: rest =>
          ( regex(name.r) ^^# IRStringLiteral ) >> { s => constructParser(rest, binding, s :: operands) }
        case JAndPredicate(param, p) :: rest =>
          getLiteralOperandParser(param, p, binding, lop).& ~> constructParser(rest, binding, operands)
        case JNotPredicate(param, p) :: rest =>
          getLiteralOperandParser(param, p, binding, lop).! ~> constructParser(rest, binding, operands)
        case Nil =>
          ContextSensitiveScanner.success(lop.semantics(binding, operands.reverse))
      }

      private def rep0(param: JParameter, pri: Option[JPriority], binding: Map[String, MetaArgument], args: List[IRExpression]): ContextSensitiveScanner[(Map[String, MetaArgument], List[IRExpression])] = {
        rep1(param, pri, binding, args) | ContextSensitiveScanner.success((binding, args))
      }

      private def rep1(param: JParameter, pri: Option[JPriority], binding: Map[String, MetaArgument], args: List[IRExpression]): ContextSensitiveScanner[(Map[String, MetaArgument], List[IRExpression])] = {
        getLiteralOperandParser(param, pri, binding, lop) >> {
          case (bind, arg) => rep0(param, pri, bind, args :+ arg)
        }
      }

      private def defaultArgumentParser (param: JParameter, binding: Map[String, MetaArgument]): ContextFreeScanner[ParsedArgument] = defaultArgument(param) match {
        case Some(value) => ContextFreeScanner.success((binding, value))
        case None        => ContextFreeScanner.failure("default argument is not found")
      }

      private def defaultArgument (param: JParameter): Option[IRDefaultArgument] = {
        param.defaultArg.flatMap(name => lop.declaringClassModule.findMethod(name, declaringModule).find(_.erasedParameterTypes == Nil)).map(IRDefaultArgument)
      }
    }
  }
}