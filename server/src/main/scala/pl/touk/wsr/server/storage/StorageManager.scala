package pl.touk.wsr.server.storage

import akka.actor.{Actor, ActorRef, Props, Terminated}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.wsr.server.receiver.SequenceReceiver.{Free, Full}
import pl.touk.wsr.server.sender.SequenceSender.RequestedData
import pl.touk.wsr.server.storage.StorageManager._
import pl.touk.wsr.server.utils.BiMap

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class StorageManager(storage: Storage)
                    (implicit executionContext: ExecutionContext)
  extends Actor with LazyLogging {

  private var freeDataSpaceListener: Option[ActorRef] = None
  private var waitingForData = List.empty[ActorRef]
  private var dataProcessingActors = BiMap.empty[ActorRef, DataPackId]

  override def receive: Receive = {
    case RegisterFreeDataSpaceListener =>
      handleRegisterFreeDataSpaceListener(sender())
    case StoreData(number) =>
      handleStoreNumber(number)
    case DataStored =>
      handleStored()
    case DispatchUnreservedData(waiter, data) =>
      handleDispatchUnreservedData(waiter, data)
    case NonUnreservedDataToDispatch(waiter, insertAtTheBeginning) =>
      handleNonUnreservedDataToDispatch(waiter, insertAtTheBeginning)
    case HasFreeDataSpace =>
      handleHasFreeDataSpace(sender())
    case DataRequest =>
      handleDataRequest(sender())
    case DataProcessed(id) =>
      handleDataProcessed(id)
    case DataDeleted(id) =>
      handleDataDeleted(id)
    case Terminated(dataProcessingActor) =>
      handleTerminated(dataProcessingActor)
  }

  private def handleRegisterFreeDataSpaceListener(listener: ActorRef): Unit = {
    freeDataSpaceListener = Some(listener)
  }

  private def handleStoreNumber(number: Int): Unit = {
    storage.addData(number) andThen { case Success(_) => self ! DataStored }
  }

  private def handleStored(): Unit = {
    if (waitingForData.nonEmpty) {
      val waiter = waitingForData.head
      waitingForData = waitingForData.tail
      tryToDispatchNewDataToProcessor(waiter, wasWaiting = true)
    }
  }

  private def handleDispatchUnreservedData(waiter: ActorRef, data: DataPack): Unit = {
    dataProcessingActors + (waiter, data.id) match {
      case (newDataProcessingActors, Some(_)) =>
        dataProcessingActors = newDataProcessingActors
        context.watch(waiter)
        waiter ! RequestedData(data)
      case (_, None) =>
        logger.error("Cannot add new data processing actor to watch collection")
    }
  }

  private def handleNonUnreservedDataToDispatch(waiter: ActorRef, insertAtTheBeginning: Boolean): Unit = {
    waitingForData =
      if (insertAtTheBeginning) {
        waiter :: waitingForData
      } else {
        (waiter :: waitingForData.reverse).reverse
      }
  }

  private def handleHasFreeDataSpace(dataRequestSender: ActorRef): Future[Unit] = {
    storage.hasFreeDataSpace.map {
      case NoFreeDataSpace => dataRequestSender ! Full
      case FreeDataSpace(size, offset) => dataRequestSender ! Free(offset, size)
    } andThen { case Failure(ex) =>
      logger.error("Storage error", ex)
    }
  }

  private def handleDataRequest(dataRequestSender: ActorRef): Unit = {
    tryToDispatchNewDataToProcessor(dataRequestSender, wasWaiting = false)
  }

  private def handleDataProcessed(id: DataPackId): Unit = {
    storage.deleteData(id) andThen {
      case Success(_) => self ! DataDeleted(id)
      case Failure(ex) => logger.error("Storage error", ex)
    }
  }

  private def handleDataDeleted(id: DataPackId): Unit = {
    dataProcessingActors = dataProcessingActors.removeByKey2(id) match {
      case (newDataProcessingActors, processingActor) =>
        processingActor.foreach(context.unwatch)
        newDataProcessingActors
    }
    freeDataSpaceListener.map(handleHasFreeDataSpace)
  }

  private def handleTerminated(dataProcessingActor: ActorRef): Unit = {
    dataProcessingActors = dataProcessingActors.removeByKey1(dataProcessingActor) match {
      case (newDataProcessingActors, Some(dataId)) =>
        storage.cancelDataPackReservation(dataId) andThen {
          case Failure(ex) => logger.error("Storage error", ex)
        }
        newDataProcessingActors
      case (newDataProcessingActors, _) =>
        newDataProcessingActors
    }
  }

  private def tryToDispatchNewDataToProcessor(dataRequestSender: ActorRef, wasWaiting: Boolean) = {
    storage.getUnreservedDataPack.andThen {
      case Success(Some(data)) =>
        self ! DispatchUnreservedData(dataRequestSender, data)
      case Success(None) =>
        self ! NonUnreservedDataToDispatch(dataRequestSender, insertAtTheBeginning = wasWaiting)
      case Failure(ex) =>
        logger.error("Storage error", ex)
    }
  }

}

object StorageManager {
  def props(storage: Storage)(implicit ec: ExecutionContext): Props = Props(new StorageManager(storage))

  // receive
  case object RegisterFreeDataSpaceListener

  case object DataRequest

  case object HasFreeDataSpace

  case class StoreData(number: Int)

  case class DataProcessed(id: DataPackId)

  // internal
  private case object DataStored

  private case class DataDeleted(id: DataPackId)

  private case class DispatchUnreservedData(waiter: ActorRef, data: DataPack)

  private case class NonUnreservedDataToDispatch(waiter: ActorRef, insertAtTheBeginning: Boolean = true)

}
