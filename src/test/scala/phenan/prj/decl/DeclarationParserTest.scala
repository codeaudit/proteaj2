package phenan.prj.decl

import java.io._

import org.scalatest._

import phenan.prj.state._

class DeclarationParserTest extends FunSuite with Matchers with ASTUtil {
  implicit val state  = JConfig().configure.get

  test ("空のプログラム") {
    val src = ""
    val ast = DeclarationParser(source(src)).get.parseAll

    ast shouldBe CompilationUnit(Header(None, Nil), Nil)
  }

  test ("パッケージ宣言") {
    val src = "package phenan.prj.decl;"
    val ast = DeclarationParser(source(src)).get.parseAll

    ast.header.pack shouldBe Some(packageDcl("phenan", "prj", "decl"))
    ast.header.imports shouldBe Nil
    ast.modules shouldBe Nil
  }

  test ("クラスインポート") {
    val src = "import java.io.Reader;"
    val ast = DeclarationParser(source(src)).get.parseAll

    ast.header.pack shouldBe None
    ast.header.imports shouldBe List(classImport("java", "io", "Reader"))
    ast.modules shouldBe Nil
  }

  test ("パッケージインポート") {
    val src = "import java.util.*;"
    val ast = DeclarationParser(source(src)).get.parseAll

    ast.header.pack shouldBe None
    ast.header.imports shouldBe List(packageImport("java", "util"))
    ast.modules shouldBe Nil
  }

  test ("静的メソッドインポート") {
    val src = "import static java.lang.Character.isDigit;"
    val ast = DeclarationParser(source(src)).get.parseAll

    ast.header.pack shouldBe None
    ast.header.imports shouldBe List(staticImport("java", "lang", "Character", "isDigit"))
    ast.modules shouldBe Nil
  }

  test ("全静的メンバインポート") {
    val src = "import static java.lang.Character.*;"
    val ast = DeclarationParser(source(src)).get.parseAll

    ast.header.pack shouldBe None
    ast.header.imports shouldBe List(staticImportAll("java", "lang", "Character"))
    ast.modules shouldBe Nil
  }

  test ("DSLインポート") {
    val src =
      """
        |import dsl phenan.Matrix;
        |import dsl phenan.SQL > phenan.File;
        |import dsl phenan.Regex < phenan.Grep < phenan.Test;
        |""".stripMargin
    val ast = DeclarationParser(source(src)).get.parseAll

    ast.header.pack shouldBe None
    ast.header.imports shouldBe List(
      dslImport("phenan", "Matrix"),
      dslImport("phenan", "SQL") > qualifiedName("phenan", "File"),
      dslImport("phenan", "Regex") < qualifiedName("phenan", "Grep") < qualifiedName("phenan", "Test"))
    ast.modules shouldBe Nil
  }

  test("正常終了したか") {
    state.clean()
    state.errors shouldBe 0
    state.warns shouldBe 0
  }


  def source (src: String): Reader = new StringReader(src)
}
