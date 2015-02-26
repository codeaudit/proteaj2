package phenan.prj.ir

import phenan.prj._
import phenan.prj.decl._
import phenan.prj.exception.InvalidTypeException
import phenan.prj.internal.JClassLoader
import phenan.prj.state.JState

import scala.util._
import scalaz.Scalaz._
import scalaz.Memo._

class IRResolver (header: Header, val loader: JClassLoader)(implicit state: JState) {
  def packageName: Option[String] = header.pack.map(pack => pack.name.names.mkString("."))

  val packageInternalName: Option[String] = header.pack.map(pack => pack.name.names.mkString("/"))

  lazy val importedClasses: Map[String, JClass] = {
    header.imports.flatMap {
      case SingleClassImportDeclaration(name) => tryLoadClass(name.names) match {
        case Success(c) => Some(name.names.last -> c)
        case Failure(e) =>
          state.error("class not found : " + name.names.mkString("."))
          None
      }
      case _ => None
    }.toMap
  }

  val loadClass: List[String] => Try[JClass] = mutableHashMapMemo(loadClassWithoutCache)

  def loadClassWithoutCache (name: List[String]): Try[JClass] = {
    if (importedClasses.contains(name.head)) tryGetInnerClass(importedClasses(name.head), name.tail)
    else tryLoadClassFromPackage(name, packages)
  }

  def arrayOf (t: JErasedType): JArrayClass = loader.arrayOf(t)
  def objectClass = loader.objectClass

  def primitives = loader.primitives

  private[ir] def getClass (name: QualifiedName): Option[JClass] = loadClass(name.names) match {
    case Success(c) => Some(c)
    case Failure(e) =>
      state.error("class not found : " + name.names.mkString("."))
      None
  }

  private def tryLoadClass (name: List[String]): Try[JClass] = tryLoadClass(name.head, name.tail)

  private def tryLoadClass (name: String, rest: List[String]): Try[JClass] = loader.loadClass(name) match {
    case Success(c) if rest.nonEmpty => tryGetInnerClass(c, rest)
    case Failure(e) if rest.nonEmpty => tryLoadClass(name + '/' + rest.head, rest.tail)
    case Success(c) => Success(c)
    case Failure(e) => Failure(e)
  }

  private def tryGetInnerClass (clazz: JClass, name: List[String]): Try[JClass] = {
    if (name.isEmpty) Success(clazz)
    else if (clazz.innerClasses.contains(name.head)) tryGetInnerClass(clazz.innerClasses(name.head), name.tail)
    else Failure(InvalidTypeException("inner class " + name.head + " is not found"))
  }

  private def tryLoadClassFromPackage (name: List[String], packages: List[QualifiedName]): Try[JClass] = packages match {
    case pack :: rest => tryLoadClass(pack.names ++ name) match {
      case Success(clazz) => Success(clazz)
      case Failure(e) => tryLoadClassFromPackage(name, rest)
    }
    case Nil => tryLoadClass(name)
  }

  private val packages = header.pack match {
    case Some(pack) => pack.name :: header.imports.collect {
      case PackageImportDeclaration(name) => name
    }
    case None => header.imports.collect {
      case PackageImportDeclaration(name) => name
    }
  }
}

trait TypeNameResolver {
  protected def resolver: IRResolver
  def typeVariables: Map[String, IRTypeVariable]

  private[ir] def typeName (t: TypeName): Option[IRGenericType] = t match {
    case c : ClassTypeName => simpleTypeName(c)
    case a : ArrayTypeName => arrayTypeName(a)
  }

  private[ir] def simpleTypeName (c: ClassTypeName): Option[IRGenericType] = {
    if (c.args.isEmpty && c.name.names.size == 1 && resolver.primitives.contains(c.name.names.head)) resolver.primitives.get(c.name.names.head).map(IRGenericPrimitiveType)
    else classTypeName(c)
  }

  private[ir] def classTypeName (c: ClassTypeName): Option[IRGenericClassType] = for {
    clazz <- resolver.getClass(c.name)
    args  <- c.args.traverse(typeArgument)
  } yield IRGenericClassType(clazz, args)

  private[ir] def arrayTypeName (a: ArrayTypeName): Option[IRGenericArrayType] = typeName(a.component).map(IRGenericArrayType(_, resolver))

  private[ir] def typeArgument (arg: TypeArgument): Option[IRTypeArgument] = arg match {
    case t: TypeName                => typeName(t)
    case WildcardType(upper, lower) => Some(IRGenericWildcardType(upper.flatMap(typeName), lower.flatMap(typeName)))
  }
}