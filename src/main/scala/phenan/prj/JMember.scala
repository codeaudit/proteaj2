package phenan.prj

trait JMember {
  def modifier: JModifier
  def declaring: JModule

  def isPrivate: Boolean = modifier.check(JModifier.accPrivate)
}

class JField (fieldDef: JFieldDef, val fieldType: JType, val declaring: JModule) extends JMember {
  def modifier: JModifier = fieldDef.mod
  def name: String = fieldDef.name
}

class JMethod (val methodDef: JMethodDef, val env: Map[String, MetaValue], val declaring: JModule, val clazz: JClass) extends JMember {
  def modifier: JModifier = methodDef.mod
  def name: String = methodDef.name

  def erasedReturnType: JErasedType = compiler.classLoader.erase_Force(methodDef.signature.returnType, metaParams)
  def erasedParameterTypes: List[JErasedType] = methodDef.signature.paramTypes.map(compiler.classLoader.erase_Force(_, metaParams))

  def returnType: JGenericType = ???
  def parameterTypes: List[JGenericType] = ???
  def exceptionTypes: List[JGenericType] = ???

  def overrides (that: JMethod): Boolean = {
    this.name == that.name && this.erasedReturnType.isSubclassOf(that.erasedReturnType) && this.erasedParameterTypes == that.erasedParameterTypes
  }

  def compiler = declaring.compiler

  private lazy val metaParams = methodDef.signature.metaParams ++ clazz.signature.metaParams
}

class JConstructor (val methodDef: JMethodDef, val enclosingEnv: Map[String, MetaValue], val declaring: JObjectType) extends JMember {
  def modifier: JModifier = methodDef.mod
}