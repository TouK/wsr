package pl.touk.wsr.transport

import pl.touk.wsr.protocol.{ClientMessage, ServerMessage}

import scala.concurrent.Future

trait WsrServer {

  protected def receiveClientMessage(message: ClientMessage): Unit

  def send(message: ServerMessage): Unit

}

trait WsrServerFactory {

  def bind(server: WsrServer): Unit

}