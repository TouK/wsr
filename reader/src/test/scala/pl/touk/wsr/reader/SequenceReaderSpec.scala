package pl.touk.wsr.reader

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.TestKit
import org.scalatest.{FlatSpecLike, Matchers}
import pl.touk.wsr.protocol.ClientMessage
import pl.touk.wsr.protocol.srvrdr.{Ack, EndOfSequence, NextNumberInSequence, RequestForSequence}
import pl.touk.wsr.transport.WsrClientSender

class SequenceReaderSpec
  extends TestKit(ActorSystem("SequenceReaderSpec"))
    with FlatSpecLike
    with Matchers {

  implicit val metrics = new NoOpMetrics

  it should "send request for sequence" in {
    prepareSequenceReader()
    expectMsgType[RequestForSequence]
  }

  it should "process first number" in {
    val reader = prepareSequenceReader()
    expectMsgType[RequestForSequence]
    reader ! NextNumberInSequence(null, 1)
    expectMsgType[Ack]
  }

  it should "process second number" in {
    val reader = prepareSequenceReader()
    expectMsgType[RequestForSequence]
    reader ! NextNumberInSequence(null, 1)
    expectMsgType[Ack]
    reader ! NextNumberInSequence(null, 2)
    expectMsgType[Ack]
  }

  it should "process out-of-order number" in {
    val reader = prepareSequenceReader()
    expectMsgType[RequestForSequence]
    reader ! NextNumberInSequence(null, 1)
    expectMsgType[Ack]
    reader ! NextNumberInSequence(null, 3)
    expectMsgType[Ack]
  }

  it should "handle end of empty sequence" in {
    val reader = prepareSequenceReader()
    expectMsgType[RequestForSequence]
    reader ! EndOfSequence(null)
    expectMsgType[Ack]
    expectTerminated(watch(reader))
  }

  it should "handle end of sequence" in {
    val reader = prepareSequenceReader()
    expectMsgType[RequestForSequence]
    reader ! NextNumberInSequence(null, 1)
    expectMsgType[Ack]
    reader ! NextNumberInSequence(null, 2)
    expectMsgType[Ack]
    reader ! EndOfSequence(null)
    expectMsgType[Ack]
    expectTerminated(watch(reader))
  }

  def prepareSequenceReader(): ActorRef = {
    val seqId = UUID.randomUUID()
    system.actorOf(
      SequenceReader.props(
        seqId,
        new WsrClientSender {
          def send(message: ClientMessage): Unit = {
            testActor ! message
          }
        }),
      seqId.toString)
  }

}
