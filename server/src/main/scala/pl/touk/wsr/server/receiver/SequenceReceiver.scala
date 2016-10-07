package pl.touk.wsr.server.receiver

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.wsr.protocol.ClientMessage
import pl.touk.wsr.protocol.wrtsrv.{Greeting, NextNumber, RequestForNumbers, WriterMessage}
import pl.touk.wsr.server.ServerMetricsReporter
import pl.touk.wsr.server.receiver.SequenceReceiver.{Free, Full}
import pl.touk.wsr.server.storage.StorageManager._
import pl.touk.wsr.transport.{WsrServerFactory, WsrServerHandler, WsrServerSender}

import scala.util.{Failure, Success}

class SequenceReceiver(serverFactory: WsrServerFactory, storage: ActorRef)
                      (implicit metrics: ServerMetricsReporter)
  extends Actor with LazyLogging {

  import context._

  override def preStart(): Unit = {
    super.preStart()
    bind()
  }

  override def postRestart(reason: Throwable): Unit = {}

  private def bind() = {
    serverFactory
      .bind(new SupplyingSequenceReceiver(self))
      .andThen {
        case Success(sender) =>
          logger.debug("Sequence receiver has been bound")
          storage ! RegisterFreeDataSpaceListener
          become(common(sender))
        case f@Failure(ex) =>
          logger.error("Sequence receiver cannot bind")
          f
      }
  }

  override def receive: Receive = Actor.emptyBehavior

  private def common(sender: WsrServerSender): Receive = {
    case Greeting =>
      storage ! HasFreeDataSpace
    case Free(offset, size) =>
      sender.send(RequestForNumbers(offset, size))
    case Full =>
      logger.info("No space, should wait")
    case NextNumber(number) =>
      storage ! StoreData(number)
      metrics.reportNumberReceived()
  }
}

object SequenceReceiver {
  def props(serverFactory: WsrServerFactory, storage: ActorRef)
           (implicit metrics: ServerMetricsReporter): Props =
    Props(new SequenceReceiver(serverFactory, storage))

  case class Free(offset: Int, size: Int)

  case object Full

}

private class SupplyingSequenceReceiver(sequenceReceiver: ActorRef)
  extends WsrServerHandler with LazyLogging {

  override def onMessage(message: ClientMessage): Unit = message match {
    case msg: WriterMessage =>
      logger.debug(s"Writer message $msg has arrived")
      handleReaderMessage(msg)
    case msg =>
      logger.error(s"Unknown client message type [$msg]")
  }

  override def onConnectionLost(): Unit = {}

  private def handleReaderMessage(msg: WriterMessage): Unit = sequenceReceiver ! msg
}