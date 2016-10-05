package pl.touk.wsr.server

import java.lang.management.ManagementFactory
import javax.management.ObjectName

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import pl.touk.wsr.server.receiver.{SequenceReceiver, SupplyingSequenceReceiver}
import pl.touk.wsr.server.sender.{SequenceSenderCoordinator, SupplyingWsrServerHandler}
import pl.touk.wsr.server.storage.{InMemoryStorage, StorageManager}
import pl.touk.wsr.transport.{WsrServerFactory, WsrServerHandler, WsrServerSender}

import scala.concurrent.{ExecutionContext, Future}

object ServerBoot extends App with LazyLogging {
  logger.info("SERVER is starting ....")

  val system = ActorSystem("server-system")
  implicit val ex = system.dispatcher

  val writerSideFactory = new WsrServerFactory {
    override def bind(handler: WsrServerHandler)
                     (implicit ec: ExecutionContext): Future[WsrServerSender] = Future.successful(null)
  }
  val readerSideFactory = new WsrServerFactory {
    override def bind(handler: WsrServerHandler)
                     (implicit ec: ExecutionContext): Future[WsrServerSender] = Future.successful(null)
  }

  implicit val metrics = new ServerMetrics

  val mbs = ManagementFactory.getPlatformMBeanServer
  val mBeanName = new ObjectName("pl.touk.wsr.server:name=Server")
  mbs.registerMBean(metrics, mBeanName)

  val storageManager = system.actorOf(StorageManager.props(new InMemoryStorage), "storage-manager")
  writerSideFactory.bind {
    val sequenceReceiver = system.actorOf(SequenceReceiver.props(null, storageManager), "sequence-receiver")
    new SupplyingSequenceReceiver(sequenceReceiver)
  }
  readerSideFactory.bind {
    val coordinator = system.actorOf(SequenceSenderCoordinator.props(null, storageManager), "sequence-sender-coordinator")
    new SupplyingWsrServerHandler(coordinator)
  }

  logger.info("Server has started!")
}
