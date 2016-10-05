package pl.touk.wsr.server.storage

import java.util.UUID

import scala.concurrent.Future

trait Storage {

  // todo: what about canceling reservation?
  def addData(number: Int): Future[Unit]

  def deleteData(id: DataPackId): Future[Unit]

  def getUnreservedDataPack: Future[Option[DataPack]]

  def hasFreeDataSpace: Future[DataSpace]
}

trait DataPackId
case class DataPack(id: DataPackId, sequence: Seq[Int])

sealed trait DataSpace
case object NoFreeDataSpace extends DataSpace
case class FreeDataSpace(size: Int, offset: Int) extends DataSpace

class InMemoryStorage(dataPackSize: Int, maxPacksDataSize: Int) extends Storage {

  private case class UUIDDataPackId(uuid: UUID) extends DataPackId

  private case class DataPackWithReservation(dataPack: DataPack, reserved: Boolean)

  private object DataPackWithReservation {
    def apply(data: DataPack): DataPackWithReservation = DataPackWithReservation(data, reserved = false)
  }

  private var dataPacks = Seq.empty[DataPackWithReservation]
  private var unpackedData: Seq[Int] = Seq.empty[Int]

  override def addData(number: Int): Future[Unit] = Future.successful {
    val newUnpackedData = unpackedData :+ number
    if (newUnpackedData.length == dataPackSize) {
      val dataPackId = UUIDDataPackId(UUID.randomUUID())
      val dataPack = DataPackWithReservation(DataPack(dataPackId, newUnpackedData))
      dataPacks = dataPacks :+ dataPack
      unpackedData = List.empty[Int]
    } else {
      unpackedData = newUnpackedData
    }
  }

  override def deleteData(id: DataPackId): Future[Unit] = Future.successful {
    dataPacks = dataPacks.filterNot(_.dataPack.id == id)
  }

  override def getUnreservedDataPack: Future[Option[DataPack]] = Future.successful {
    dataPacks.find(!_.reserved).map(_.dataPack)
  }

  override def hasFreeDataSpace: Future[DataSpace] = Future.successful {
    if (maxPacksDataSize <= dataPacks.length) NoFreeDataSpace
    else FreeDataSpace(
      (maxPacksDataSize - dataPacks.length) * dataPackSize,
      if(unpackedData.nonEmpty) unpackedData.last else dataPacks.last.dataPack.sequence.last
    )
  }
}