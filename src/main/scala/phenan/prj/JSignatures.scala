package phenan.prj

case class JClassSignature (metaParams: List[FormalMetaParameter], superClass: JClassTypeSignature, interfaces: List[JClassTypeSignature])

object JClassSignature {
  def apply (superClassName: Option[String], interfaceNames: List[String]): JClassSignature = {
    JClassSignature(Nil, superClassName.map(SimpleClassTypeSignature(_, Nil)).getOrElse(JTypeSignature.objectTypeSig), interfaceNames.map(SimpleClassTypeSignature(_, Nil)))
  }
}

case class JMethodSignature (metaParams: List[FormalMetaParameter], paramTypes: List[JTypeSignature], returnType: JTypeSignature, throwTypes: List[JTypeSignature],
                            activates: List[JTypeSignature], deactivates: List[JTypeSignature], requires: List[JTypeSignature]) {
  def throws (es: List[String]): JMethodSignature = {
    if (es.nonEmpty) JMethodSignature(metaParams, paramTypes, returnType, throwTypes ++ es.map(name => SimpleClassTypeSignature(name, Nil)), activates, deactivates, requires)
    else this
  }
}

case class FormalMetaParameter (name: String, metaType: JTypeSignature, bounds: List[JTypeSignature])

sealed trait JTypeSignature extends JTypeArgument

object JTypeSignature {
  lazy val typeTypeSig = SimpleClassTypeSignature("proteaj/lang/Type", Nil)
  lazy val objectTypeSig = SimpleClassTypeSignature("java/lang/Object", Nil)
}

sealed trait JClassTypeSignature extends JTypeSignature

case class SimpleClassTypeSignature (clazz: String, args: List[JTypeArgument]) extends JClassTypeSignature

case class MemberClassTypeSignature (outer: JClassTypeSignature, clazz: String, args: List[JTypeArgument]) extends JClassTypeSignature

sealed trait JPrimitiveTypeSignature extends JTypeSignature

case object ByteTypeSignature extends JPrimitiveTypeSignature
case object CharTypeSignature extends JPrimitiveTypeSignature
case object DoubleTypeSignature extends JPrimitiveTypeSignature
case object FloatTypeSignature extends JPrimitiveTypeSignature
case object IntTypeSignature extends JPrimitiveTypeSignature
case object LongTypeSignature extends JPrimitiveTypeSignature
case object ShortTypeSignature extends JPrimitiveTypeSignature
case object BoolTypeSignature extends JPrimitiveTypeSignature
case object VoidTypeSignature extends JPrimitiveTypeSignature

case class JArrayTypeSignature (component: JTypeSignature) extends JTypeSignature

case class JTypeVariableSignature (name: String) extends JTypeSignature

case class JCapturedWildcardSignature (upperBound: Option[JTypeSignature], lowerBound: Option[JTypeSignature]) extends JTypeSignature

sealed trait JTypeArgument

case class PureVariable (name: String) extends JTypeArgument

case class UpperBoundWildcardArgument (signature: JTypeSignature) extends JTypeArgument

case class LowerBoundWildcardArgument (signature: JTypeSignature) extends JTypeArgument

object UnboundWildcardArgument extends JTypeArgument
