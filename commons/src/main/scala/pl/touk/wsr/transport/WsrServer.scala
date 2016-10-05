package pl.touk.wsr.transport

import pl.touk.wsr.protocol.{ClientMessage, ServerMessage}

import scala.concurrent.Future

trait WsrServer {

  protected def receiveClientMessage(message: ClientMessage): Unit

  protected def send(message: ServerMessage): Unit

}

trait WsrServerFactory {

  def bind(): Future[WsrServer]

}