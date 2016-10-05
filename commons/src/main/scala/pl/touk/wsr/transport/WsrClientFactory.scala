package pl.touk.wsr.transport

import pl.touk.wsr.protocol._

import scala.concurrent.Future

trait WsrClientFactory {

  def connect(handler: WsrClientHandler): WsrClientSender

}

trait WsrClientSender {

  def send(message: ClientMessage): Unit

}

trait WsrClientHandler {

  def onMessage(message: ServerMessage): Unit

  def onConnectionEstablished(): Unit

  def onConnectionLost(): Unit

}