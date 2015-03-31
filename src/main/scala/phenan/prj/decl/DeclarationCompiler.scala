package phenan.prj.decl

import java.io._

import phenan.prj.JCompiler
import phenan.prj.ir.IRFile
import phenan.prj.state.JState

import scala.util.Try

class DeclarationCompiler (compiler: JCompiler) (implicit state: JState) {
  def compile (file: String): Try[IRFile] = compile(new FileReader(file), file)
  def compile (reader: Reader, fileName: String): Try[IRFile] = DeclarationParser(reader, fileName).map(_.parseAll).map(new IRFile(_, compiler))
}
