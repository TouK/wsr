package pl.touk.wsr.transport.tcp.codec

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.wsr.protocol.wrtsrv.{Greeting, NextNumber}

class ClientMessageCodecSpec extends FlatSpec with Matchers {

  it should "correctly round-trip encode and decode writer messages" in {
    val messages = List(Greeting, NextNumber(1))

    val str = messages.map(ClientMessageCodec.encoder.encode).reduce(_ ++ _)

    val extractor = MessagesExtractor.empty(ClientMessageCodec.writerExtractor)

    val (result, afterExtraction) = extractor.extract(str)

    result shouldEqual messages
  }

}