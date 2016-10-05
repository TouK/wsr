package pl.touk.wsr.protocol

import java.util.UUID

object srvrdr {

  sealed trait ReaderMessage extends ClientMessage

  sealed trait RServerMessage extends ServerMessage

  case class RequestForSequence(seqId: UUID) extends ReaderMessage

  case class NextNumberInSequence(seqId: UUID, number: Int) extends RServerMessage

  case class Ack(seqId: UUID) extends ReaderMessage

  case class EndOfSequence(seqId: UUID) extends RServerMessage

}