package pl.touk.wsr.server.sender

import java.util.UUID

import akka.actor.{Actor, ActorRef, Props, Terminated}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.wsr.protocol.ClientMessage
import pl.touk.wsr.protocol.srvrdr.{Ack, ReaderMessage, RequestForSequence}
import pl.touk.wsr.server.ServerMetricsReporter
import pl.touk.wsr.server.sender.SequenceSender.Next
import pl.touk.wsr.server.sender.SequenceSenderCoordinator.ConnectionLost
import pl.touk.wsr.server.utils.BiMap
import pl.touk.wsr.transport.{WsrServerFactory, WsrServerHandler, WsrServerSender}

import scala.util.{Failure, Success}

class SequenceSenderCoordinator(serverFactory: WsrServerFactory, storage: ActorRef)
                               (implicit metrics: ServerMetricsReporter)
  extends Actor with LazyLogging {

  private var sequenceSenders = BiMap.empty[UUID, ActorRef]

  import context._

  override def preStart(): Unit = {
    super.preStart()
    bind()
  }

  override def postRestart(reason: Throwable): Unit = {}

  private def bind() = {
    serverFactory
      .bind(new SupplyingWsrServerHandler(self))
      .andThen {
        case Success(sender) =>
          logger.debug("Sequence sender coordinator has been bound")
          become(bounded(sender))
        case f@Failure(ex) =>
          logger.error("Sequence sender coordinator cannot bind")
          f
      }
  }

  override def receive: Receive = Actor.emptyBehavior

  private def bounded(sender: WsrServerSender): Receive = {
    case ConnectionLost => handleConnectionLost()
    case RequestForSequence(seqId) => handleRequestForSequence(seqId, sender)
    case Ack(seqId) => handleAck(seqId)
    case Terminated(seqSender) => handleSequenceSenderTermination(seqSender)
  }

  private def handleConnectionLost(): Unit = {
    val localSenders = sequenceSenders
    sequenceSenders = BiMap.empty
    localSenders.keys2.foreach(context.stop)
  }

  private def handleRequestForSequence(seqId: UUID, sender: WsrServerSender): Unit = {
    val newSequenceSenderWithId = createSequenceSender(seqId, sender)
    sequenceSenders + newSequenceSenderWithId match {
      case (newSequenceSenders, Some(_)) =>
        sequenceSenders = newSequenceSenders
      case (_, None) =>
        context.stop(newSequenceSenderWithId._2)
        logger.error(s"Cannot add sequence sender to collection; seqId = [${seqId.toString}]")
    }
  }

  private def handleAck(seqId: UUID): Unit = {
    sequenceSenders.getByKey1(seqId) match {
      case Some(seqSender) =>
        seqSender ! Next
      case None =>
        logger.error(s"Cannot find Sequence Sender for id=[${seqId.toString}]")
        metrics.reportError()
    }
  }

  private def handleSequenceSenderTermination(seqSender: ActorRef): Unit = {
    sequenceSenders.getByKey2(seqSender) match {
      case Some(seqId) =>
        sequenceSenders = sequenceSenders.removeByKey1(seqId)._1
      case None =>
    }
  }

  private def createSequenceSender(seqId: UUID, sender: WsrServerSender): (UUID, ActorRef) = {
    val sequenceSender = context.actorOf(SequenceSender.prop(seqId, sender, storage))
    context.watch(sequenceSender)
    (seqId, sequenceSender)
  }
}

object SequenceSenderCoordinator {
  def props(serverFactory: WsrServerFactory, storage: ActorRef)
           (implicit metrics: ServerMetricsReporter): Props =
    Props(new SequenceSenderCoordinator(serverFactory, storage))

  case object ConnectionLost
}

private class SupplyingWsrServerHandler(coordinator: ActorRef) extends WsrServerHandler with LazyLogging {
  override def onMessage(message: ClientMessage): Unit = message match {
    case msg: ReaderMessage =>
      logger.debug(s"Reader message [$msg] has arrived")
      handleReaderMessage(msg)
    case msg =>
      logger.error(s"Unknown client message type [$msg]")
  }

  override def onConnectionLost(): Unit = coordinator ! ConnectionLost

  private def handleReaderMessage(msg: ReaderMessage): Unit = coordinator ! msg
}