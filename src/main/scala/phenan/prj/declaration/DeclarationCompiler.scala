package phenan.prj.declaration

import java.io.Reader

import phenan.prj.JCompiler
import phenan.prj.ir.{NameResolver, IRFile}
import phenan.prj.state.JState

import scala.collection.immutable.PagedSeq
import scala.util.Try

class DeclarationCompiler (compiler: JCompiler) (implicit state: JState) {
  import DeclarationParsers._

  def compile (file: String): Try[IRFile] = compile(PagedSeq.fromFile(file), file)
  def compile (reader: Reader, file: String): Try[IRFile] = compile(PagedSeq.fromReader(reader), file)
  def compile (seq: PagedSeq[Char], file: String): Try[IRFile] = tryParse(compilationUnit, seq, file).map(cu => new IRFile(cu, root))

  val root = NameResolver.root(compiler)
}
