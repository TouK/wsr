package pl.touk.wsr.writer

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
    def connect(handler: WsrClientHandler): WsrClientSender = {
      new WsrClientSender {
        def send(message: ClientMessage): Unit = {}
      }
    }
  }

  val writer = system.actorOf(Writer.props(clientFactory))

}
