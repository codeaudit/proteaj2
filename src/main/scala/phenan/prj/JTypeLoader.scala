package phenan.prj

trait JTypeLoader {
  def compiler: JCompiler

  val arrayOf: JType => JArrayType

  def getObjectType (clazz: JClass, args: List[MetaValue]): Option[JObjectType]

  lazy val objectType = compiler.classLoader.objectClass.flatMap(_.objectType(Nil))
  lazy val boolean = compiler.classLoader.boolean.primitiveType
  lazy val void = compiler.classLoader.void.primitiveType

  def iterableOf (arg: JRefType) = compiler.classLoader.iterableClass.flatMap(_.objectType(List(arg)))

  def fromTypeSignature (sig: JTypeSignature, env: Map[String, MetaValue]): Option[JType]
  def fromTypeSignature_RefType (sig: JTypeSignature, env: Map[String, MetaValue]): Option[JRefType]
  def fromClassTypeSignature (sig: JClassTypeSignature, env: Map[String, MetaValue]): Option[JObjectType]
  def fromPrimitiveSignature (p: JPrimitiveTypeSignature): JPrimitiveType

  lazy val superTypesOfArray: List[JObjectType] = CommonNames.superClassesOfArray.flatMap { name =>
    compiler.classLoader.loadClass_PE(name).flatMap(_.objectType(Nil))
  }

  def state = compiler.state
}
