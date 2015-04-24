package phenan.prj.decl

trait ASTUtil {
  def packageDcl (names: String*): PackageDeclaration = PackageDeclaration(qualifiedName(names:_*))
  def classImport (names: String*): SingleClassImportDeclaration = SingleClassImportDeclaration(qualifiedName(names:_*))
  def packageImport (names: String*): PackageImportDeclaration = PackageImportDeclaration(qualifiedName(names:_*))
  def staticImport (names: String*): ImportStaticMemberDeclaration = ImportStaticMemberDeclaration(qualifiedName(names:_*))
  def staticImportAll (names: String*): ImportStaticStarDeclaration = ImportStaticStarDeclaration(qualifiedName(names:_*))
  def dslImport (names: String*): DSLImportDeclaration = DSLImportDeclaration(qualifiedName(names:_*), None)

  def simpleType (names: String*): ClassTypeName = ClassTypeName(qualifiedName(names:_*), Nil)
  
  def arrayOf (components: AnnotationElement*): ArrayOfAnnotationElement = ArrayOfAnnotationElement(components.toList)
  def expression (src: String, line: Int): ExpressionSnippet = ExpressionSnippet(Snippet(src, line))
  def block (src: String, line: Int): BlockSnippet = BlockSnippet(Snippet(src, line))
  def qualifiedName (names: String*): QualifiedName = QualifiedName(names.toList)

  implicit class DSLImportDclOps (d : DSLImportDeclaration) {
    def < (names: QualifiedName): DSLImportDeclaration = DSLImportDeclaration(d.name, d.precedence match {
      case Some(AscendingDSLPrecedence(p)) => Some(AscendingDSLPrecedence(p :+ names))
      case None => Some(AscendingDSLPrecedence(List(names)))
      case _    => throw new Exception("bad ast")
    })
    def > (names: QualifiedName): DSLImportDeclaration = DSLImportDeclaration(d.name, d.precedence match {
      case Some(DescendingDSLPrecedence(p)) => Some(DescendingDSLPrecedence(p :+ names))
      case None => Some(DescendingDSLPrecedence(List(names)))
      case _    => throw new Exception("bad ast")
    })
  }
}
