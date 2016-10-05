package pl.touk.wsr.server

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging

object ServerBoot extends App with LazyLogging {
  logger.info("SERVER is starting ....")

  val system = ActorSystem("server-system")
  implicit val ex = system.dispatcher
}
