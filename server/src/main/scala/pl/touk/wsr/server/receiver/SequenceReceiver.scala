package pl.touk.wsr.server.receiver

import com.typesafe.scalalogging.LazyLogging
import pl.touk.wsr.protocol.ClientMessage
import pl.touk.wsr.protocol.wrtsrv.{Greeting, NextNumber, WriterMessage}
import pl.touk.wsr.server.storage.{FreeDataSpace, Full, StorageManager}
import pl.touk.wsr.transport.WsrServerHandler

import scala.concurrent.ExecutionContext

class SequenceReceiver(requester: SequenceRequester, storage: StorageManager)
                      (implicit executionContext: ExecutionContext)
  extends WsrServerHandler with LazyLogging {

  override protected def onMessage(message: ClientMessage): Unit = message match {
    case msg: WriterMessage => handleReaderMessage(msg)
    case _ => logger.error("Unknown client message type")
  }

  private def handleReaderMessage(msg: WriterMessage): Unit = msg match {
    case Greeting => handleGreeting()
    case NextNumber(number) => handleNextNumber(number)
  }

  private def handleGreeting(): Unit = {
    logger.info("Server handle Greeting message from Writer")
    storage.isFreeDataSpace.map {
      case FreeDataSpace(offset, size) => requester.request(size, offset)
      case Full => logger.info("Waiting for free space in storage")
    }
  }

  private def handleNextNumber(number: Int): Unit = {
    storage.storeData(number)
  }

}
