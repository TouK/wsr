package pl.touk.wsr.server.sender

import java.util.UUID

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.wsr.protocol.srvrdr.{EndOfSequence, NextNumberInSequence}
import pl.touk.wsr.server.ServerMetricsReporter
import pl.touk.wsr.server.sender.SequenceSender.{Next, RequestedData}
import pl.touk.wsr.server.storage.StorageManager.{DataProcessed, DataRequest}
import pl.touk.wsr.server.storage.{DataPack, DataPackId}
import pl.touk.wsr.transport.WsrServerSender

class SequenceSender(seqId: UUID, serverSender: WsrServerSender, storage: ActorRef)
                    (implicit metrics: ServerMetricsReporter)
  extends Actor with LazyLogging {

  import context._

  private var unsentSequence: Option[List[Int]] = None
  private var sequencePackId: Option[DataPackId] = None

  override def preStart(): Unit = {
    super.preStart()
    storage ! DataRequest
  }

  override def receive: Receive = {
    case RequestedData(data) =>
      unsentSequence = Some(data.sequence.toList)
      sequencePackId = Some(data.id)
      become(dataAvailable)
      sendNextNumberFromSequence()
  }

  private def waitingForEnd: Receive = {
    case Next =>
      context.stop(self)
  }

  private def dataAvailable: Receive = {
    case Next if unsentSequence.forall(_.isEmpty) => finishSending()
    case Next => sendNextNumberFromSequence()
  }

  private def finishSending(): Unit = {
    serverSender.send(EndOfSequence(seqId))
    sequencePackId.foreach(id => storage ! DataProcessed(id))
    become(waitingForEnd)
  }

  private def sendNextNumberFromSequence(): Unit = {
    unsentSequence.getOrElse(Nil).headOption match {
      case Some(numberToSend) =>
        serverSender.send(NextNumberInSequence(seqId, numberToSend))
        metrics.reportNumberSent()
        unsentSequence = unsentSequence.map(seq => seq.tail)
      case None =>
        finishSending()
    }
  }

}

object SequenceSender {
  def prop(uuid: UUID, sender: WsrServerSender, storage: ActorRef)
          (implicit metrics: ServerMetricsReporter): Props =
    Props(new SequenceSender(uuid, sender, storage))

  case object Next
  case class RequestedData(data: DataPack)

}
