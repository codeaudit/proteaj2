package phenan.prj.combinator

import scala.reflect.ClassTag
import scala.util.parsing.combinator.PackratParsers
import scala.util.parsing.input.{Positional, Position, Reader}

import scala.language.implicitConversions

trait TwoLevelParsers {
  self =>

  type Elem

  def delimiter: LParser[Any]

  type Input = Reader[Elem]
  type ParseResult[T] = Impl.ParseResult[T]
  type ~ [+A, +B] = Impl.~[A, B]

  val ~ = Impl.~
  val ParseSuccess = Impl.Success
  val ParseFailure = Impl.NoSuccess

  def success [T] (t: => T): LParser[T] = Impl.LParserImpl(Impl.success(t))
  def accept [T] (kind: String, f: PartialFunction[Elem, T]): LParser[T] = Impl.LParserImpl(Impl.accept(kind, f))
  def elem (e: Elem): LParser[Elem] = Impl.LParserImpl(Impl.elem(e))
  def elem (kind: String, f: Elem => Boolean): LParser[Elem] = Impl.LParserImpl(Impl.elem(kind, f))
  def elem [E <: Elem] (implicit Tag: ClassTag[E]): LParser[E] = Impl.LParserImpl(Impl.accept(Tag.toString(), { case Tag(x) => x }))

  // workaround for avoiding infinite loop of lazy initialization
  def ref [T] (parser: => HParser[T]): HParser[T] = new Impl.HParserRef[T](parser)

  def positioned [T <: Positional] (parser: HParser[T]): HParser[T] = Impl.HParserImpl(Impl.positioned(parser.parser))

  trait HParser[+T] {
    def apply (in: Input): ParseResult[T]

    def ~ [U] (parser: => HParser[U]): HParser[T ~ U]
    def ~> [U] (parser: => HParser[U]): HParser[U]
    def <~ [U] (parser: => HParser[U]): HParser[T]

    def | [U >: T] (parser: => HParser[U]): HParser[U]
    def ||| [U >: T] (parser: => HParser[U]): HParser[U]

    def ? : HParser[Option[T]]
    def & : HParser[T]
    def ! : HParser[Unit]
    def ! (f: T => Boolean): HParser[T]
    def * : HParser[List[T]]
    def + : HParser[List[T]]

    def * [U] (sep: => HParser[U]): HParser[List[T]]
    def + [U] (sep: => HParser[U]): HParser[List[T]]

    def *! (f: List[T] => Boolean): HParser[List[T]]

    def map [R] (f: T => R): HParser[R]
    def flatMap [R] (f: T => HParser[R]): HParser[R]

    def into [R] (f: T => HParser[R]): HParser[R] = flatMap(f)

    def ^? [R] (f: PartialFunction[T, R]): HParser[R]
    def ^^ [R] (f: T => R): HParser[R] = map(f)
    def ^^? [R] (f: T => Option[R]): HParser[R]
    def ^^^ [R] (f: => R): HParser[R]
    def >> [R] (f: T => HParser[R]): HParser[R] = flatMap(f)

    def log (s: String): HParser[T]

    protected [TwoLevelParsers] def parser: Impl.PackratParser[T]
  }

  trait LParser[+T] {
    def ^ : HParser[T]

    def ~ [U] (parser: => LParser[U]): LParser[T ~ U]
    def ~> [U] (parser: => LParser[U]): LParser[U]
    def <~ [U] (parser: => LParser[U]): LParser[T]

    def | [U >: T] (parser: => LParser[U]): LParser[U]

    def ? : LParser[Option[T]]
    def ! : LParser[Unit]
    def * : LParser[List[T]]
    def + : LParser[List[T]]

    def * [U] (sep: => LParser[U]): LParser[List[T]]
    def + [U] (sep: => LParser[U]): LParser[List[T]]

    def map [R] (f: T => R): LParser[R]
    def flatMap [R] (f: T => LParser[R]): LParser[R]

    def into [R] (f: T => LParser[R]): LParser[R] = flatMap(f)

    def ^^ [R] (f: T => R): LParser[R] = map(f)
    def >> [R] (f: T => LParser[R]): LParser[R] = flatMap(f)

    def log (s: String): LParser[T]

    protected [TwoLevelParsers] def parser: Impl.PackratParser[T]
  }

  object Impl extends PackratParsers {
    override type Elem = self.Elem

    trait HParserLike[T] extends HParser[T] {
      def apply (in: Input): ParseResult[T] = (delimiter.parser ~> parser <~ delimiter.parser).apply(new PackratReader[Elem](in))

      def ~ [U] (that: => HParser[U]): HParser[T ~ U] = HParserImpl((this.parser <~ delimiter.parser) ~ that.parser)
      def ~> [U] (that: => HParser[U]): HParser[U] = HParserImpl(this.parser ~> delimiter.parser ~> that.parser)
      def <~ [U] (that: => HParser[U]): HParser[T] = HParserImpl(this.parser <~ delimiter.parser <~ that.parser)

      def | [U >: T] (that: => HParser[U]): HParser[U] = HParserImpl(this.parser | that.parser)
      def ||| [U >: T] (that: => HParser[U]): HParser[U] = HParserImpl(this.parser ||| that.parser)

      def ? : HParser[Option[T]] = HParserImpl(parser.?)
      def & : HParser[T] = HParserImpl(guard(parser))
      def ! : HParser[Unit] = HParserImpl(not(parser))
      def ! (f: T => Boolean): HParser[T] = HParserImpl(parser ^? { case r if ! f(r) => r })
      def * : HParser[List[T]] = HParserImpl(repsep(parser, delimiter.parser))
      def + : HParser[List[T]] = HParserImpl(rep1sep(parser, delimiter.parser))
      def * [U] (sep: => HParser[U]): HParser[List[T]] = HParserImpl(repsep(parser, delimiter.parser ~> sep.parser ~> delimiter.parser))
      def + [U] (sep: => HParser[U]): HParser[List[T]] = HParserImpl(rep1sep(parser, delimiter.parser ~> sep.parser ~> delimiter.parser))

      def map [R] (f: T => R): HParser[R] = HParserImpl(parser.map(f))
      def flatMap [R] (f: T => HParser[R]): HParser[R] = HParserImpl(parser.flatMap(f(_).parser))

      def ^? [R] (f: PartialFunction[T, R]): HParser[R] = HParserImpl(parser ^? f)
      def ^^? [R] (f: T => Option[R]): HParser[R] = HParserImpl(parser ^^ f ^? { case Some(r) => r })
      def ^^^ [R] (f: => R): HParser[R] = HParserImpl(parser ^^^ f)

      def *! (f: List[T] => Boolean): HParser[List[T]] = HParserImpl(new Parser[List[T]] {
        def apply (in: Input): ParseResult[List[T]] = apply_rec(Nil, in)
        private def apply_rec (list: List[T], in: Input): ParseResult[List[T]] = parser(in) match {
          case Success(res, next) =>
            val rs = list :+ res
            if (f(rs)) Success(list, in)
            else apply_rec(rs, next)
          case fail => Success(list, in)
        }
      })

      def log (s: String): HParser[T] = HParserImpl(Impl.log(parser)(s))
    }

    case class HParserImpl[T] (parser: PackratParser[T]) extends HParserLike[T]

    class HParserRef[T] (hp: => HParser[T]) extends HParserLike[T] {
      lazy val parser: PackratParser[T] = hp.parser
    }

    case class LParserImpl[T] (parser: PackratParser[T]) extends LParser[T] {
      lazy val ^ : HParser[T] = HParserImpl(parser)

      def ~ [U] (that: => LParser[U]): LParser[T ~ U] = LParserImpl(this.parser ~ that.parser)
      def ~> [U] (that: => LParser[U]): LParser[U] = LParserImpl(this.parser ~> that.parser)
      def <~ [U] (that: => LParser[U]): LParser[T] = LParserImpl(this.parser <~ that.parser)

      def | [U >: T] (that: => LParser[U]): LParser[U] = LParserImpl(this.parser | that.parser)

      def ? : LParser[Option[T]] = LParserImpl(parser.?)
      def ! : LParser[Unit] = LParserImpl(not(parser))
      def * : LParser[List[T]] = LParserImpl(parser.*)
      def + : LParser[List[T]] = LParserImpl(parser.+)
      def * [U] (sep: => LParser[U]): LParser[List[T]] = LParserImpl(repsep(parser, sep.parser))
      def + [U] (sep: => LParser[U]): LParser[List[T]] = LParserImpl(rep1sep(parser, sep.parser))

      def map [R] (f: T => R): LParser[R] = LParserImpl(parser.map(f))
      def flatMap [R] (f: T => LParser[R]): LParser[R] = LParserImpl(parser.flatMap(f(_).parser))

      def log (s: String): LParser[T] = LParserImpl(Impl.log(parser)(s))
    }
  }
}