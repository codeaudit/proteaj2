package phenan.prj.internal

import org.scalatest._

import phenan.prj._
import phenan.prj.config.JConfig

import scala.util._

class JClassLoaderTest extends FunSuite with Matchers {
  val config = JConfig.default
  val loader = new JClassLoader(new JDeclarationCompiler(config), config)

  test ("String 型をロード") {
    val clazz = loader.load("java/lang/String")
    clazz shouldBe a [Success[_]]
    clazz.get shouldBe a [JLoadedClass]
  }

  test ("配列型をロード") {
    val o = loader.load("java/lang/Object")
    val os = loader.load("[Ljava/lang/Object;")
    val oss = loader.load("[[Ljava/lang/Object;")

    o shouldBe a [Success[_]]
    os shouldBe a [Success[_]]
    oss shouldBe a [Success[_]]
    
    loader.arrayOf(o.get) shouldBe os.get
    loader.arrayOf(os.get) shouldBe oss.get
  }
}
