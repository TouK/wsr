package pl.touk.wsr.server.storage

import scala.concurrent.Future

trait Storage {

  def addData(number: Int): Future[Unit]

  def deleteData(id: DataPackId): Future[Unit]

  def getUnreservedDataPack: Future[Option[DataPack]]

  def cancelDataPackReservation(id: DataPackId): Future[Unit]

  def requestForFreeDataSpace: Future[DataSpace]

  def freeRequestedDataSpace: Future[Unit]
}

trait DataPackId

case class DataPack(id: DataPackId, sequence: Seq[Int])

sealed trait DataSpace

case object NoFreeDataSpace extends DataSpace

case class FreeDataSpace(size: Int, offset: Int) extends DataSpace
