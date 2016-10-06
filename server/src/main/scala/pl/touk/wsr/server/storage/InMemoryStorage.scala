package pl.touk.wsr.server.storage

import java.util.UUID
import pl.touk.wsr.server.utils.ScalaUtils._
import scala.concurrent.Future

class InMemoryStorage(dataPackSize: Int, maxPacksDataSize: Int) extends Storage {

  private case class UUIDDataPackId(uuid: UUID) extends DataPackId

  private case class DataPackWithReservation(dataPack: DataPack, reserved: Boolean)

  private object DataPackWithReservation {
    def apply(data: DataPack): DataPackWithReservation = DataPackWithReservation(data, reserved = false)
  }

  private var dataPacks = Seq.empty[DataPackWithReservation]
  private var unpackedData: Seq[Int] = Seq.empty[Int]

  override def addData(number: Int): Future[Unit] = Future.successful {
    synchronized {
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
  }

  override def deleteData(id: DataPackId): Future[Unit] = Future.successful {
    synchronized {
      dataPacks = dataPacks.filterNot(_.dataPack.id == id)
    }
  }

  override def getUnreservedDataPack: Future[Option[DataPack]] = Future.successful {
    synchronized {
      val (newDataPacks, found) = dataPacks.findAndUpdateWithReturningOld(!_.reserved, _.copy(reserved = true))
      dataPacks = newDataPacks
      found.map(_.dataPack)
    }
  }

  override def cancelDataPackReservation(id: DataPackId): Future[Unit] = Future.successful {
    synchronized {
      dataPacks = dataPacks.findAndUpdate(
        _.dataPack.id == id,
        _.copy(reserved = false)
      )
    }
  }

  override def hasFreeDataSpace: Future[DataSpace] = Future.successful {
    synchronized {
      if (maxPacksDataSize <= dataPacks.length) NoFreeDataSpace
      else FreeDataSpace(
        (maxPacksDataSize - dataPacks.length) * dataPackSize,
        if (unpackedData.nonEmpty) unpackedData.last else dataPacks.last.dataPack.sequence.last
      )
    }
  }

}