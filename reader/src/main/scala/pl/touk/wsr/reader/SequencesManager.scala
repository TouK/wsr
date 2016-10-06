package pl.touk.wsr.reader

import java.util.UUID

import akka.actor.{Actor, ActorRef, Props, Terminated}
import com.typesafe.scalalogging.StrictLogging
import pl.touk.wsr.protocol.ServerMessage
import pl.touk.wsr.protocol.srvrdr.{EndOfSequence, NextNumberInSequence}
import pl.touk.wsr.transport.{WsrClientFactory, WsrClientHandler, WsrClientSender}

object SequencesManager {

  def props(numberOfSequences: Int,
            clientFactory: WsrClientFactory)
           (implicit metrics: ReaderMetricsReporter): Props = {
    Props(new SequencesManager(
      numberOfSequences,
      clientFactory))
  }

  case object ConnectionEstablished

  case object ConnectionLost

}

private class SequencesManager(numberOfSequences: Int,
                               clientFactory: WsrClientFactory)
                              (implicit metrics: ReaderMetricsReporter)
  extends Actor
    with StrictLogging {

  import SequencesManager._
  import context._

  logger.debug("Start")

  var sequences: Map[UUID, ActorRef] = Map.empty

  val client = clientFactory.connect(new WsrClientHandler {
    def onMessage(message: ServerMessage): Unit = {
      self ! message
    }

    override def onConnectionEstablished(): Unit = {
      self ! ConnectionEstablished
    }

    def onConnectionLost(): Unit = {
      self ! ConnectionLost
    }
  })

  def receive = receiveDisconnected

  def receiveDisconnected: Receive = {
    case ConnectionEstablished =>
      logger.info("Connection established")
      sequences.size until numberOfSequences foreach {
        _ =>
          createSequenceReader(client)
      }
      become(receiveConnected)
    case Terminated(ref) =>
      val seqId = UUID.fromString(ref.path.name)
      sequences -= seqId
  }

  def receiveConnected: Receive = {
    case msg@NextNumberInSequence(seqId, number) =>
      sequences.get(seqId) match {
        case Some(ref) =>
          ref ! msg
        case None =>
          logger.error(s"Received value $number for unknown sequence $seqId")
          metrics.reportUnknownSequenceValueError()
      }
    case msg@EndOfSequence(seqId) =>
      sequences.get(seqId) match {
        case Some(ref) =>
          ref ! msg
        case None =>
          logger.error(s"Received end of unknown sequence $seqId")
          metrics.reportUnknownSequenceEndError()
      }
    case ConnectionLost =>
      logger.error("Connection lost")
      metrics.reportConnectionLostError()
      sequences.values.foreach(stop)
      become(receiveDisconnected)
    case Terminated(ref) =>
      val seqId = UUID.fromString(ref.path.name)
      sequences -= seqId
      createSequenceReader(client)
  }

  def createSequenceReader(client: WsrClientSender): Unit = {
    val seqId = UUID.randomUUID()
    val actor = actorOf(
      SequenceReader.props(
        seqId,
        client),
      seqId.toString)
    watch(actor)
    sequences += seqId -> actor
  }

}
