package phenan.prj.body

import phenan.prj._
import phenan.prj.combinator._
import phenan.prj.ir._

import scala.language.implicitConversions
import scala.util._

import scalaz.Memo._

class BodyParsers (compiler: JCompiler) extends TwoLevelParsers {
  type Elem = Char

  class StatementParsers private (returnType: JType, env: Environment) {
    lazy val block = '{' ~> blockStatements <~ '}' ^^ IRBlock

    lazy val blockStatements: HParser[List[IRStatement]] = statement_BlockStatements | local_BlockStatements | expression_BlockStatements | success(Nil).^

    private lazy val statement_BlockStatements = statement ~ blockStatements ^^ { case s ~ bs => s :: bs }

    private lazy val local_BlockStatements = localDeclarationStatement >> { local =>
      StatementParsers(returnType, env.defineLocals(local.declaration)).blockStatements ^^ { local :: _ }
    }

    private lazy val expression_BlockStatements = expressionStatement >> { es =>
      StatementParsers(returnType, env.modifyContext(es.expression)).blockStatements ^^ { es :: _ }
    }

    lazy val statement: HParser[IRStatement] = block | controlStatement

    lazy val controlStatement: HParser[IRStatement] = ifStatement | whileStatement | forStatement | returnStatement

    lazy val ifStatement: HParser[IRIfStatement] = ( "if" ~> '(' ~> expression(compiler.typeLoader.boolean) <~ ')' ) ~ statement ~ ( "else" ~> statement ).? ^^ {
      case cond ~ thenStmt ~ elseStmt => IRIfStatement(cond, thenStmt, elseStmt)
    }

    lazy val whileStatement = ( "while" ~> '(' ~> expression(compiler.typeLoader.boolean) <~ ')' ) ~ statement ^^ {
      case cond ~ stmt => IRWhileStatement(cond, stmt)
    }

    lazy val forStatement = normalForStatement | ancientForStatement | enhancedForStatement

    lazy val normalForStatement = "for" ~> '(' ~> localDeclaration <~ ';' >> { local =>
      StatementParsers(returnType, env.defineLocals(local)).forControlRest ^^ {
        case cond ~ update ~ stmt => IRNormalForStatement(local, cond, update, stmt)
      }
    }

    lazy val ancientForStatement = "for" ~> '(' ~> ( statementExpressionList <~ ';' ) ~ forControlRest ^^ {
      case init ~ ( cond ~ update ~ stmt ) => IRAncientForStatement(init, cond, update, stmt)
    }

    lazy val enhancedForStatement = ( "for" ~> '(' ~> typeName ) ~ identifier ~ emptyBrackets <~ ':' >> {
      case elemType ~ name ~ dim => collection(elemType.array(dim)) ~ ( ')' ~> StatementParsers(returnType, env.defineLocal(elemType.array(dim), name)).statement ) ^^ {
        case set ~ stmt => IREnhancedForStatement(elemType, name, dim, set, stmt)
      }
    }

    private lazy val forControlRest = expression(compiler.typeLoader.boolean).? ~ ( ';' ~> statementExpressionList ) ~ ( ')' ~> statement )


    lazy val returnStatement = "return" ~> expression(returnType) <~ ';' ^^ IRReturnStatement

    lazy val localDeclarationStatement = localDeclaration <~ ';' ^^ IRLocalDeclarationStatement

    lazy val localDeclaration: HParser[IRLocalDeclaration] = typeName >> { t =>
      ExpressionParsers(t, env).variableDeclarator.+(',') ^^ { IRLocalDeclaration(t, _) }
    }

    lazy val expressionStatement = statementExpression <~ ';' ^^ IRExpressionStatement

    lazy val statementExpressionList = statementExpression.*(',')

    lazy val statementExpression = expression(compiler.typeLoader.void)


    def collection (elemType: JType) =
      compiler.state.someOrError(elemType.boxed.flatMap(compiler.typeLoader.iterableOf).map(expression).map(_ | expression(elemType.array)),
        "cannot get type object Iterable<" + elemType.name + ">" , expression(elemType.array))


    def expression (expected: JType) = ExpressionParsers(expected, env).expression

    lazy val typeName = TypeParsers(env.resolver).typeName
  }

  class ExpressionParsers private (expected: JType, env: Environment) {
    lazy val variableDeclarator: HParser[IRVariableDeclarator] = identifier ~ emptyBrackets >> {
      case id ~ dim => ( '=' ~> ExpressionParsers(expected.array(dim), env).expression ).? ^^ { IRVariableDeclarator(id, dim, _) }
    }

    def expression: HParser[IRExpression] = env.highestPriority(expected).map(cached).getOrElse(hostExpression)

    def expression (priority: JPriority): HParser[IRExpression] = cached(priority)

    lazy val hostExpression: HParser[IRExpression] = ???

    private val cached: JPriority => HParser[IRExpression] = mutableHashMapMemo { p =>
      env.expressionOperators(expected, p).map(OperatorParsers(_, env).operator).reduce(_ ||| _) | env.nextPriority(expected, p).map(cached).getOrElse(hostExpression)
    }
  }

  class OperatorParsers private (syntax: JSyntax, env: Environment) {
    lazy val operator: HParser[IROperation] = pattern ^^ { IROperation(syntax, _) }
    lazy val pattern: HParser[List[IRExpression]] = ???

    // return typeArgs?
    def constructParser (pattern: List[JSyntaxElement], typeArgs: Map[String, MetaValue], env: Environment): HParser[List[IRExpression]] = pattern match {
      case JOperand(param) :: rest           => parameter(param, env) >> { e => constructParser(rest, typeArgs, env.modifyContext(e)) ^^ { e :: _ } }
      case JOptionalOperand(param) :: rest   => parameter(param, env).? >> {
        case Some(e) => constructParser(rest, typeArgs, env.modifyContext(e)) ^^ { e :: _ }
        case None    => constructParser(rest, typeArgs, env) ^^ { IRDefaultArgument(param) :: _ }
      }
      case JRepetition0(param) :: rest       => parameter(param, env).* ~ constructParser(rest, typeArgs, env) ^^ {
        case es ~ args => IRVariableArguments(es) :: args
      }
      case JRepetition1(param) :: rest       => parameter(param, env).+ ~ constructParser(rest, typeArgs, env) ^^ {
        case es ~ args => IRVariableArguments(es) :: args
      }
      case JOperatorName(name) :: rest       => word(name).^ ~> constructParser(rest, typeArgs, env)
      case JMetaOperand(name, param) :: rest => parameter(param, env) >> { arg => constructParser(rest, compiler.state.successOrError(arg.eval.map(v => typeArgs + (name -> v)), "invalid meta operand : cannot evaluate parameter " + name, typeArgs), env) }
      case JMetaValue(value) :: rest         => metaValue(value) ~> constructParser(rest, typeArgs, env)
      case JAndPredicate(param) :: rest      => parameter(param, env).& ~> constructParser(rest, typeArgs, env)
      case JNotPredicate(param) :: rest      => parameter(param, env).! ~> constructParser(rest, typeArgs, env)
      case Nil                               => success(Nil).^
    }

    def parameter (param: JParameter, env: Environment): HParser[IRExpression] = ???

    def metaValue (mv: MetaValue): HParser[MetaValue] = ???
  }

  class TypeParsers private (resolver: NameResolver) {
    lazy val metaValue: HParser[MetaValue] = wildcard | metaVariable | refType
    lazy val typeName: HParser[JType] = primitiveTypeName | refType
    lazy val refType: HParser[JRefType] = arrayType | typeVariable | objectType
    lazy val objectType: HParser[JObjectType] = className ~ ( '<' ~> metaValue.+(',') <~ '>' ).? ^^? {
      case clazz ~ args => clazz.objectType(args.getOrElse(Nil))
    }
    lazy val packageName: HParser[List[String]] = (identifier <~ '.').*! { names =>
      ! resolver.root.isKnownPackage(names) && resolver.resolve(names).isSuccess
    }
    lazy val className: HParser[JClass] = ref(innerClassName | topLevelClassName)
    lazy val topLevelClassName: HParser[JClass] = packageName ~ identifier ^^? {
      case pack ~ name => resolver.resolve(pack :+ name).toOption
    }
    lazy val innerClassName: HParser[JClass] = className ~ ('.' ~> identifier) ^^? {
      case name ~ id => name.innerClasses.get(id).flatMap(compiler.classLoader.loadClass_PE)
    }
    lazy val typeVariable: HParser[JTypeVariable] = identifier ^^? resolver.typeVariable
    lazy val metaVariable: HParser[PureVariableRef] = identifier ^^? resolver.metaVariable
    lazy val arrayType: HParser[JArrayType] = typeName <~ emptyBracket ^^ { _.array }
    lazy val primitiveTypeName: HParser[JPrimitiveType] = identifier ^? {
      case "byte"    => compiler.classLoader.byte.primitiveType
      case "char"    => compiler.classLoader.char.primitiveType
      case "double"  => compiler.classLoader.double.primitiveType
      case "float"   => compiler.classLoader.float.primitiveType
      case "int"     => compiler.classLoader.int.primitiveType
      case "long"    => compiler.classLoader.long.primitiveType
      case "short"   => compiler.classLoader.short.primitiveType
      case "boolean" => compiler.classLoader.boolean.primitiveType
      case "void"    => compiler.classLoader.void.primitiveType
    }
    lazy val wildcard: HParser[JWildcard] = '?' ~> ( "extends" ~> refType ).? ~ ( "super" ~> refType ).? ^^ {
      case ub ~ lb => JWildcard(ub, lb)
    }
  }

  object StatementParsers {
    def apply (expected: JType, env: Environment): StatementParsers = cached((expected, env))
    private val cached : ((JType, Environment)) => StatementParsers = mutableHashMapMemo { pair => new StatementParsers(pair._1, pair._2) }
  }

  object ExpressionParsers {
    def apply (expected: JType, env: Environment): ExpressionParsers = cached((expected, env))
    private val cached : ((JType, Environment)) => ExpressionParsers = mutableHashMapMemo { pair => new ExpressionParsers(pair._1, pair._2) }
  }

  object OperatorParsers {
    def apply (syntax: JSyntax, env: Environment): OperatorParsers = cached((syntax, env))
    private val cached : ((JSyntax, Environment)) => OperatorParsers = mutableHashMapMemo { pair => new OperatorParsers(pair._1, pair._2) }
  }

  object TypeParsers {
    def apply (resolver: NameResolver): TypeParsers = cached(resolver)
    private val cached : NameResolver => TypeParsers = mutableHashMapMemo(new TypeParsers(_))
  }

  lazy val delimiter: LParser[Any] = elem("white space", Character.isWhitespace).*

  lazy val emptyBrackets = emptyBracket.* ^^ { _.size }
  lazy val emptyBracket = '[' ~> ']'

  lazy val qualifiedName = identifier.+('.')

  lazy val identifier = (elem("identifier start", Character.isJavaIdentifierStart) ~ elem("identifier part", Character.isJavaIdentifierPart).*).^ ^^ {
    case s ~ ps => (s :: ps).mkString
  }

  private def word (cs: String): LParser[String] = cs.foldRight(success(cs)) { (ch, r) => elem(ch) ~> r }
  private implicit def keyword (kw: String): HParser[String] = (word(kw) <~ elem("identifier part", Character.isJavaIdentifierPart).!).^
  private implicit def symbol (ch: Char): HParser[Char] = elem(ch).^
}
