package pl.touk.wsr.writer

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.TestKit
import org.scalatest.{FlatSpecLike, Matchers}
import pl.touk.wsr.protocol.ClientMessage
import pl.touk.wsr.protocol.wrtsrv.{NextNumber, RequestForNumbers}
import pl.touk.wsr.transport.WsrClient

class WriterSpec
  extends TestKit(ActorSystem("WriterSpec"))
    with FlatSpecLike
    with Matchers {

  it should "generate numbers" in {
    val writer = prepareWriter()
    writer ! RequestForNumbers(1, 2)
    expectMsg(NextNumber(1))
    expectMsg(NextNumber(2))
    expectNoMsg()
  }

  it should "handle many requests for numbers" in {
    val writer = prepareWriter()
    writer ! RequestForNumbers(1, 2)
    expectMsg(NextNumber(1))
    expectMsg(NextNumber(2))
    writer ! RequestForNumbers(5, 3)
    expectMsg(NextNumber(5))
    expectMsg(NextNumber(6))
    expectMsg(NextNumber(7))
    expectNoMsg()
  }

  def prepareWriter(): ActorRef = {
    system.actorOf(
      Writer.props(
        new WsrClient {
          def send(message: ClientMessage): Unit = {
            testActor ! message
          }
        }))
  }

}
