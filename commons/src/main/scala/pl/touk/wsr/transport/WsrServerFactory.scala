package pl.touk.wsr.transport

import pl.touk.wsr.protocol.{ClientMessage, ServerMessage}

import scala.concurrent.{ExecutionContext, Future}

trait WsrServerFactory {

  def bind(handler: WsrServerHandler)
          (implicit ec: ExecutionContext): Future[WsrServerSender]

}

trait WsrServerSender {

  def send(message: ServerMessage): Unit

}

trait WsrServerHandler {

  def onMessage(message: ClientMessage): Unit

  def onConnectionLost(): Unit

}