package pl.touk.wsr.server

import java.lang.management.ManagementFactory
import java.net.InetSocketAddress
import javax.management.ObjectName

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import pl.touk.wsr.protocol.ServerMessage
import pl.touk.wsr.server.receiver.SequenceReceiver
import pl.touk.wsr.server.sender.SequenceSenderCoordinator
import pl.touk.wsr.server.storage.{InMemoryStorageWithSerialization, StorageManager}
import pl.touk.wsr.transport.tcp.TcpWsrServerFactory
import pl.touk.wsr.transport.tcp.codec.ClientMessageCodec
import pl.touk.wsr.transport.{WsrServerFactory, WsrServerHandler, WsrServerSender}

import scala.concurrent.{ExecutionContext, Future}

object ServerBoot extends App with LazyLogging {
  logger.info("SERVER is starting ....")

  val system = ActorSystem("server-system")
  implicit val ex = system.dispatcher

  val writerSideFactory = new TcpWsrServerFactory(
    system,
    ClientMessageCodec.writerExtractor,
    new InetSocketAddress("0.0.0.0", 11234))

  val readerSideFactory = new TcpWsrServerFactory(
    system,
    ClientMessageCodec.readerExtractor,
    new InetSocketAddress("0.0.0.0", 21234))

  implicit val metrics = new ServerMetrics

  val mbs = ManagementFactory.getPlatformMBeanServer
  val mBeanName = new ObjectName("pl.touk.wsr.server:name=Server")
  mbs.registerMBean(metrics, mBeanName)

  val storage = new InMemoryStorageWithSerialization(10, 2000, "/tmp/wsr")
  val storageManager = system.actorOf(StorageManager.props(storage), "storage-manager")
  val sequenceReceiver = system.actorOf(SequenceReceiver.props(writerSideFactory, storageManager), "sequence-receiver")
  val sequencesSenderCoordinator = system.actorOf(SequenceSenderCoordinator.props(readerSideFactory, storageManager), "sequence-sender-coordinator")

  logger.info("SERVER has started!")
}
