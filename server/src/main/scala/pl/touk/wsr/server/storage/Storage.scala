package pl.touk.wsr.server.storage

import scala.concurrent.Future

// data state: unreserved, reserved
trait Storage {

  def addData(number: Int): Future[Unit]
  def deleteData(id: DataPackId): Future[Unit]
  def getUnreservedDataPack: Future[Option[DataPack]]
  def hasFreeDataSpace: Future[DataSpace]
}

trait DataPackId
case class DataPack(id: DataPackId, sequence: List[Int])

sealed trait DataSpace
case object NoFreeDataSpace extends DataSpace
case class FreeDataSpace(size: Int, offset: Int) extends DataSpace

class HsqlDbStorage extends Storage {
  override def addData(number: Int): Future[Unit] = ???

  override def deleteData(id: DataPackId): Future[Unit] = ???

  override def getUnreservedDataPack: Future[Option[DataPack]] = ???

  override def hasFreeDataSpace: Future[DataSpace] = ???
}