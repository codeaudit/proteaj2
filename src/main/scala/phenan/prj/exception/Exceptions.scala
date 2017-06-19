package phenan.prj.exception

case class ClassFileNotFoundException (msg: String) extends Exception(msg)

case class InvalidClassFileException (msg: String) extends Exception(msg)

case class InvalidTypeException (msg: String) extends Exception(msg)

case class ParseException (msg: String) extends Exception(msg)

case class InvalidASTException (msg: String) extends Exception(msg)

case class InitializationFailedException (cause: Throwable) extends Exception(cause)
