package pl.touk.wsr.server.storage

import java.io.{FileInputStream, FileOutputStream, ObjectInputStream, ObjectOutputStream}
import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import pl.touk.wsr.server.utils.ScalaUtils._

import scala.concurrent.Future
import scala.util.Try
import scala.collection.immutable.Seq

class InMemoryStorageWithSerialization(dataPackSize: Int, maxPacksDataSize: Int, dataPath: String)
  extends Storage with LazyLogging {

  @SerialVersionUID(100L)
  private case class UUIDDataPackId(uuid: UUID) extends DataPackId with Serializable

  private case class DataPackWithReservation(dataPack: DataPack, reserved: Boolean)

  private object DataPackWithReservation {
    def apply(data: DataPack): DataPackWithReservation = DataPackWithReservation(data, reserved = false)
  }

  private var dataPacks = load().map(_.map(DataPackWithReservation(_))).getOrElse(Seq.empty[DataPackWithReservation])
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
        if (unpackedData.nonEmpty) unpackedData.last else dataPacks.last.dataPack.sequence.last
      )
    }
  }

  private def save() = Try {
    val oos = new ObjectOutputStream(new FileOutputStream(dataPath))
    oos.writeObject(dataPacks.map(_.dataPack))
    oos.close()
  } getOrElse {
    logger.error("Serialization fails!")
  }

  private def load() = Try {
    val ois = new ObjectInputStream(new FileInputStream(dataPath))
    val loadedDataPacks = ois.readObject.asInstanceOf[Seq[DataPack]]
    ois.close()
    loadedDataPacks
  }
}