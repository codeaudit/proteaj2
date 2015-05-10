package phenan.prj.ir

import phenan.prj._
import phenan.prj.declaration._
import phenan.prj.exception._
import phenan.util._

import scala.util._

import scalaz.Memo._

trait NameResolver {
  import scalaz.Scalaz._

  def inClass (clazz: IRClass) = NameResolverInClass(clazz, this)
  def inMethod (method: IRMethod) = NameResolverInMethod(method, this)

  def typeVariable (name: String): Option[JTypeVariableSignature]
  def metaVariable (tn: TypeName): Try[JTypeArgument]
  def resolve (name: String): Try[JClass]
  def root: RootResolver

  def resolve (name: QualifiedName): Try[JClass] =
    resolve(name.names.head).flatMap(root.findInnerClass(_, name.names.tail)).orElse(root.findClass(name.names))

  def metaParameter (mp: MetaParameter): Try[FormalMetaParameter] = mp match {
    case TypeParameter(name, bounds)  => bounds.traverse(typeSignature).map(FormalMetaParameter(name, JTypeSignature.typeTypeSig, _))
    case MetaValueParameter(name, mt) => typeSignature(mt).map(FormalMetaParameter(name, _, Nil))
  }

  def typeSignature (tn: TypeName): Try[JTypeSignature] = nonArrayTypeSignature(tn.name, tn.args).map(JTypeSignature.arraySig(_, tn.dim))

  def classTypeSignature (tn: TypeName): Try[JClassTypeSignature] = {
    if (tn.dim > 0) Failure(InvalidTypeException("expected class type, but found array type : " + tn.name.names.mkString(".") + "[]".multiply(tn.dim)))
    else classTypeSignature(tn.name, tn.args)
  }

  def classTypeSignature (name: String): Try[JClassTypeSignature] = resolve(name).map(clazz => SimpleClassTypeSignature(clazz.internalName, Nil))

  def classTypeSignature (name: QualifiedName): Try[JClassTypeSignature] = resolve(name).map(clazz => SimpleClassTypeSignature(clazz.internalName, Nil))

  def classTypeSignature (name: QualifiedName, args: List[TypeArgument]): Try[JClassTypeSignature] = for {
    clazz <- resolve(name)
    as    <- args.traverse(typeArgument)
  } yield SimpleClassTypeSignature(clazz.internalName, as)

  def parameterSignature (param: FormalParameter, initializer: Option[String]): Try[JParameterSignature] = {
    parameterSignature(Nil, param.parameterType, param.priority, param.varArgs, param.dim, initializer)
  }

  private def parameterSignature (contexts: List[TypeName], parameterType: ParameterType, priority: Option[String], varArgs: Boolean, dim: Int, initializer: Option[String]): Try[JParameterSignature] = parameterType match {
    case ContextualType(c, p) => parameterSignature(contexts :+ c, p, priority, varArgs, dim, initializer)
    case tn: TypeName => for {
      cs  <- contexts.traverse(typeSignature)
      sig <- typeSignature(tn).map(JTypeSignature.arraySig(_, dim))
    } yield JParameterSignature(cs, sig, priority, varArgs, initializer)
  }

  private def nonArrayTypeSignature (name: QualifiedName, args: List[TypeArgument]): Try[JTypeSignature] = {
    if (args.nonEmpty) classTypeSignature(name, args)
    else if (name.names.size > 1) classTypeSignature(name)
    else {
      val simpleName = name.names.head
      primitiveType(simpleName).orElse(typeVariable(simpleName)).map(Success(_)).getOrElse(classTypeSignature(simpleName))
    }
  }

  private def primitiveType (name: String): Option[JPrimitiveTypeSignature] = name match {
    case "byte"    => Some(ByteTypeSignature)
    case "char"    => Some(CharTypeSignature)
    case "double"  => Some(DoubleTypeSignature)
    case "float"   => Some(FloatTypeSignature)
    case "int"     => Some(IntTypeSignature)
    case "long"    => Some(LongTypeSignature)
    case "short"   => Some(ShortTypeSignature)
    case "boolean" => Some(BoolTypeSignature)
    case "void"    => Some(VoidTypeSignature)
    case _ => None
  }

  private def typeArgument (arg: TypeArgument): Try[JTypeArgument] = arg match {
    case UpperBoundWildcardType(bound) => typeSignature(bound).map(UpperBoundWildcardArgument)
    case LowerBoundWildcardType(bound) => typeSignature(bound).map(LowerBoundWildcardArgument)
    case UnboundWildcardType           => Success(UnboundWildcardArgument)
    case tn: TypeName                  => metaVariable(tn)
  }
}

object NameResolver {
  def root (compiler: JCompiler) = RootResolver(compiler)
}

case class RootResolver (compiler: JCompiler) extends NameResolver {
  def file (file: IRFile): NameResolver = NameResolverInFile(file, this)

  def typeVariable (name: String) = None

  def metaVariable (tn: TypeName) = typeSignature(tn)

  def resolve (name: String): Try[JClass] = abbreviated(name).orElse(findClass(name))

  def findClass (name: List[String]): Try[JClass] = {
    if (name.size > 1) findClass(List(name.head), name.tail.head, name.tail.tail)
    else resolve(name.head)
  }

  def findClass (packageName: List[String], name: String, rest: List[String]): Try[JClass] = {
    val names = packageName :+ name
    if (knownPackages.contains(names)) findClass(names, rest.head, rest.tail)
    else findClass(names.mkString("/")) match {
      case Success(clazz) =>
        addKnownPackages(packageName)
        findInnerClass(clazz, rest)
      case fail => rest match {
        case head :: tail => findClass(names, head, tail)
        case Nil          => fail
      }
    }
  }

  def addKnownPackages (packageName: List[String]): Unit = {
    if (! knownPackages.contains(packageName)) {
      knownPackages += packageName
      if (packageName.size > 1) addKnownPackages(packageName.init)
    }
  }

  def findInnerClass (clazz: JClass, name: List[String]): Try[JClass] = name match {
    case head :: tail => findInnerClass(clazz, head) match {
      case Success(inner) => findInnerClass(inner, tail)
      case fail => fail
    }
    case Nil => Success(clazz)
  }

  def findInnerClass (clazz: JClass, name: String): Try[JClass] = compiler.classLoader.loadInnerClass(clazz, name)
  def findClass (name: String): Try[JClass] = compiler.classLoader.loadClass(name)

  def root = this

  private val abbreviated: String => Try[JClass] = mutableHashMapMemo { name =>
    findClass("java/lang/" + name).orElse(findClass("proteaj/lang/" + name))
  }

  private val knownPackages: scala.collection.mutable.Set[List[String]] = scala.collection.mutable.Set (
    List("java"), List("java", "lang"), List("java", "util"), List("java", "io"),
    List("proteaj"), List("proteaj", "lang")
  )
}

case class NameResolverInFile (file: IRFile, root: RootResolver) extends NameResolver {
  def typeVariable (name: String) = None

  def metaVariable (tn: TypeName) = root.metaVariable(tn)

  def resolve (name: String): Try[JClass] = abbreviated(name).orElse(root.resolve(name))

  private lazy val packageName = file.ast.header.pack.map(_.name.names)

  private lazy val importedClasses: Map[String, List[String]] = file.ast.header.imports.collect {
    case ClassImportDeclaration(name) => name.names.last -> name.names
  }.toMap

  private lazy val importedPackages: List[List[String]] = file.ast.header.imports.collect {
    case PackageImportDeclaration(name) => name.names
  }

  private val abbreviated: String => Try[JClass] = mutableHashMapMemo { name =>
    if (importedClasses.contains(name)) root.findClass(importedClasses(name))
    else packageName match {
      case Some(pack) => root.findClass(pack, name, Nil).orElse(searchImportedPackages(name, importedPackages))
      case None       => searchImportedPackages(name, importedPackages)
    }
  }

  private def searchImportedPackages (name: String, imported: List[List[String]]): Try[JClass] = imported match {
    case pack :: rest => root.findClass(pack, name, Nil) match {
      case s: Success[_] => s
      case _: Failure[_] => searchImportedPackages(name, rest)
    }
    case Nil => Failure(ClassFileNotFoundException("class name " + name + " cannot be resolved"))
  }
}

case class NameResolverInClass (clazz: IRClass, parent: NameResolver) extends MetaParametersResolver {
  def resolve (name: String): Try[JClass] = abbreviated(name).orElse(parent.resolve(name))

  def metaParameters: List[MetaParameter] = clazz.metaParametersAST

  private val abbreviated: String => Try[JClass] = mutableHashMapMemo { name => root.findInnerClass(clazz, name) }
}

case class NameResolverInMethod (method: IRMethod, parent: NameResolver) extends MetaParametersResolver {
  def resolve (name: String) = parent.resolve(name)

  def metaParameters: List[MetaParameter] = method.metaParametersAST
}

trait MetaParametersResolver extends NameResolver {
  def metaParameters: List[MetaParameter]
  def parent: NameResolver

  def typeVariable (name: String) = {
    if (typeVariables.contains(name)) Some(JTypeVariableSignature(name))
    else parent.typeVariable(name)
  }

  def metaVariable (tn: TypeName): Try[JTypeArgument] = tn match {
    case TypeName(QualifiedName(name :: Nil), Nil, 0) if metaVariables.contains(name) => Success(PureVariable(name))
    case _ => parent.metaVariable(tn)
  }

  def root = parent.root

  private lazy val typeVariables = metaParameters.collect {
    case TypeParameter(name, _) => name
    case MetaValueParameter(name, t) if isTypeType(t) => name
  }.toSet

  private lazy val metaVariables = metaParameters.collect {
    case MetaValueParameter(name, t) if ! isTypeType(t) => name
  }.toSet

  private def isTypeType (t: TypeName): Boolean = parent.typeSignature(t).toOption.contains(JTypeSignature.typeTypeSig)
}
