package pl.touk.wsr.server

import java.lang.management.ManagementFactory
import javax.management.ObjectName

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import pl.touk.wsr.server.receiver.{SequenceReceiver, SupplyingSequenceReceiver}
import pl.touk.wsr.server.sender.{SequenceSenderCoordinator, SupplyingWsrServerHandler}
import pl.touk.wsr.server.storage.{HsqlDbStorage, StorageManager}
import pl.touk.wsr.transport.{WsrServerFactory, WsrServerHandler, WsrServerSender}

import scala.concurrent.Future

object ServerBoot extends App with LazyLogging {
  logger.info("SERVER is starting ....")

  val system = ActorSystem("server-system")
  implicit val ex = system.dispatcher

  val writerSideFactory = new WsrServerFactory {
    override def bind(server: (WsrServerSender) => WsrServerHandler): Future[Unit] = Future.successful(Unit)
  }
  val readerSideFactory = new WsrServerFactory {
    override def bind(server: (WsrServerSender) => WsrServerHandler): Future[Unit] = Future.successful(Unit)
  }

  implicit val metrics = new ServerMetrics

  val mbs = ManagementFactory.getPlatformMBeanServer
  val mBeanName = new ObjectName("pl.touk.wsr.server:name=Server")
  mbs.registerMBean(metrics, mBeanName)

  val storageManager = system.actorOf(StorageManager.props(new HsqlDbStorage), "storage-manager")
  writerSideFactory.bind { sender: WsrServerSender =>
    val sequenceReceiver = system.actorOf(SequenceReceiver.props(sender, storageManager), "sequence-receiver")
    new SupplyingSequenceReceiver(sequenceReceiver)
  }
  readerSideFactory.bind { sender: WsrServerSender =>
    val coordinator = system.actorOf(SequenceSenderCoordinator.props(sender, storageManager), "sequence-sender-coordinator")
    new SupplyingWsrServerHandler(coordinator)
  }

  logger.info("Server has started!")
}
