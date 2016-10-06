package pl.touk.wsr.transport.tcp

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Second, Seconds, Span}
import org.scalatest.{FlatSpecLike, Matchers}
import pl.touk.wsr.protocol.ClientMessage
import pl.touk.wsr.protocol.wrtsrv.{Greeting, RequestForNumbers}
import pl.touk.wsr.transport.tcp.codec.{ClientMessageCodec, ServerMessageCodec}
import pl.touk.wsr.transport.{MockWsrClientHandler, WsrServerHandler, WsrServerSender}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class TcpTransportSpec extends TestKit(ActorSystem("TcpTransportSpec")) with FlatSpecLike with Matchers with Eventually {

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(10, Seconds), Span(1, Second))
  import system.dispatcher

  it should "send client message and wait for response" in {
    val address = new InetSocketAddress("localhost", 8887)

    var serverSender: WsrServerSender = null
    val serverHandler = new WsrServerHandler {
      override def onMessage(message: ClientMessage): Unit = {
        message shouldEqual Greeting
        serverSender.send(RequestForNumbers(1, 2))
      }
      override def onConnectionLost(): Unit = {}
    }

    val serverFactory = new TcpWsrServerFactory(system, ClientMessageCodec.writerExtractor, address)
    serverSender = Await.result(serverFactory.bind(serverHandler), 5 seconds)


    val handler = new MockWsrClientHandler
    val clientFactory = new TcpWsrClientFactory(system, ServerMessageCodec.writerExtractor, address)
    val clientSender = clientFactory.connect(handler)

    clientSender.send(Greeting)

    eventually {
      handler.serverMessages shouldEqual List(RequestForNumbers(1, 2))
    }
  }

}