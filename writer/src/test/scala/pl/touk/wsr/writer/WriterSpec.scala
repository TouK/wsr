package pl.touk.wsr.writer

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.{FlatSpecLike, Matchers}
import pl.touk.wsr.protocol.ClientMessage
import pl.touk.wsr.protocol.wrtsrv.{NextNumber, RequestForNumbers}
import pl.touk.wsr.transport.{WsrClientFactory, WsrClientHandler, WsrClientSender}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}

class WriterSpec
  extends TestKit(ActorSystem("WriterSpec"))
    with FlatSpecLike
    with Matchers {

  it should "generate numbers" in {
    val handler = prepareWriter()
    handler.onMessage(RequestForNumbers(1, 2))
    expectMsg(NextNumber(1))
    expectMsg(NextNumber(2))
    expectNoMsg()
  }

  it should "handle many requests for numbers" in {
    val handler = prepareWriter()
    handler.onMessage(RequestForNumbers(1, 2))
    expectMsg(NextNumber(1))
    expectMsg(NextNumber(2))
    handler.onMessage(RequestForNumbers(5, 3))
    expectMsg(NextNumber(5))
    expectMsg(NextNumber(6))
    expectMsg(NextNumber(7))
    expectNoMsg()
  }

  def prepareWriter(): WsrClientHandler = {
    val promise = Promise[WsrClientHandler]()
    system.actorOf(
      Writer.props(
        new WsrClientFactory {
          def connect(handler: WsrClientHandler): Future[WsrClientSender] = {
            promise.success(handler)
            Future.successful {
              new WsrClientSender {
                def send(message: ClientMessage): Unit = {
                  testActor ! message
                }
              }
            }
          }
        }))
    Await.result(promise.future, Duration(10, TimeUnit.SECONDS))
  }

}
