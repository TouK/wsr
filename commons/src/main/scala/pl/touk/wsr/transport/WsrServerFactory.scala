package pl.touk.wsr.transport

import pl.touk.wsr.protocol.{ClientMessage, ServerMessage}

import scala.concurrent.Future

trait WsrServerFactory {

  def bind(server: WsrServerSender => WsrServerHandler): Future[Unit]

}

trait WsrServerSender {

  def send(message: ServerMessage): Unit

}

trait WsrServerHandler {

  def onMessage(message: ClientMessage): Unit

}