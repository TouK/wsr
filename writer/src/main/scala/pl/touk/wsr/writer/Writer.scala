package pl.touk.wsr.writer

import akka.actor.{Actor, Props, Stash, Status}
import akka.pattern.pipe
import com.typesafe.scalalogging.LazyLogging
import pl.touk.wsr.protocol.ServerMessage
import pl.touk.wsr.protocol.wrtsrv.{NextNumber, RequestForNumbers}
import pl.touk.wsr.transport.{WsrClientFactory, WsrClientHandler, WsrClientSender}

object Writer {

  def props(clientFactory: WsrClientFactory): Props = {
    Props(new Writer(clientFactory))
  }

  case object ConnectionLost

}

class Writer(clientFactory: WsrClientFactory)
  extends Actor
    with Stash
    with LazyLogging {

  import Writer._
  import context._

  logger.debug("Start")

  clientFactory.connect(new WsrClientHandler {
    def onMessage(message: ServerMessage): Unit = {
      self ! message
    }

    def onConnectionLost(): Unit = {
      self ! ConnectionLost
    }
  }) pipeTo self

  def receive = {
    case client: WsrClientSender =>
      logger.debug("Connected")
      unstashAll()
      become(receiveConnected(client))
    case Status.Failure(cause) =>
      logger.error("Exception while connecting", cause)
      system.terminate()
    case _ =>
      stash()
  }

  def receiveConnected(client: WsrClientSender): Receive = {
    case RequestForNumbers(start, count) =>
      logger.debug(s"Received request for $count numbers starting from $start")
      start until (start + count) foreach {
        number =>
          client.send(NextNumber(number))
      }
    case ConnectionLost =>
      logger.error("Connection lost")
  }

}
