package phenan.prj.internal

import phenan.prj.state.JState

import scalaz._

class AnnotationReader (classFile: BClassFile)(implicit state: JState) {
  import classFile.poolReader._

  def readClassAnnotations (attribute: RuntimeVisibleAnnotationsAttribute): PrjClassAnnotations = classAnnotations(attribute)
  def readMethodAnnotations (attribute: RuntimeVisibleAnnotationsAttribute): PrjMethodAnnotations = methodAnnotations(attribute)

  private lazy val classAnnotations = annotations >=> {
    for {
      s <- classSig
      d <- dsl
      p <- pure
      c <- context
    } yield PrjClassAnnotations(s, d, p, c)
  }

  private lazy val methodAnnotations = annotations >=> {
    for {
      sig <- methodSig
      op  <- operator
      pr  <- pure
      fnl <- finalizer
    } yield PrjMethodAnnotations(sig, op, pr, fnl)
  }

  private lazy val classSig = annotation("ClassSig") {
    for {
      genParams  <- array("genericParameters")(genericParameter)
      supType    <- element("superType")(classTypeSignature)(SimpleClassTypeSignature("java/lang/Object", Nil))
      interfaces <- array("interfaces")(classTypeSignature)
    } yield PrjClassSignature(genParams, supType, interfaces)
  }

  private lazy val methodSig = annotation("MethodSig") {
    for {
      genParams   <- array("genericParameters")(genericParameter)
      retType     <- required("returnType")(typeSignature)(VoidTypeSignature)
      paramTypes  <- array("parameterTypes")(typeSignature)
      exceptions  <- array("throwsTypes")(typeSignature)
      activates   <- array("activates")(typeSignature)
      deactivates <- array("deactivates")(typeSignature)
      requires    <- array("requires")(typeSignature)
    } yield PrjMethodSignature(genParams, retType, paramTypes, exceptions, activates, deactivates, requires)
  }

  private lazy val dsl = annotation("DSL") {
    for {
      priorities <- array("priorities")(string)
      withDSLs   <- array("with")(descriptor)
    } yield PrjDSLAnnotation(priorities, withDSLs)
  }

  private lazy val operator = annotation ("Operator") {
    enumSwitch ("level", "OpLevel") {
      case "Statement"  => statementOperator
      case "Literal"    => literalOperator
      case "Expression" => expressionOperator
    } (expressionOperator)
  }

  private def operatorAnnotation (f: (PrjOperatorAssociation, List[PrjOperatorElement]) => PrjOperatorAnnotation): Reader[Map[String, BAnnotationElement], PrjOperatorAnnotation] = for {
    assoc <- association
    pat <- pattern
  } yield f(assoc, pat)

  private lazy val statementOperator = operatorAnnotation(PrjStatementOperator)

  private lazy val expressionOperator = operatorAnnotation(PrjExpressionOperator)

  private lazy val literalOperator = operatorAnnotation(PrjLiteralOperator)

  private lazy val pure = marker("Pure")

  private lazy val context = marker("Context")

  private lazy val finalizer = marker("Finalizer")

  private lazy val genericParameter = elementAnnotation("GenericParameter") {
    for {
      name      <- required("name")(string)("")
      paramType <- element("parameterType")(typeSignature)(SimpleClassTypeSignature("proteaj/lang/Type", Nil))
      bounds    <- array("bounds")(typeSignature)
    } yield PrjGenericParameter(name, paramType, bounds)
  }

  private lazy val association = enumSwitch [PrjOperatorAssociation] ("assoc", "Association") {
    case "LEFT"  => priority >==> PrjLeftAssociation
    case "RIGHT" => priority >==> PrjRightAssociation
    case "NON"   => priority >==> PrjNonAssociation
  } { priority >==> PrjNonAssociation }

  private lazy val priority = element("priority")(string)("")

  private lazy val pattern = array("pattern")(elementAnnotation("OpElem")(operatorElement))

  private lazy val operatorElement = enumSwitch [PrjOperatorElement] ("kind", "OpElemType") {
    case "Name" => required("name")(string)("") >==> PrjOperatorName
    case "Hole" => elementReader >==> { _ => PrjOperatorHole }
    case "Star" => elementReader >==> { _ => PrjOperatorRepStarHole }
    case "Plus" => elementReader >==> { _ => PrjOperatorRepPlusHole }
    case "Optional" => elementReader >==> { _ => PrjOperatorOptionalHole }
    case "AndPredicate" => required("name")(typeSignature)(VoidTypeSignature) >==> PrjOperatorAndPredicate
    case "NotPredicate" => required("name")(typeSignature)(VoidTypeSignature) >==> PrjOperatorNotPredicate
    case "Reference" => required("name")(string)("") >==> PrjOperatorPureValueRef
  } {
    state.error("invalid operator element type")
    elementReader >==> { _ => PrjOperatorHole }
  }

  private lazy val annotations: Reader[RuntimeVisibleAnnotationsAttribute, Map[String, Map[String, BAnnotationElement]]] = Reader {
    _.annotations.map(ann => readUTF(ann.annotationType) -> ann.values.map { case (k, v) => readUTF(k) -> v }.toMap).toMap
  }

  private lazy val annotationReader = Reader(identity[Map[String, Map[String, BAnnotationElement]]])

  private def marker (name: String): Reader[Map[String, Map[String, BAnnotationElement]], Boolean] = {
    annotationReader.map(_.get("Lproteaj/lang/" + name + ";")) >==> { _.nonEmpty }
  }

  private def annotation [T] (name: String)(reader: Reader[Map[String, BAnnotationElement], T]): Reader[Map[String, Map[String, BAnnotationElement]], Option[T]] = {
    annotationReader.map(_.get("Lproteaj/lang/" + name + ";")) >==> { _.map(reader =<< _) }
  }

  private lazy val elementReader = Reader(identity[Map[String, BAnnotationElement]])

  private def required [T] (name: String)(reader: Reader[BAnnotationElement, Option[T]])(default: => T) = element(name)(reader) {
    state.error("invalid generic parameter annotation : name field does not exist")
    default
  }

  private def element [T] (name: String)(reader: Reader[BAnnotationElement, Option[T]])(default: => T): Reader[Map[String, BAnnotationElement], T] = {
    elementReader.map(_.get(name)) >==> { _.flatMap(reader =<< _).getOrElse(default) }
  }

  private def enumSwitch [T] (name: String, enumType: String)(readers: PartialFunction[String, Reader[Map[String, BAnnotationElement], T]])(default: => Reader[Map[String, BAnnotationElement], T]): Reader[Map[String, BAnnotationElement], T] = {
    elementReader.map(_.get(name)).flatMap {
      case Some(BAnnotationElement_Enum(e, c)) if readUTF(e) == "Lproteaj/lang/" + enumType + ";" => readers.applyOrElse(readUTF(c), { _: String => default })
      case _ => default
    }
  }

  private def array [T] (name: String)(reader: Reader[BAnnotationElement, Option[T]]): Reader[Map[String, BAnnotationElement], List[T]] = {
    elementReader.map(_.get(name)) >==> { _.map(array(reader) =<< _).getOrElse(Nil) }
  }

  private def array [T] (reader: Reader[BAnnotationElement, Option[T]]): Reader[BAnnotationElement, List[T]] = Reader {
    case BAnnotationElement_Array(array) => array.flatMap(reader =<< _)
    case e =>
      state.error("invalid annotation element : expected array, but found " + e)
      Nil
  }

  private def elementAnnotation [T] (name: String)(reader: Reader[Map[String, BAnnotationElement], T]): Reader[BAnnotationElement, Option[T]] = Reader {
    case BAnnotationElement_Annotation(ann) if readUTF(ann.annotationType) == "Lproteaj/lang/" + name + ";" =>
      Some(reader =<< ann.values.map { case (k, v) => readUTF(k) -> v }.toMap)
    case e =>
      state.error("invalid annotation element : expected annotation, but found " + e)
      None
  }

  private lazy val string: Reader[BAnnotationElement, Option[String]] = Reader {
    case BAnnotationElement_String(str) => Some(readUTF(str))
    case e =>
      state.error("invalid annotation element : expected string, but found " + e)
      None
  }

  private lazy val descriptor: Reader[BAnnotationElement, Option[String]] = Reader {
    case BAnnotationElement_Class(ref) => Some(readUTF(ref))
    case e =>
      state.error("invalid annotation element : expected class, but found " + e)
      None
  }

  private lazy val typeSignature: Reader[BAnnotationElement, Option[TypeSignature]] = string.map(_.flatMap(SignatureParsers.parseTypeSignature))

  private lazy val classTypeSignature: Reader[BAnnotationElement, Option[ClassTypeSignature]] = string.map(_.flatMap(SignatureParsers.parseClassTypeSignature))

}
