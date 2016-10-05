package pl.touk.wsr.server.storage

import akka.actor.{Actor, Props, Terminated}
import pl.touk.wsr.server.storage.StorageManager.{DeleteData, DataRequest}

class StorageManager extends Actor {
  override def receive: Receive = {
    case DataRequest =>
    case DeleteData(id) =>
    case Terminated(dataRequestor) =>
  }
}

object StorageManager  {
  def props(): Props = Props(new StorageManager)

  // receive
  case object RegisterFreeDataSpaceListener
  case object DataRequest
  case object IsFreeDataSpace
  case class Store(number: Int)
  case class DeleteData(id: DataPackId)

  // send
  case class Free(offset: Int, size: Int)
  case object Full

  trait DataPackId
  case class DataPack(id: DataPackId, sequence: List[Int])
}
