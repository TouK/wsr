package pl.touk.wsr.transport

import pl.touk.wsr.protocol._

import scala.concurrent.Future

trait WsrClient {

  def send(message: ClientMessage): Unit

}

trait WsrClientFactory {

  def connect(handler: WsrHandler): Future[WsrClient]

}

trait WsrHandler {

  def onMessage(message: ServerMessage): Unit

  def onConnectionLost(): Unit

}