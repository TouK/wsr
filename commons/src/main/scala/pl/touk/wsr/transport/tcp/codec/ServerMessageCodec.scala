package pl.touk.wsr.transport.tcp.codec

import akka.util.ByteStringBuilder
import pl.touk.wsr.protocol.{ClientMessage, ServerMessage}
import pl.touk.wsr.protocol.srvrdr._
import pl.touk.wsr.protocol.wrtsrv._

object ServerMessageCodec extends CodecCommons {

  val encoder = new MessageEncoder[ServerMessage]({
    case wrt: WServerMessage =>
      encodeWriterMessage(wrt)
    case rdr: RServerMessage =>
      encodeReaderMessage(rdr)
  })

  private def encodeWriterMessage(wrt: WServerMessage): ByteStringBuilder = {
    val builder = new ByteStringBuilder
    wrt match {
      case RequestForNumbers(start, count) =>
        builder.putInt(start)
        builder.putInt(count)
    }
  }

  private def encodeReaderMessage(rdr: RServerMessage): ByteStringBuilder = {
    val builder = new ByteStringBuilder
    rdr match {
      case NextNumberInSequence(seqId, number) =>
        encodeUuid(builder, seqId)
        builder.putInt(number)
      case EndOfSequence(seqId) =>
        encodeUuid(builder, seqId)
        builder.putInt(-1)
    }
  }

  val writerExtractor = WaitingForRequestForNumbersExtractor

  object WaitingForRequestForNumbersExtractor extends SingleMessageExtractor[ServerMessage] {
    override def extractMessage(str: EnrichedByteString): Option[(ServerMessage, SingleMessageExtractor[ServerMessage], EnrichedByteString)] = {
      for {
        (start, afterStart) <- str.readInt()
        (count, afterCount) <- afterStart.readInt()
      } yield (RequestForNumbers(start, count), this, afterCount)
    }
  }

  val readerExtractor = WaitingForNextNumberInSeqExtractor

  object WaitingForNextNumberInSeqExtractor extends SingleMessageExtractor[ServerMessage] {
    override def extractMessage(str: EnrichedByteString): Option[(ServerMessage, SingleMessageExtractor[ServerMessage], EnrichedByteString)] = {
      for {
        (seqId, afterSeqId) <- str.readUuid()
        (number, afterNumber) <- afterSeqId.readInt()
      } yield {
        val msg = if (number == -1)
          EndOfSequence(seqId)
        else
          NextNumberInSequence(seqId, number)
        (msg, this, afterNumber)
      }
    }
  }

}
