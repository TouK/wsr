package pl.touk.wsr.transport.tcp

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.{FlatSpecLike, Matchers}
import pl.touk.wsr.protocol.wrtsrv.Greeting
import pl.touk.wsr.transport.WsrClientSender

class TcpTransportSpec extends TestKit(ActorSystem("TcpTransportSpec")) with FlatSpecLike with Matchers {

  it should "send message" in {
    val client: WsrClientSender = null

    client.send(Greeting)

    expectMsg(Greeting)
  }


}