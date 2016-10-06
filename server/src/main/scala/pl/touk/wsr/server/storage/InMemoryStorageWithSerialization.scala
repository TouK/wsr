package pl.touk.wsr.server.storage

import java.io.{FileInputStream, FileOutputStream, ObjectInputStream, ObjectOutputStream}
import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import pl.touk.wsr.server.utils.ScalaUtils._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class InMemoryStorageWithSerialization(dataPackSize: Int, maxPacksDataSize: Int, dataPath: String)
  extends Storage with LazyLogging {

  private case class UUIDDataPackId(uuid: UUID) extends DataPackId

  private object UUIDDataPackId {
    def apply(): UUIDDataPackId = UUIDDataPackId(UUID.randomUUID())
  }

  private case class DataPackWithReservation(dataPack: DataPack, reserved: Boolean)

  private object DataPackWithReservation {
    def apply(data: DataPack): DataPackWithReservation = DataPackWithReservation(data, reserved = false)
  }

  private var dataPacks: Seq[DataPackWithReservation] = load().map(_.toSeq).getOrElse(Seq.empty[DataPackWithReservation])
  private var unpackedData: Seq[Int] = Seq.empty[Int]

  override def addData(number: Int): Future[Unit] = Future.successful {
    synchronized {
      val newUnpackedData = unpackedData :+ number
      if (newUnpackedData.length == dataPackSize) {
        val dataPack = DataPackWithReservation(DataPack(UUIDDataPackId(), newUnpackedData))
        dataPacks = dataPacks :+ dataPack
        unpackedData = List.empty[Int]
      } else {
        unpackedData = newUnpackedData
      }
      save()
    }
  }

  override def deleteData(id: DataPackId): Future[Unit] = Future.successful {
    synchronized {
      dataPacks = dataPacks.filterNot(_.dataPack.id == id)
      save()
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
        if (unpackedData.nonEmpty) unpackedData.last else dataPacks.lastOption.flatMap(_.dataPack.sequence.lastOption).getOrElse(0)
      )
    }
  }

  private def save() = Try {
    val oos = new ObjectOutputStream(new FileOutputStream(dataPath))
    oos.writeObject(dataPacks.flatMap(_.dataPack.sequence))
    oos.close()
  } match {
    case r@Success(_) =>
      r
    case r@Failure(ex) =>
      logger.error("Serialization fails!", ex)
      r
  }

  private def load(): Try[Seq[DataPackWithReservation]] = {
    Try {
      val ois = new ObjectInputStream(new FileInputStream(dataPath))
      val loadedDataPacks = ois.readObject.asInstanceOf[Seq[Int]]
      ois.close()
      loadedDataPacks
    } map { list =>
      list.grouped(dataPackSize).map(group =>
        DataPackWithReservation(DataPack(UUIDDataPackId(), group), reserved = false)
      ).toSeq
    }
  }
}