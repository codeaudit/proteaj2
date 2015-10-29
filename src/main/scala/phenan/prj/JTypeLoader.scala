package phenan.prj

import scalaz.Memo._
import scalaz.syntax.traverse._
import scalaz.std.list._
import scalaz.std.option._

class JTypeLoader (compiler: JCompiler) {
  val arrayOf: JType => JArrayType = mutableHashMapMemo(getArrayType)

  def getObjectType (clazz: JClass, args: List[MetaArgument]): Option[JObjectType] = {
    val result = getLoadedObjectType(clazz, args)
    if (validTypeArgs(clazz.signature.metaParams, args, result.env)) Some(result)
    else {
      state.error("invalid type arguments of class " + clazz.name + " : " + args.mkString("<", ",", ">"))
      None
    }
  }

  lazy val typeType = compiler.classLoader.typeClass.objectType(Nil)
  lazy val objectType = compiler.classLoader.objectClass.flatMap(_.objectType(Nil))
  lazy val stringType = compiler.classLoader.stringClass.flatMap(_.objectType(Nil))
  lazy val anyClassType = compiler.classLoader.classClass.flatMap(_.objectType(List(JWildcard(None, None))))

  lazy val byte    = compiler.classLoader.byte.primitiveType
  lazy val char    = compiler.classLoader.char.primitiveType
  lazy val double  = compiler.classLoader.double.primitiveType
  lazy val float   = compiler.classLoader.float.primitiveType
  lazy val int     = compiler.classLoader.int.primitiveType
  lazy val long    = compiler.classLoader.long.primitiveType
  lazy val short   = compiler.classLoader.short.primitiveType
  lazy val boolean = compiler.classLoader.boolean.primitiveType
  lazy val void    = compiler.classLoader.void.primitiveType

  lazy val superTypesOfArray: List[JObjectType] = CommonNames.superClassesOfArray.flatMap { name =>
    compiler.classLoader.loadClass_PE(name).flatMap(_.objectType(Nil))
  }

  lazy val runtimeExceptionType = compiler.classLoader.runtimeExceptionClass.flatMap(_.objectType(Nil))
  lazy val errorType = compiler.classLoader.errorClass.flatMap(_.objectType(Nil))

  lazy val uncheckedExceptionTypes = (runtimeExceptionType ++ errorType).toList

  def iterableOf (arg: JRefType) = compiler.classLoader.iterableClass.flatMap(_.objectType(List(arg)))
  def classTypeOf (arg: JType) = boxing(arg).flatMap(t => compiler.classLoader.classClass.flatMap(_.objectType(List(t))))
  def functionTypeOf (from: JType, to: JType) = for {
    f <- boxing(from)
    t <- boxing(to)
    func <- compiler.classLoader.functionClass
    r <- func.objectType(List(f, t))
  } yield r

  def boxing (t: JType): Option[JRefType] = t match {
    case ref: JRefType       => Some(ref)
    case prm: JPrimitiveType => prm.boxed
  }

  def fromTypeSignature (sig: JTypeSignature, env: Map[String, MetaArgument]): Option[JType] = sig match {
    case p: JPrimitiveTypeSignature => Some(fromPrimitiveSignature(p))
    case s                          => fromTypeSignature_RefType(s, env)
  }

  def fromTypeSignature_RefType (sig: JTypeSignature, env: Map[String, MetaArgument]): Option[JRefType] = sig match {
    case cts: JClassTypeSignature        => fromClassTypeSignature(cts, env)
    case tvs: JTypeVariableSignature     => fromTypeVariableSignature(tvs, env)
    case JArrayTypeSignature(component)  => fromTypeSignature(component, env).map(_.array)
    case cap: JCapturedWildcardSignature => fromCapturedWildcardSignature(cap, env)
    case prm: JPrimitiveTypeSignature    =>
      state.error("do not use this method for primitive type signature : " + prm)
      None
  }

  def fromClassTypeSignature (sig: JClassTypeSignature, env: Map[String, MetaArgument]): Option[JObjectType] = sig match {
    case JTypeSignature.typeTypeSig => typeType
    case SimpleClassTypeSignature(className, typeArgs) => for {
      clazz <- compiler.classLoader.loadClass_PE(className)
      args  <- fromTypeArguments(typeArgs, env)
    } yield getLoadedObjectType(clazz, args)
    case MemberClassTypeSignature(outer, name, typeArgs) => ???    // not supported yet
  }

  def fromTypeVariableSignature (sig: JTypeVariableSignature, env: Map[String, MetaArgument]): Option[JRefType] = env.get(sig.name).flatMap {
    case t: JRefType  => Some(t)
    case w: JWildcard => w.upperBound.orElse(objectType).map(ub => JCapturedWildcardType(ub, w.lowerBound))
    case p: MetaValue =>
      state.error("invalid type variable : " + sig.name)
      None
  }

  def fromCapturedWildcardSignature (sig: JCapturedWildcardSignature, env: Map[String, MetaArgument]): Option[JCapturedWildcardType] = {
    sig.upperBound.flatMap(ub => fromTypeSignature_RefType(ub, env)).orElse(objectType).map { ub =>
      JCapturedWildcardType(ub, sig.lowerBound.flatMap(lb => fromTypeSignature_RefType(lb, env)))
    }
  }

  def fromPrimitiveSignature (p: JPrimitiveTypeSignature): JPrimitiveType = compiler.classLoader.erase(p).primitiveType

  def fromTypeArguments (args: List[JTypeArgument], env: Map[String, MetaArgument]): Option[List[MetaArgument]] = args.traverse {
    case sig: JTypeSignature            => fromTypeSignature_RefType(sig, env)
    case WildcardArgument(upper, lower) => Some(JWildcard(upper.flatMap(fromTypeSignature_RefType(_, env)).filterNot(objectType.contains), lower.flatMap(fromTypeSignature_RefType(_, env))))
    case MetaVariableSignature(name)    => env.get(name)
  }

  def validTypeArgs (params: List[FormalMetaParameter], args: List[MetaArgument], env: Map[String, MetaArgument]): Boolean = {
    if (params.isEmpty || args.isEmpty) params.isEmpty && args.isEmpty
    else if (validTypeArg(params.head, args.head, env)) validTypeArgs(params.tail, args.tail, env)
    else false
  }

  private def validTypeArg (param: FormalMetaParameter, arg: MetaArgument, env: Map[String, MetaArgument]): Boolean = arg match {
    case arg: JRefType => param.bounds.forall(withinBound(_, arg, env))
    case pv: MetaValue => fromTypeSignature(param.metaType, env).exists(pv.valueType <:< _)
    case wc: JWildcard => param.bounds.forall(bound => wc.upperBound.orElse(objectType).exists(upper => withinBound(bound, upper, env)))
  }

  private def withinBound (bound: JTypeSignature, arg: JRefType, env: Map[String, MetaArgument]): Boolean = {
    arg.isSubtypeOf(fromTypeSignature(bound, env).get)
  }

  def state = compiler.state

  private def getArrayType (component: JType): JArrayType = JArrayType(component)

  private def getLoadedObjectType (clazz: JClass, args: List[MetaArgument]): JObjectType = memoizedGetObjectType((clazz, clazz.signature.metaParams.map(_.name).zip(args).toMap))

  private val memoizedGetObjectType: ((JClass, Map[String, MetaArgument])) => JObjectType = mutableHashMapMemo { pair => new JObjectType(pair._1, pair._2) }
}
