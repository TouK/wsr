package pl.touk.wsr.transport

import pl.touk.wsr.protocol.{ClientMessage, ServerMessage}

trait WsrServerFactory {

  def bind(server: WsrServerSender => WsrServerHandler): Unit

}

trait WsrServerSender {

  def send(message: ServerMessage): Unit

}

trait WsrServerHandler {

  protected def onMessage(message: ClientMessage): Unit

}