package pl.touk.wsr.transport
import akka.actor.ActorRef
import pl.touk.wsr.protocol.{ClientMessage, ServerMessage}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

object simple {

  class SimpleWsrClientFactory(targetActor: ActorRef) extends WsrClientFactory {

    def awaitConnect(handler: WsrClientHandler): SimpleWsrClientSender =
      Await.result(connect(handler), 1 second)

    override def connect(clientHandler: WsrClientHandler): Future[SimpleWsrClientSender] = {
      val serverSender = new SimpleWsrServerSender(clientHandler)
      val serverHandler = new ActorForwardingWsrServerHandler(targetActor)
      val clientSender = new SimpleWsrClientSender(serverSender, serverHandler)
      Future.successful(clientSender)
    }
  }

  class SimpleWsrClientSender(val serverSender: SimpleWsrServerSender,
                              val serverHandler: ActorForwardingWsrServerHandler) extends WsrClientSender {

    override def send(message: ClientMessage): Unit = {
      serverHandler.onMessage(message)
    }

  }

  class SimpleWsrServerSender(handler: WsrClientHandler) extends WsrServerSender {

    override def send(message: ServerMessage): Unit = {
      handler.onMessage(message)
    }

    def connectionLost(): Unit = {
      handler.onConnectionLost()
    }

  }

  class ActorForwardingWsrServerHandler(targetActor: ActorRef) extends WsrServerHandler {
    override def onMessage(message: ClientMessage): Unit = {
      targetActor ! message
    }
  }

}