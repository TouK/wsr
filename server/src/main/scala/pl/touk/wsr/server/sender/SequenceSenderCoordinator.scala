package pl.touk.wsr.server.sender

import java.util.UUID

import akka.actor.{Actor, ActorRef, Props, Terminated}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.wsr.protocol.ClientMessage
import pl.touk.wsr.protocol.srvrdr.{Ack, ReaderMessage, RequestForSequence}
import pl.touk.wsr.server.sender.SequenceSender.Next
import pl.touk.wsr.transport.{WsrServerHandler, WsrServerSender}

class SequenceSenderCoordinator(sender: WsrServerSender, storage: ActorRef)
  extends Actor with LazyLogging {

  private var sequenceSenders = Map.empty[UUID, ActorRef]

  override def receive: Receive = {
    case RequestForSequence(seqId) => handleRequestForSequence(seqId)
    case Ack(seqId) => handleAck(seqId)
    case Terminated(seqSender) => handleSequenceSenderTermination(seqSender)
  }

  private def handleRequestForSequence(seqId: UUID): Unit = {
    sequenceSenders = sequenceSenders + createSequenceSender(seqId)
  }

  private def handleAck(seqId: UUID): Unit = {
    sequenceSenders.get(seqId) match {
      case Some(seqSender) => seqSender ! Next
      case None => logger.error(s"Cannot find Sequence Sender for id=[${seqId.toString}]")
    }
  }

  private def handleSequenceSenderTermination(seqSender: ActorRef): Unit = {
    sequenceSenders.find { case (_, ref) => ref == seqSender} match {
      case Some((seqId, _)) =>
        sequenceSenders = sequenceSenders - seqId
      case None =>
        logger.error("There is no Sequence sender in map!")
    }
  }

  private def createSequenceSender(seqId: UUID): (UUID, ActorRef) = {
    val sequenceSender = context.actorOf(SequenceSender.prop(seqId, sender, storage))
    context.watch(sequenceSender)
    (seqId, sequenceSender)
  }
}

object SequenceSenderCoordinator {
  def props(sender: WsrServerSender, storage: ActorRef): Props =
    Props(new SequenceSenderCoordinator(sender, storage))
}

class SupplyingWsrServerHandler(coordinator: ActorRef) extends WsrServerHandler with LazyLogging {
  override protected def onMessage(message: ClientMessage): Unit = message match {
    case msg: ReaderMessage => handleReaderMessage(msg)
    case _ => logger.error("Unknown client message type")
  }

  private def handleReaderMessage(msg: ReaderMessage): Unit = coordinator ! msg
}