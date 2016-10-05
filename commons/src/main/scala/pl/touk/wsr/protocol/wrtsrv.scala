package pl.touk.wsr.protocol

object wrtsrv {

  sealed trait WriterMessage extends ClientMessage

  sealed trait WServerMessage extends ServerMessage

  case object Greeting extends WriterMessage

  case class RequestForNumbers(start: Int, count: Int) extends WServerMessage

  case class NextNumber(number: Int) extends WriterMessage

}