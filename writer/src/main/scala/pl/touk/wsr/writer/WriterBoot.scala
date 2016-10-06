package pl.touk.wsr.writer

import java.lang.management.ManagementFactory
import java.net.InetSocketAddress
import javax.management.ObjectName

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import pl.touk.wsr.transport.WsrClientFactory
import pl.touk.wsr.transport.tcp.TcpWsrClientFactory
import pl.touk.wsr.transport.tcp.codec.ServerMessageCodec

object WriterBoot extends App with LazyLogging {

  logger.info("WRITER is starting ....")

  val system = ActorSystem("writer")

  val clientFactory: WsrClientFactory = new TcpWsrClientFactory(
    system,
    ServerMessageCodec.writerExtractor,
    new InetSocketAddress("server", 11234))

  implicit val metrics = new WriterMetrics

  val mbs = ManagementFactory.getPlatformMBeanServer
  val mBeanName = new ObjectName("pl.touk.wsr.writer:name=Writer")
  mbs.registerMBean(metrics, mBeanName)

  val writer = system.actorOf(Writer.props(clientFactory))

  logger.info("WRITER has started!")

}
