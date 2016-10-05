package pl.touk.wsr.server.sender

import java.util.UUID

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.wsr.protocol.srvrdr.NextNumberInSequence
import pl.touk.wsr.server.sender.SequenceSender.{Next, RequestedData}
import pl.touk.wsr.server.storage.{DataPack, DataPackId}
import pl.touk.wsr.server.storage.StorageManager.{DataRequest, DeleteData}
import pl.touk.wsr.transport.WsrServerSender

class SequenceSender(seqId: UUID, serverSender: WsrServerSender, storage: ActorRef)
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

  private def dataAvailable: Receive = {
    case Next if unsentSequence.forall(_.isEmpty) => finishSending()
    case Next => sendNextNumberFromSequence()
  }

  private def finishSending(): Unit = {
    sequencePackId.foreach(id => storage ! DeleteData(id))
    context.stop(self)
  }

  private def sendNextNumberFromSequence(): Unit = {
    unsentSequence.getOrElse(Nil).headOption match {
      case Some(numberToSend) =>
        serverSender.send(NextNumberInSequence(seqId, numberToSend))
        unsentSequence = unsentSequence.map(seq => seq.tail)
      case None =>
        finishSending()
    }
  }

}

object SequenceSender {
  def prop(uuid: UUID, sender: WsrServerSender, storage: ActorRef): Props =
    Props(new SequenceSender(uuid, sender, storage))

  case object Next
  case class RequestedData(data: DataPack)

}
