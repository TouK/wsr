package pl.touk.wsr.server

import java.lang.management.ManagementFactory
import javax.management.ObjectName

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import pl.touk.wsr.protocol.ServerMessage
import pl.touk.wsr.server.receiver.SequenceReceiver
import pl.touk.wsr.server.sender.SequenceSenderCoordinator
import pl.touk.wsr.server.storage.{InMemoryStorage, StorageManager}
import pl.touk.wsr.transport.{WsrServerFactory, WsrServerHandler, WsrServerSender}

import scala.concurrent.{ExecutionContext, Future}

object ServerBoot extends App with LazyLogging {
  logger.info("SERVER is starting ....")

  val system = ActorSystem("server-system")
  implicit val ex = system.dispatcher

  val writerSideFactory = new WsrServerFactory {
    override def bind(handler: WsrServerHandler)
                     (implicit ec: ExecutionContext): Future[WsrServerSender] = Future.successful(
      new WsrServerSender {
        override def send(message: ServerMessage): Unit = {}
      }
    )
  }
  val readerSideFactory = new WsrServerFactory {
    override def bind(handler: WsrServerHandler)
                     (implicit ec: ExecutionContext): Future[WsrServerSender] = Future.successful(
      new WsrServerSender {
        override def send(message: ServerMessage): Unit = {}
      }
    )
  }

  implicit val metrics = new ServerMetrics

  val mbs = ManagementFactory.getPlatformMBeanServer
  val mBeanName = new ObjectName("pl.touk.wsr.server:name=Server")
  mbs.registerMBean(metrics, mBeanName)

  val storageManager = system.actorOf(StorageManager.props(new InMemoryStorage(10, 2000)), "storage-manager")
  val sequenceReceiver = system.actorOf(SequenceReceiver.props(writerSideFactory, storageManager), "sequence-receiver")
  val sequencesSenderCoordinator = system.actorOf(SequenceSenderCoordinator.props(readerSideFactory, storageManager), "sequence-sender-coordinator")

  logger.info("Server has started!")
}
