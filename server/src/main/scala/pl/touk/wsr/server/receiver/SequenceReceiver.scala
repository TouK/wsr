package pl.touk.wsr.server.receiver

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.wsr.protocol.ClientMessage
import pl.touk.wsr.protocol.wrtsrv.{Greeting, NextNumber, RequestForNumbers, WriterMessage}
import pl.touk.wsr.server.receiver.SequenceReceiver.{Free, Full}
import pl.touk.wsr.server.storage.StorageManager._
import pl.touk.wsr.transport.{WsrServerHandler, WsrServerSender}

class SequenceReceiver(serverSender: WsrServerSender, storage: ActorRef)
  extends Actor with LazyLogging {

  import context._

  override def preStart(): Unit = {
    storage ! RegisterFreeDataSpaceListener
    super.preStart()
  }

  override def receive: Receive = common

  private def common: Receive = {
    case Greeting =>
      storage ! HasFreeDataSpace
      become(waitingForDataSpaceInfo)
  }

  private def dataRequest: Receive = {
    case Free(offset, size) =>
      serverSender.send(RequestForNumbers(offset, size))
      become(waitingForNumbers)
  }

  private def waitingForDataSpaceInfo: Receive = common orElse dataRequest orElse {
    case Full =>
      logger.info("Waiting for free space in storage")
      become(waitingForFreeDataSpace)
  }

  private def waitingForNumbers: Receive = common orElse dataRequest orElse {
    case NextNumber(number) =>
      storage ! Store(number)
  }

  private def waitingForFreeDataSpace: Receive = common orElse dataRequest

}

object SequenceReceiver {
  def props(serverSender: WsrServerSender, storage: ActorRef): Props =
    Props(new SequenceReceiver(serverSender, storage))

  case class Free(offset: Int, size: Int)
  case object Full
}

class SupplyingSequenceReceiver(sequenceReceiver: ActorRef)
  extends WsrServerHandler with LazyLogging {

  override protected def onMessage(message: ClientMessage): Unit = message match {
    case msg: WriterMessage => handleReaderMessage(msg)
    case _ => logger.error("Unknown client message type")
  }

  private def handleReaderMessage(msg: WriterMessage): Unit = sequenceReceiver ! msg
}