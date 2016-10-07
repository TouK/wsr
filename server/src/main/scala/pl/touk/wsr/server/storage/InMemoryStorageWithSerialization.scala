package pl.touk.wsr.server.storage

import java.io._
import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import pl.touk.wsr.server.utils.ScalaUtils._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class InMemoryStorageWithSerialization(dataPackSize: Int,
                                       maxPacksDataSize: Int,
                                       dataPath: String,
                                       serializable: Boolean = true)
  extends Storage with LazyLogging {

  private val serializer = new Serializer(dataPath)

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
  private var nextOffset = dataPacks.lastOption.flatMap(_.dataPack.sequence.lastOption)
  private var waitingFor = 0

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
      waitingFor = Math.max(waitingFor - 1, 0)
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

  override def requestForFreeDataSpace: Future[DataSpace] = Future.successful {
    synchronized {
      val result =
        if (maxPacksDataSize <= dataPacks.length) NoFreeDataSpace
        else {
          val freeSpaceSize = (maxPacksDataSize - dataPacks.length) * dataPackSize - unpackedData.length
          if (freeSpaceSize + dataPacks.length * dataPackSize <= maxPacksDataSize * dataPackSize) {
            val freeDataSpace = FreeDataSpace(freeSpaceSize - waitingFor, nextOffset.getOrElse(dataPacks.size * dataPackSize))
            if (freeDataSpace.size > 0) {
              nextOffset = Some(freeDataSpace.offset + freeDataSpace.size)
              waitingFor = waitingFor + freeDataSpace.size
              freeDataSpace
            } else {
              NoFreeDataSpace
            }
          } else {
            NoFreeDataSpace
          }
        }
      result
    }
  }

  override def freeRequestedDataSpace: Future[Unit] = Future.successful {
    synchronized {
      nextOffset = nextOffset.map(_ - waitingFor)
      waitingFor = 0
    }
  }

  private def save() = {
    if (serializable) {
      serializer.serialize(dataPacks.flatMap(_.dataPack.sequence)) match {
        case r@Success(_) =>
          r
        case r@Failure(ex) =>
          logger.error("Serialization fails!", ex)
          Try(new File(dataPath).delete())
          r
      }
    }
  }

  private def load(): Try[Seq[DataPackWithReservation]] = {
    if (serializable) {
      serializer.deserialize() map { list =>
        list.grouped(dataPackSize).map(group =>
          DataPackWithReservation(DataPack(UUIDDataPackId(), group), reserved = false)
        ).toSeq
      }
    } else {
      Failure(new Exception("disabled"))
    }
  }

}