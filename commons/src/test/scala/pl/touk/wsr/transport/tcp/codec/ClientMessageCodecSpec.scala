package pl.touk.wsr.transport.tcp.codec

import java.util.UUID

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.wsr.protocol.ClientMessage
import pl.touk.wsr.protocol.srvrdr.{Ack, RequestForSequence}
import pl.touk.wsr.protocol.wrtsrv.{Greeting, NextNumber}

class ClientMessageCodecSpec extends FlatSpec with Matchers {

  it should "correctly round-trip encode and decode writer messages" in {
    val messages = List(Greeting, NextNumber(1))
    roundTrip(ClientMessageCodec.writerExtractor, messages)
  }

  it should "correctly round-trip encode and decode reader messages" in {
    val firstSeqId = UUID.randomUUID()
    val secondSeqId = UUID.randomUUID()
    val messages = List(
      RequestForSequence(firstSeqId),
      Ack(firstSeqId),
      Ack(firstSeqId),
      RequestForSequence(secondSeqId),
      Ack(firstSeqId),
      Ack(secondSeqId)
    )
    roundTrip(ClientMessageCodec.readerExtractor, messages)
  }

  private def roundTrip(initialExtractor: SingleMessageExtractor[ClientMessage], messages: List[ClientMessage]) = {
    val str = messages.map(ClientMessageCodec.encoder.encode).reduce(_ ++ _)

    val extractor = MessagesExtractor.empty(initialExtractor)

    val (result, afterExtraction) = extractor.extract(str)

    result shouldEqual messages
  }

}