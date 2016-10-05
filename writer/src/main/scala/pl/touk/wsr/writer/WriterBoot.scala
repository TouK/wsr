package pl.touk.wsr.writer

import java.lang.management.ManagementFactory
import javax.management.ObjectName

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import pl.touk.wsr.protocol.ClientMessage
import pl.touk.wsr.transport.{WsrClientFactory, WsrClientHandler, WsrClientSender}

import scala.concurrent.Future

object WriterBoot extends App with LazyLogging {

  logger.info("WRITER HAS STARTED ....")

  val system = ActorSystem("writer")

  // TODO replace by real implementation
  val clientFactory: WsrClientFactory = new WsrClientFactory {
    def connect(handler: WsrClientHandler): Future[WsrClientSender] = {
      Future.successful {
        new WsrClientSender {
          def send(message: ClientMessage): Unit = {}
        }
      }
    }
  }

  implicit val metrics = new WriterMetrics

  val mbs = ManagementFactory.getPlatformMBeanServer
  val mBeanName = new ObjectName("pl.touk.wsr.writer:name=Writer")
  mbs.registerMBean(metrics, mBeanName)

  val writer = system.actorOf(Writer.props(clientFactory))

}
