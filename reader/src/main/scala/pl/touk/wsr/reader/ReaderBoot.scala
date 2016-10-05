package pl.touk.wsr.reader

import java.lang.management.ManagementFactory
import java.net.InetSocketAddress
import javax.management.ObjectName

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import pl.touk.wsr.transport.WsrClientFactory
import pl.touk.wsr.transport.tcp.TcpWsrClientFactory

object ReaderBoot extends App with LazyLogging {

  logger.info("READER HAS STARTED ....")

  val system = ActorSystem("reader")

  val clientFactory: WsrClientFactory = new TcpWsrClientFactory(
    system,
    null,
    new InetSocketAddress("localhost", 21234))

  implicit val metrics = new ReaderMetrics

  val mbs = ManagementFactory.getPlatformMBeanServer
  val mBeanName = new ObjectName("pl.touk.wsr.reader:name=Reader")
  mbs.registerMBean(metrics, mBeanName)

  val manager = system.actorOf(
    SequencesManager.props(
      1000,
      clientFactory))

}
