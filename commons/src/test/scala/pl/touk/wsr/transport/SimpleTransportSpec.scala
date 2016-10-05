package pl.touk.wsr.transport

import java.util.UUID

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.{FlatSpecLike, Matchers}
import pl.touk.wsr.protocol.ServerMessage
import pl.touk.wsr.protocol.srvrdr.NextNumberInSequence
import pl.touk.wsr.protocol.wrtsrv.Greeting
import pl.touk.wsr.transport.simple.{ActorForwardingWsrServerHandler, SimpleWsrClientSender, SimpleWsrClientFactory}

class SimpleTransportSpec extends TestKit(ActorSystem("SimpleTransportSpec")) with FlatSpecLike with Matchers {

  it should "forward message" in {
    val client = prepareClient(null)

    client.send(Greeting)

    expectMsg(Greeting)
  }

  it should "notify handler about connection lost" in {
    val handler = new MockWsrClientHandler
    val client = prepareClient(handler)

    client.serverSender.connectionLost()

    handler.connectionLost shouldBe true
  }

  it should "notify about server message" in {
    val handler = new MockWsrClientHandler
    val client = prepareClient(handler)

    val message = NextNumberInSequence(UUID.randomUUID(), 123)
    client.serverSender.send(message)

    handler.serverMessages shouldEqual Seq(message)
  }

  def prepareClient(handler: WsrClientHandler): SimpleWsrClientSender = {
    new SimpleWsrClientFactory(testActor).awaitConnect(handler)
  }

}

class MockWsrClientHandler extends WsrClientHandler {
  @volatile var serverMessages = IndexedSeq.empty[ServerMessage]

  @volatile var connectionLost: Boolean = false

  override def onMessage(message: ServerMessage): Unit =
    serverMessages :+= message
  override def onConnectionLost(): Unit =
    connectionLost = true
}
