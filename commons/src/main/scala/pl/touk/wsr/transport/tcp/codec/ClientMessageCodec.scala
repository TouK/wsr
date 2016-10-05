package pl.touk.wsr.transport.tcp.codec

import java.nio.ByteOrder
import java.util.UUID

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
    }
  }


  val writerExtractor = WaitingForGreetingWriterExtractor

  object WaitingForGreetingWriterExtractor extends SingleMessageExtractor[WriterMessage] {
    override def extractMessage(str: EnrichedByteString): Option[(WriterMessage, SingleMessageExtractor[WriterMessage], EnrichedByteString)] = {
      str.readInt().map {
        case (i, rest) =>
          if (i != 0)
            throw new IllegalArgumentException(s"Illegal greeting message: $i")
          (Greeting, WaitingForNextNumberWriterExtractor, rest)
      }
    }
  }

  object WaitingForNextNumberWriterExtractor extends SingleMessageExtractor[WriterMessage] {
    override def extractMessage(str: EnrichedByteString): Option[(WriterMessage, SingleMessageExtractor[WriterMessage], EnrichedByteString)] = {
      str.readInt().map {
        case (i, rest) =>
          (NextNumber(i), this, rest)
      }
    }
  }

}