package pl.touk.wsr.transport
import akka.actor.ActorRef
import pl.touk.wsr.protocol.{ClientMessage, ServerMessage}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

object simple {

  class SimpleWsrClientFactory(server: WsrClientHandler => SimpleWsrServerHandler) extends WsrClientFactory {

    def awaitConnect(handler: WsrClientHandler): SimpleWsrClientSender =
      Await.result(connect(handler), 1 second)

    override def connect(handler: WsrClientHandler): Future[SimpleWsrClientSender] = {
      val client = new SimpleWsrClientSender(server(handler))
      Future.successful(client)
    }
  }

  class SimpleWsrClientSender(val server: SimpleWsrServerHandler) extends WsrClientSender {

    override def send(message: ClientMessage): Unit = {
      server.onMessage(message)
    }

  }

  trait SimpleWsrServerHandler extends WsrServerHandler {

    protected def handler: WsrClientHandler

    override def onMessage(message: ClientMessage): Unit

    override def send(message: ServerMessage): Unit = {
      handler.onMessage(message)
    }

    def connectionLost(): Unit = {
      handler.onConnectionLost()
    }

  }

  class ActorForwardingWsrServerHandler(override protected val handler: WsrClientHandler, targetActor: ActorRef) extends SimpleWsrServerHandler {
    override def onMessage(message: ClientMessage): Unit = {
      targetActor ! message
    }
  }

}