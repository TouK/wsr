package pl.touk.wsr.server.storage

import java.io._
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
  private var requestedDataSpace = FreeDataSpace(0, 0)

  override def addData(number: Int): Future[Unit] = Future.successful {
    synchronized {
      val newUnpackedData = unpackedData :+ number
      if (newUnpackedData.length == dataPackSize) {
        logStorageState("adding")
        val dataPack = DataPackWithReservation(DataPack(UUIDDataPackId(), newUnpackedData))
        dataPacks = dataPacks :+ dataPack
        unpackedData = List.empty[Int]
        logStorageState("after adding")
      } else {
        unpackedData = newUnpackedData
        requestedDataSpace = FreeDataSpace(
          size = requestedDataSpace.size - dataPackSize,
          offset = requestedDataSpace.offset + dataPackSize
        )
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

  override def requestForFreeDataSpace: Future[DataSpace] = Future.successful {
    synchronized {
      val maxSize = maxPacksDataSize * dataPackSize
      val freeSize = maxSize - dataPacks.size * dataPackSize

      logStorageState("Before request")
      val result =
        if (maxPacksDataSize <= dataPacks.length) NoFreeDataSpace
        else {
          val freeSpaceSize = (maxPacksDataSize - dataPacks.length) * dataPackSize
          val offset = if (unpackedData.nonEmpty) unpackedData.last else requestedDataSpace.offset + requestedDataSpace.size
          if (freeSpaceSize + dataPacks.length * dataPackSize <= maxPacksDataSize * dataPackSize) {
            val freeDataSpace = FreeDataSpace(requestedDataSpace.size + freeSpaceSize, requestedDataSpace.size + offset)
            requestedDataSpace = FreeDataSpace(requestedDataSpace.size + freeDataSpace.size, requestedDataSpace.offset)
            freeDataSpace
          } else {
            NoFreeDataSpace
          }
        }
      logStorageState("After request")
      result
    }
  }

  private def logStorageState(info: String) = {
    val maxSize = maxPacksDataSize * dataPackSize
    logger.debug(s"""
        |---------  STORAGE ---------
        |--------- $info
        | max-size:  $maxSize
        | free-size: ${maxSize - dataPacks.size * dataPackSize}
        | requested: (offset: ${requestedDataSpace.offset}, size: ${requestedDataSpace.size})
        |----------------------------
        |""".stripMargin
    )
  }

  override def freeRequestedDataSpace: Future[Unit] = Future.successful {
    synchronized {
      requestedDataSpace = FreeDataSpace(0, 0)
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
      Try(new File(dataPath).delete())
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