package pl.touk.wsr.reader

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.{FlatSpecLike, Matchers}
import pl.touk.wsr.protocol.ClientMessage
import pl.touk.wsr.protocol.srvrdr.{Ack, EndOfSequence, NextNumberInSequence, RequestForSequence}
import pl.touk.wsr.transport.{WsrClientFactory, WsrClientHandler, WsrClientSender}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}

class SequencesManagerSpec
  extends TestKit(ActorSystem("SequencesManagerSpec"))
    with FlatSpecLike
    with Matchers {

  implicit val metrics = new NoOpMetrics

  it should "handle single sequence" in {
    val handler = prepareSequencesManager(1)
    val seqId = expectMsgType[RequestForSequence].seqId
    handler.onMessage(NextNumberInSequence(seqId, 1))
    expectMsg(Ack(seqId))
  }

  it should "handle many sequences" in {
    val handler = prepareSequencesManager(2)
    receiveN(2) foreach {
      case RequestForSequence(seqId) =>
        handler.onMessage(NextNumberInSequence(seqId, 1))
        expectMsg(Ack(seqId))
    }
  }

  it should "handle end of sequence" in {
    val handler = prepareSequencesManager(1)
    val seqId = expectMsgType[RequestForSequence].seqId
    handler.onMessage(EndOfSequence(seqId))
    expectMsg(Ack(seqId))
    expectMsgType[RequestForSequence]
  }

  def prepareSequencesManager(numberOfSequences: Int): WsrClientHandler = {
    val promise = Promise[WsrClientHandler]()
    system.actorOf(
      SequencesManager.props(
        numberOfSequences,
        new WsrClientFactory {
          def connect(handler: WsrClientHandler): WsrClientSender = {
            promise.success(handler)
            new WsrClientSender {
              def send(message: ClientMessage): Unit = {
                testActor ! message
              }
            }
          }
        }))
    Await.result(promise.future, Duration(10, TimeUnit.SECONDS))
  }

}
