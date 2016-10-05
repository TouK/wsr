package pl.touk.wsr.reader

import java.util.UUID

import akka.actor.{Actor, Props}
import com.typesafe.scalalogging.StrictLogging
import pl.touk.wsr.protocol.srvrdr.{Ack, EndOfSequence, NextNumberInSequence, RequestForSequence}
import pl.touk.wsr.transport.WsrClientSender

object SequenceReader {

  def props(seqId: UUID,
            client: WsrClientSender)
           (implicit metrics: ReaderMetricsReporter): Props = {
    Props(new SequenceReader(
      seqId,
      client))
  }

}

private class SequenceReader(seqId: UUID,
                             client: WsrClientSender)
                            (implicit metrics: ReaderMetricsReporter)
  extends Actor
    with StrictLogging {

  import context._

  logger.debug(s"Sequence $seqId: start")

  metrics.reportSequenceStarted()

  client.send(RequestForSequence(seqId))

  def receive = receiveCommon orElse {
    case NextNumberInSequence(_, number) =>
      logger.debug(s"Sequence $seqId: received value $number")
      client.send(Ack(seqId))
      become(receiveNext(number))
  }

  def receiveNext(previous: Int): Receive = receiveCommon orElse {
    case NextNumberInSequence(_, number) =>
      logger.debug(s"Sequence $seqId: received value $number")
      if (number != previous + 1) {
        logger.error(s"Sequence $seqId: out-of-order value $number after value $previous")
      }
      client.send(Ack(seqId))
      become(receiveNext(number))
  }

  def receiveCommon: Receive = {
    case EndOfSequence(_) =>
      logger.debug(s"Sequence $seqId: received end of sequence")
      client.send(Ack(seqId))
      metrics.reportSequenceFinished()
      stop(self)
  }

}
