package phenan.prj

case class JSyntax (priority: JPriority, pattern: List[JSyntaxElement])

sealed trait JSyntaxElement

case class JOperand (parameter: JParameter) extends JSyntaxElement
case class JOptionalOperand (parameter: JParameter) extends JSyntaxElement
case class JRepetition0 (parameter: JParameter) extends JSyntaxElement
case class JRepetition1 (parameter: JParameter) extends JSyntaxElement
case class JOperatorName (name: String) extends JSyntaxElement

case class JMetaOperand (name: String, parameter: JParameter) extends JSyntaxElement
case class JMetaName (mv: MetaValue) extends JSyntaxElement
case class JAndPredicate (parameter: JParameter) extends JSyntaxElement
case class JNotPredicate (parameter: JParameter) extends JSyntaxElement
