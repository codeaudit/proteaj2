package phenan.prj.ir

import phenan.prj._

import scalaz.syntax.traverse._
import scalaz.std.list._
import scalaz.std.option._

sealed trait IRExpression {
  def staticType: Option[JType]
  def activates: List[IRContextRef]
  def deactivates: List[IRContextRef]

  def withContexts (contexts: List[IRContextRef]): IRExpression = {
    if (contexts.isEmpty) this
    else IRContextualArgument(this, contexts)
  }
}

sealed trait IRLeftHandSide extends IRExpression

sealed trait IRAssignmentExpression extends IRExpression {
  def left: IRLeftHandSide
  def right: IRExpression
  def staticType: Option[JType] = right.staticType
  def activates: List[IRContextRef] = Nil
  def deactivates: List[IRContextRef] = Nil
}

case class IRSimpleAssignmentExpression (left: IRLeftHandSide, right: IRExpression) extends IRAssignmentExpression

case class IRNewExpression (metaArgs: Map[String, MetaArgument], constructor: JConstructor, args: List[IRExpression], requiredContexts: List[IRContextRef]) extends IRExpression {
  def staticType = Some(constructor.declaring)
  lazy val activates: List[IRContextRef] = constructor.activates.traverse(_.bind(metaArgs).collect { case obj: JObjectType => IRContextRef(obj) }).getOrElse {
    constructor.compiler.state.errorAndReturn("invalid context type", Nil)
  }
  lazy val deactivates: List[IRContextRef] = constructor.deactivates.traverse(_.bind(metaArgs).collect { case obj: JObjectType => IRContextRef(obj) }).getOrElse {
    constructor.compiler.state.errorAndReturn("invalid context type", Nil)
  }
  lazy val throws: List[JType] = constructor.exceptionTypes.flatMap(_.bind(metaArgs))
}

case class IRAnonymousClass (metaArgs: Map[String, MetaArgument], baseType: JObjectType, args: List[IRExpression], requiredContexts: List[IRContextRef], members: List[IRClassMember])

sealed trait IRExplicitConstructorCall

case class IRThisConstructorCall (metaArgs: Map[String, MetaArgument], constructor: JConstructor, args: List[IRExpression], requiredContexts: List[IRContextRef]) extends IRExplicitConstructorCall
case class IRSuperConstructorCall (metaArgs: Map[String, MetaArgument], constructor: JConstructor, args: List[IRExpression], requiredContexts: List[IRContextRef]) extends IRExplicitConstructorCall

sealed trait IRArrayCreation extends IRExpression

case class IRNewArray (componentType: JType, length: List[IRExpression], dim: Int) extends IRArrayCreation {
  def staticType = Some(componentType.array(length.size + dim))
  def activates: List[IRContextRef] = Nil
  def deactivates: List[IRContextRef] = Nil
}

case class IRArrayInitializer (componentType: JType, dim: Int, components: List[IRExpression]) extends IRArrayCreation {
  def staticType = Some(componentType.array(dim))
  def activates: List[IRContextRef] = Nil
  def deactivates: List[IRContextRef] = Nil
}

case class IRCastExpression (destType: JType, expression: IRExpression) extends IRExpression {
  def staticType = Some(destType)
  def activates: List[IRContextRef] = expression.activates
  def deactivates: List[IRContextRef] = expression.deactivates
}

case class IRArrayAccess (array: IRExpression, index: IRExpression) extends IRLeftHandSide {
  def staticType: Option[JType] = array.staticType match {
    case Some(JArrayType(component)) => Some(component)
    case _ => None
  }
  def activates: List[IRContextRef] = Nil
  def deactivates: List[IRContextRef] = Nil
}

sealed trait IRFieldAccess extends IRLeftHandSide {
  def field: JField
  def staticType = Some(field.fieldType)
  def activates: List[IRContextRef] = Nil
  def deactivates: List[IRContextRef] = Nil
}

case class IRInstanceFieldAccess (instance: IRExpression, field: JField) extends IRFieldAccess {
  override def toString: String = instance.toString + '.' + field.name
}

case class IRSuperFieldAccess (thisType: JObjectType, field: JField) extends IRFieldAccess

case class IRStaticFieldAccess (field: JField) extends IRFieldAccess

sealed trait IRMethodCall extends IRExpression {
  def metaArgs: Map[String, MetaArgument]
  def method: JMethod
  def args: List[IRExpression]
  def requiredContexts: List[IRContextRef]
  lazy val staticType: Option[JType] = method.returnType.bind(metaArgs)

  lazy val activates: List[IRContextRef] = method.activates.traverse(_.bind(metaArgs).collect { case obj: JObjectType => IRContextRef(obj) }).getOrElse {
    method.compiler.state.errorAndReturn("invalid context type", Nil)
  }
  lazy val deactivates: List[IRContextRef] = method.deactivates.traverse(_.bind(metaArgs).collect { case obj: JObjectType => IRContextRef(obj) }).getOrElse {
    method.compiler.state.errorAndReturn("invalid context type", Nil)
  }
  lazy val throws: List[JType] = method.exceptionTypes.flatMap(_.bind(metaArgs))
}

case class IRInstanceMethodCall (instance: IRExpression, metaArgs: Map[String, MetaArgument], method: JMethod, args: List[IRExpression], requiredContexts: List[IRContextRef]) extends IRMethodCall

case class IRSuperMethodCall (thisType: JObjectType, metaArgs: Map[String, MetaArgument], method: JMethod, args: List[IRExpression], requiredContexts: List[IRContextRef]) extends IRMethodCall

case class IRStaticMethodCall (metaArgs: Map[String, MetaArgument], method: JMethod, args: List[IRExpression], requiredContexts: List[IRContextRef]) extends IRMethodCall

case class IRDSLOperation (method: JMethod, metaArgs: Map[String, MetaArgument], args: List[IRExpression], requiredContexts: List[IRContextRef]) extends IRMethodCall {
  override def toString: String = "#DSLOperation#" + method.name + args.mkString("(", ",", ")")
}

case class IRContextOperation (context: IRContextRef, method: JMethod, metaArgs: Map[String, MetaArgument], args: List[IRExpression], requiredContexts: List[IRContextRef]) extends IRMethodCall {
  override def toString: String = "#ContextOperation#" + context.contextType.name + '.' + method.name + args.mkString("(", ",", ")")
}

case class IRDefaultArgument (defaultMethod: JMethod) extends IRExpression {
  def staticType: Option[JType] = defaultMethod.returnType.bind(Map.empty)
  def activates: List[IRContextRef] = Nil
  def deactivates: List[IRContextRef] = Nil
}

case class IRContextualArgument (argument: IRExpression, contexts: List[IRContextRef]) extends IRExpression {
  def staticType: Option[JType] = argument.staticType
  def activates: List[IRContextRef] = Nil
  def deactivates: List[IRContextRef] = Nil
}

case class IRVariableArguments (args: List[IRExpression], componentType: Option[JType]) extends IRExpression {
  def staticType: Option[JType] = componentType.map(_.array)
  def activates: List[IRContextRef] = args.flatMap(_.activates)
  def deactivates: List[IRContextRef] = args.flatMap(_.deactivates)
}

sealed trait IRJavaLiteral extends IRExpression with IRAnnotationElement {
  def activates: List[IRContextRef] = Nil
  def deactivates: List[IRContextRef] = Nil
}

sealed trait IRClassLiteral extends IRJavaLiteral

case class IRObjectClassLiteral (clazz: JClass, dim: Int) extends IRClassLiteral {
  def staticType: Option[JObjectType] = clazz.objectType(Nil).map(_.array(dim)).flatMap(clazz.compiler.typeLoader.classTypeOf)
}

case class IRPrimitiveClassLiteral (primitiveClass: JPrimitiveType, dim: Int) extends IRClassLiteral {
  def staticType: Option[JObjectType] = primitiveClass.boxed.map(_.array(dim)).flatMap(primitiveClass.compiler.typeLoader.classTypeOf)
}

case class IRCharLiteral (value: Char, compiler: JCompiler) extends IRJavaLiteral {
  def staticType = Some(compiler.typeLoader.char)
}

case class IRIntLiteral (value: Int, compiler: JCompiler) extends IRJavaLiteral {
  def staticType = Some(compiler.typeLoader.int)
}

case class IRLongLiteral (value: Long, compiler: JCompiler) extends IRJavaLiteral {
  def staticType = Some(compiler.typeLoader.long)
}

case class IRBooleanLiteral (value: Boolean, compiler: JCompiler) extends IRJavaLiteral {
  def staticType = Some(compiler.typeLoader.boolean)
}

case class IRStringLiteral (value: String, compiler: JCompiler) extends IRJavaLiteral {
  def staticType: Option[JObjectType] = compiler.typeLoader.stringType
}

case class IRNullLiteral (expected: JType) extends IRJavaLiteral {
  def staticType = Some(expected)
}

case class IRThisRef (thisType: JObjectType) extends IRExpression {
  def staticType = Some(thisType)
  def activates: List[IRContextRef] = Nil
  def deactivates: List[IRContextRef] = Nil

  override def toString: String = thisType.name + '.' + "this"
}

case class IRLocalVariableRef (localType: JType, name: String) extends IRLeftHandSide {
  def staticType = Some(localType)
  def activates: List[IRContextRef] = Nil
  def deactivates: List[IRContextRef] = Nil
}

case class IRContextRef (contextType: JObjectType) extends IRExpression {
  def staticType = Some(contextType)
  def activates: List[IRContextRef] = Nil
  def deactivates: List[IRContextRef] = Nil
}

sealed trait IRAnnotationElement

case class IRAnnotation (annotationClass: JClass, args: Map[String, IRAnnotationElement]) extends IRAnnotationElement

case class IRAnnotationElementArray (array: List[IRAnnotationElement]) extends IRAnnotationElement

case class IREnumConstantRef (field: JFieldDef) extends IRAnnotationElement

case class IRStatementExpression (stmt: IRStatement, contexts: List[IRContextRef], voidType: JType) extends IRExpression {
  def staticType: Option[JType] = Some(voidType)
  def activates: List[IRContextRef] = Nil
  def deactivates: List[IRContextRef] = Nil
}
