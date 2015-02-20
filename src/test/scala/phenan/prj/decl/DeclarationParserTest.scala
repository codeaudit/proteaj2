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

  test ("単純なクラス宣言") {
    val src = "public class Main {}"
    val ast = DeclarationParser(source(src)).get.parseAll

    ast.header.pack shouldBe None
    ast.header.imports shouldBe Nil
    ast.modules shouldBe List(ClassDeclaration(List(PublicModifier), "Main", Nil, None, Nil, Nil))
  }

  test ("マーカーアノテーション") {
    val src = "@Test public class Main {}"
    val ast = DeclarationParser(source(src)).get.parseAll

    ast.header.pack shouldBe None
    ast.header.imports shouldBe Nil
    ast.modules shouldBe List(ClassDeclaration(List(MarkerAnnotation(qualifiedName("Test")), PublicModifier), "Main", Nil, None, Nil, Nil))
  }

  test ("単一引数アノテーション") {
    val src = "public @SingleArg(Foo.class) class Main {}"
    val ast = DeclarationParser(source(src)).get.parseAll

    ast.header.pack shouldBe None
    ast.header.imports shouldBe Nil
    ast.modules shouldBe List(ClassDeclaration(List(PublicModifier, SingleElementAnnotation(qualifiedName("SingleArg"), expression("Foo.class", 1))), "Main", Nil, None, Nil, Nil))
  }

  test ("フルアノテーション") {
    val src =
      """@full.Annotation(numArgs = (1 + 1), value = {"foo", @Sub, "bar"})
        |public class Main {}""".stripMargin
    val ast = DeclarationParser(source(src)).get.parseAll

    val ann = FullAnnotation(qualifiedName("full", "Annotation"), Map("numArgs" -> expression(" (1 + 1)", 1), "value" -> arrayOf(expression("\"foo\"", 1), MarkerAnnotation(qualifiedName("Sub")), expression(" \"bar\"", 1))))

    ast.header.pack shouldBe None
    ast.header.imports shouldBe Nil
    ast.modules shouldBe List(ClassDeclaration(List(ann, PublicModifier), "Main", Nil, None, Nil, Nil))
  }

  test ("extends 節付きのクラス宣言") {
    val src = "public class Main extends java.awt.event.MouseAdapter {}"
    val ast = DeclarationParser(source(src)).get.parseAll

    ast.header.pack shouldBe None
    ast.header.imports shouldBe Nil
    ast.modules shouldBe List(ClassDeclaration(List(PublicModifier), "Main", Nil, Some(simpleType("java", "awt", "event", "MouseAdapter")), Nil, Nil))
  }

  test ("implements 節付きのクラス宣言") {
    val src = "public class Main implements Cloneable, Serializable {}"
    val ast = DeclarationParser(source(src)).get.parseAll

    ast.header.pack shouldBe None
    ast.header.imports shouldBe Nil
    ast.modules shouldBe List(ClassDeclaration(List(PublicModifier), "Main", Nil, None, List(simpleType("Cloneable"), simpleType("Serializable")), Nil))
  }

  test ("型引数付きのクラス宣言") {
    val src = "public class MyList <T> extends AbstractList<T> implements List<T> {}"
    val ast = DeclarationParser(source(src)).get.parseAll

    ast.header.pack shouldBe None
    ast.header.imports shouldBe Nil
    ast.modules shouldBe List(ClassDeclaration(List(PublicModifier), "MyList", List(TypeParameter("T", Nil)),
      Some(ClassTypeName(qualifiedName("AbstractList"), List(simpleType("T")))),
      List(ClassTypeName(qualifiedName("List"), List(simpleType("T")))), Nil))
  }

  test ("複雑な型引数") {
    val src = "public class Foo <S extends java.lang.Object, T extends Bar<?> & Baz<? extends S>> extends Fizz<S, ? super T> {}"
    val ast = DeclarationParser(source(src)).get.parseAll

    ast.header.pack shouldBe None
    ast.header.imports shouldBe Nil

    val typeParams = List(
      TypeParameter("S", List(simpleType("java", "lang", "Object"))),
      TypeParameter("T", List(ClassTypeName(qualifiedName("Bar"), List(WildcardType(None, None))), ClassTypeName(qualifiedName("Baz"), List(WildcardType(Some(simpleType("S")), None))))))
    val superType = Some(
      ClassTypeName(qualifiedName("Fizz"), List(simpleType("S"), WildcardType(None, Some(simpleType("T"))))))
    ast.modules shouldBe List(ClassDeclaration(List(PublicModifier), "Foo", typeParams, superType, Nil, Nil))
  }

  test("正常終了したか") {
    state.clean()
    state.errors shouldBe 0
    state.warns shouldBe 0
  }


  def source (src: String): Reader = new StringReader(src)
}
