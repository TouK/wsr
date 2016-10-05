package pl.touk.wsr.server.storage

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.wsr.server.receiver.SequenceReceiver.{Free, Full}
import pl.touk.wsr.server.sender.SequenceSender.RequestedData
import pl.touk.wsr.server.storage.StorageManager._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class StorageManager(storage: Storage)
                    (implicit executionContext: ExecutionContext)
  extends Actor with LazyLogging {

  private var freeDataSpaceListener: Option[ActorRef] = None
  private var waitingForData = List.empty[ActorRef]

  override def receive: Receive = {
    case RegisterFreeDataSpaceListener =>
      handleRegisterFreeDataSpaceListener(sender())
    case Store(number) =>
      handleStoreNumber(number)
    case DataRequest =>
      handleDataRequest(sender())
    case HasFreeDataSpace =>
      handleHasFreeDataSpace(sender())
    case DeleteData(id) =>
      handleDeleteDataPack(id)
  }

  private def handleRegisterFreeDataSpaceListener(listener: ActorRef): Unit = {
    freeDataSpaceListener = Some(listener)
  }

  private def handleStoreNumber(number: Int): Unit = {
    storage.addData(number) andThen { case Success(_) =>
      if (waitingForData.nonEmpty) {
        storage.getUnreservedDataPack.andThen {
          case Success(Some(data)) =>
            val waiter = waitingForData.head
            waitingForData = waitingForData.tail
            waiter ! RequestedData(data)
          case Failure(ex) =>
            logger.error("Storage error", ex)
        }
      }
    }
  }

  private def handleDataRequest(dataRequestSender: ActorRef): Unit = {
    storage.getUnreservedDataPack.andThen {
      case Success(Some(data)) =>
        dataRequestSender ! RequestedData(data)
      case Success(None) =>
        waitingForData = (dataRequestSender :: waitingForData.reverse).reverse
      case Failure(ex) =>
        logger.error("Storage error", ex)
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

  private def handleDeleteDataPack(id: DataPackId): Unit = {
    for {
      _ <- storage.deleteData(id) andThen { case Failure(ex) => logger.error("Storage error", ex) }
      _ <- freeDataSpaceListener.map(handleHasFreeDataSpace).getOrElse(Future.successful({}))
    } yield ()
  }

}

object StorageManager {
  def props(storage: Storage)(implicit ec: ExecutionContext): Props = Props(new StorageManager(storage))

  // receive
  case object RegisterFreeDataSpaceListener

  case object DataRequest

  case object HasFreeDataSpace

  case class Store(number: Int)

  case class DeleteData(id: DataPackId)

}
