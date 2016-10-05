package pl.touk.wsr.writer

import akka.actor.{Actor, Props}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.wsr.protocol.wrtsrv.{NextNumber, RequestForNumbers}
import pl.touk.wsr.transport.WsrClientSender

object Writer {

  def props(client: WsrClientSender): Props = {
    Props(new Writer(client))
  }

}

class Writer(client: WsrClientSender)
  extends Actor
    with LazyLogging {

  def receive = {
    case RequestForNumbers(start, count) =>
      logger.debug(s"Received request for $count numbers starting from $start")
      start until (start + count) foreach {
        number =>
          client.send(NextNumber(number))
      }
  }

}
