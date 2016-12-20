package phenan.prj.ir

import java.io.StringReader

import org.scalatest._

import phenan.prj.JCompiler
import phenan.prj.declaration.{QualifiedName, TypeName}
import phenan.prj.state.JConfig

import scala.util._

class NameResolverTest extends FunSuite with Matchers {
  test ("完全修飾名によるロード") {
    val compiler = new JCompiler(JConfig().configure.get)
    val resolver: NameResolver = NameResolver.root(compiler)

    resolver.resolve(List("java", "util", "ArrayList")) shouldBe a [Success[_]]
    resolver.resolve(List("java", "util", "ArrayList")).get shouldBe compiler.classLoader.load("java/util/ArrayList").get

    resolver.resolve(List("java", "util", "Map", "Entry")) shouldBe a [Success[_]]
    resolver.resolve(List("java", "util", "Map", "Entry")).get shouldBe compiler.classLoader.load(s"java/util/Map$$Entry").get
  }

  test ("短縮名称によるロード") {
    val compiler = new JCompiler(JConfig().configure.get)
    val resolver: NameResolver = NameResolver.root(compiler)

    resolver.resolve(List("System")) shouldBe a [Success[_]]
    resolver.resolve(List("System")).get shouldBe compiler.classLoader.load("java/lang/System").get
  }

  test ("パッケージ、インポート") {
    val program =
      """package java.awt;
        |import java.io.File;
        |import java.util.*;
      """.stripMargin

    val compiler = new JCompiler(JConfig().configure.get)
    val file = compiler.declarationCompiler.compile(new StringReader(program), "testsrc.java")

    file shouldBe a [Success[_]]
    val resolver = file.get.resolver

    resolver.resolve(List("Color")) shouldBe a [Success[_]]
    resolver.resolve(List("Color")).get shouldBe compiler.classLoader.load("java/awt/Color").get

    resolver.resolve(List("List")) shouldBe a [Success[_]]
    resolver.resolve(List("List")).get shouldBe compiler.classLoader.load("java/awt/List").get

    resolver.resolve(List("File")) shouldBe a [Success[_]]
    resolver.resolve(List("File")).get shouldBe compiler.classLoader.load("java/io/File").get

    resolver.resolve(List("Reader")) shouldBe a [Failure[_]]

    resolver.resolve(List("ArrayList")) shouldBe a [Success[_]]
    resolver.resolve(List("ArrayList")).get shouldBe compiler.classLoader.load("java/util/ArrayList").get

    resolver.resolve(List("Map", "Entry")) shouldBe a [Success[_]]
    resolver.resolve(List("Map", "Entry")).get shouldBe compiler.classLoader.load(s"java/util/Map$$Entry").get
  }

  test ("クラス内") {
    val program =
      """package test.pack;
        |import java.util.*;
        |class Foo <T, U> {}
      """.stripMargin

    val compiler = new JCompiler(JConfig().configure.get)
    val file = compiler.declarationCompiler.compile(new StringReader(program), "testsrc.java")

    file shouldBe a [Success[_]]
    file.get.modules should have size 1

    val foo = file.get.modules.head
    val resolver = foo.resolver

    resolver.typeSignature(TypeName(QualifiedName(List("T")), Nil, 0)) shouldBe a [Success[_]]
    resolver.typeSignature(TypeName(QualifiedName(List("S")), Nil, 0)) shouldBe a [Failure[_]]
    resolver.typeSignature(TypeName(QualifiedName(List("List")), List(TypeName(QualifiedName(List("T")), Nil, 0)), 0)) shouldBe a [Success[_]]
  }
}
