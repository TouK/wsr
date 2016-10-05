package pl.touk.wsr.server.storage

import pl.touk.wsr.server.receiver.SequenceRequester

import scala.concurrent.Future

trait StorageManager {
  def isFreeDataSpace: Future[DataSpace]
  def storeData(number: Int): Future[Unit]
}

sealed trait DataSpace
case class FreeDataSpace(offset: Int, sizE: Int) extends DataSpace
case object Full extends DataSpace

class HsqlDbStorageManager(dataRequester: SequenceRequester) {
}
