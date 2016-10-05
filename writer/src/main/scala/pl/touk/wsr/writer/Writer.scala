package pl.touk.wsr.writer

import akka.actor.{Actor, Props}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.wsr.protocol.ServerMessage
import pl.touk.wsr.protocol.wrtsrv.{Greeting, NextNumber, RequestForNumbers}
import pl.touk.wsr.transport.{WsrClientFactory, WsrClientHandler}

object Writer {

  def props(clientFactory: WsrClientFactory)
           (implicit metrics: WriterMetricsReporter): Props = {
    Props(new Writer(clientFactory))
  }

  case object ConnectionEstablished

  case object ConnectionLost

}

class Writer(clientFactory: WsrClientFactory)
            (implicit metrics: WriterMetricsReporter)
  extends Actor
    with LazyLogging {

  import Writer._
  import context._

  logger.debug("Start")

  val client = clientFactory.connect(new WsrClientHandler {
    def onMessage(message: ServerMessage): Unit = {
      self ! message
    }

    override def onConnectionEstablished(): Unit = {
      self ! ConnectionEstablished
    }

    def onConnectionLost(): Unit = {
      self ! ConnectionLost
    }
  })

  def receive = receiveDisconnected

  def receiveDisconnected: Receive = {
    case ConnectionEstablished =>
      logger.info("Connection established")
      client.send(Greeting)
      become(receiveConnected)
  }

  def receiveConnected: Receive = {
    case RequestForNumbers(start, count) =>
      logger.debug(s"Received request for $count numbers starting from $start")
      metrics.reportRequestStarted()
      start until (start + count) foreach {
        number =>
          client.send(NextNumber(number))
      }
      metrics.reportRequestFinished()
    case ConnectionLost =>
      logger.error("Connection lost")
      become(receiveDisconnected)
  }

}
