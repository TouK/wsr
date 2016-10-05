package pl.touk.wsr.reader

import java.lang.management.ManagementFactory
import javax.management.ObjectName

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import pl.touk.wsr.protocol.ClientMessage
import pl.touk.wsr.transport.{WsrClientFactory, WsrClientHandler, WsrClientSender}

object ReaderBoot extends App with LazyLogging {

  logger.info("READER HAS STARTED ....")

  val system = ActorSystem("reader")

  // TODO replace by real implementation
  val clientFactory: WsrClientFactory = new WsrClientFactory {
    def connect(handler: WsrClientHandler): WsrClientSender = {
      new WsrClientSender {
        def send(message: ClientMessage): Unit = {}
      }
    }
  }

  implicit val metrics = new ReaderMetrics

  val mbs = ManagementFactory.getPlatformMBeanServer
  val mBeanName = new ObjectName("pl.touk.wsr.reader:name=Reader")
  mbs.registerMBean(metrics, mBeanName)

  val manager = system.actorOf(
    SequencesManager.props(
      1000,
      clientFactory))

}
