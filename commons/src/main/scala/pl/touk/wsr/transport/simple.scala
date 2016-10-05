package pl.touk.wsr.transport
import akka.actor.ActorRef
import pl.touk.wsr.protocol.{ClientMessage, ServerMessage}

object simple {

  class SimpleWsrClientFactory(targetActor: ActorRef) extends WsrClientFactory {
    override def connect(clientHandler: WsrClientHandler): SimpleWsrClientSender = {
      val serverSender = new SimpleWsrServerSender(clientHandler)
      val serverHandler = new ActorForwardingWsrServerHandler(targetActor)
      new SimpleWsrClientSender(serverSender, serverHandler)
    }
  }

  class SimpleWsrClientSender(val serverSender: SimpleWsrServerSender,
                              val serverHandler: WsrServerHandler) extends WsrClientSender {

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