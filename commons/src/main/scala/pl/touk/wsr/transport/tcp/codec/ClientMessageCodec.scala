package pl.touk.wsr.transport.tcp.codec

import akka.util.ByteStringBuilder
import pl.touk.wsr.protocol.ClientMessage
import pl.touk.wsr.protocol.srvrdr.{Ack, ReaderMessage, RequestForSequence}
import pl.touk.wsr.protocol.wrtsrv.{Greeting, NextNumber, WriterMessage}

object ClientMessageCodec extends CodecCommons {

  val encoder = new MessageEncoder[ClientMessage]({
    case wrt: WriterMessage =>
      encodeWriterMessage(wrt)
    case rdr: ReaderMessage =>
      encodeReaderMessage(rdr)
  })

  private def encodeWriterMessage(wrt: WriterMessage): ByteStringBuilder = {
    val builder = new ByteStringBuilder
    wrt match {
      case Greeting =>
        builder.putInt(0)
      case NextNumber(number) =>
        builder.putInt(number)
    }
  }

  private def encodeReaderMessage(rdr: ReaderMessage): ByteStringBuilder = {
    val builder = new ByteStringBuilder
    rdr match {
      case RequestForSequence(seqId) =>
        encodeUuid(builder, seqId)
        builder.putInt(0)
      case Ack(seqId) =>
        encodeUuid(builder, seqId)
        builder.putInt(1) // TODO: whithout this server can't recognize if got RequestForSequence or Ack without additional knowledge of msg sent from his site
    }
  }


  val writerExtractor = WaitingForGreetingWriterExtractor

  object WaitingForGreetingWriterExtractor extends SingleMessageExtractor[ClientMessage] {
    override def extractMessage(str: EnrichedByteString): Option[(ClientMessage, SingleMessageExtractor[ClientMessage], EnrichedByteString)] = {
      str.readInt().map {
        case (i, rest) =>
          if (i != 0)
            throw new IllegalArgumentException(s"Illegal greeting message: $i")
          (Greeting, WaitingForNextNumberWriterExtractor, rest)
      }
    }
  }

  object WaitingForNextNumberWriterExtractor extends SingleMessageExtractor[ClientMessage] {
    override def extractMessage(str: EnrichedByteString): Option[(ClientMessage, SingleMessageExtractor[ClientMessage], EnrichedByteString)] = {
      str.readInt().map {
        case (i, rest) =>
          (NextNumber(i), this, rest)
      }
    }
  }

  val readerExtractor = WaitingForRequestForSequence

  object WaitingForRequestForSequence extends SingleMessageExtractor[ClientMessage] {
    override def extractMessage(str: EnrichedByteString): Option[(ClientMessage, SingleMessageExtractor[ClientMessage], EnrichedByteString)] = {
      for {
        (seqId, afterSeqId) <- str.readUuid()
        (number, afterNumber) <- afterSeqId.readInt()
      } yield {
        val msg = number match {
          case 0 => RequestForSequence(seqId)
          case 1 => Ack(seqId)
        }
        (msg, this, afterNumber)
      }
    }
  }

}