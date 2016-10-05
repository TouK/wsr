package pl.touk.wsr.transport
import akka.actor.ActorRef
import pl.touk.wsr.protocol.{ClientMessage, ServerMessage}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

object simple {

  class SimpleWsrClientFactory(server: WsrHandler => SimpleWsrServer) extends WsrClientFactory {

    def awaitConnect(handler: WsrHandler): SimpleWsrClient =
      Await.result(connect(handler), 1 second)

    override def connect(handler: WsrHandler): Future[SimpleWsrClient] = {
      val client = new SimpleWsrClient(server(handler))
      Future.successful(client)
    }
  }

  class SimpleWsrClient(val server: SimpleWsrServer) extends WsrClient {

    override def send(message: ClientMessage): Unit = {
      server.receiveClientMessage(message)
    }

  }

  trait SimpleWsrServer extends WsrServer {

    protected def handler: WsrHandler

    override def receiveClientMessage(message: ClientMessage): Unit

    override def send(message: ServerMessage): Unit = {
      handler.onMessage(message)
    }

    def connectionLost(): Unit = {
      handler.onConnectionLost()
    }

  }

  class ActorForwardingWsrServer(override protected val handler: WsrHandler, targetActor: ActorRef) extends SimpleWsrServer {
    override def receiveClientMessage(message: ClientMessage): Unit = {
      targetActor ! message
    }
  }

}