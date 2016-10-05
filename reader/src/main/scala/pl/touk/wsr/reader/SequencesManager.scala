package pl.touk.wsr.reader

import java.util.UUID

import akka.actor.{Actor, ActorRef, Props, Stash, Terminated}
import akka.pattern.pipe
import com.typesafe.scalalogging.StrictLogging
import pl.touk.wsr.protocol.ServerMessage
import pl.touk.wsr.protocol.srvrdr.{EndOfSequence, NextNumberInSequence}
import pl.touk.wsr.transport.{WsrClient, WsrClientFactory, WsrHandler}

object SequencesManager {

  def props(numberOfSequences: Int,
            clientFactory: WsrClientFactory): Props = {
    Props(new SequencesManager(
      numberOfSequences,
      clientFactory))
  }

  case object ConnectionLost

}

private class SequencesManager(numberOfSequences: Int,
                               clientFactory: WsrClientFactory)
  extends Actor
    with Stash
    with StrictLogging {

  import SequencesManager._
  import context._

  logger.debug("Start")

  var sequences: Map[UUID, ActorRef] = Map.empty

  clientFactory.connect(new WsrHandler {
    def onMessage(message: ServerMessage): Unit = {
      self ! message
    }

    def onConnectionLost(): Unit = {
      self ! ConnectionLost
    }
  }) pipeTo self

  def receive = {
    case client: WsrClient =>
      1 to numberOfSequences foreach {
        _ =>
          createSequenceReader(client)
      }
      unstashAll()
      become(receiveConnected(client))
    case _ =>
      stash()
  }

  def receiveConnected(client: WsrClient): Receive = {
    case msg@NextNumberInSequence(seqId, number) =>
      sequences.get(seqId) match {
        case Some(ref) =>
          ref ! msg
        case None =>
          logger.error(s"Received value $number for unknown sequence $seqId")
      }
    case msg@EndOfSequence(seqId) =>
      sequences.get(seqId) match {
        case Some(ref) =>
          ref ! msg
        case None =>
          logger.error(s"Received end of unknown sequence $seqId")
      }
    case ConnectionLost =>
      sequences.values.foreach(stop)
    case Terminated(ref) =>
      val seqId = UUID.fromString(ref.path.name)
      sequences -= seqId
      createSequenceReader(client)
  }

  def createSequenceReader(client: WsrClient) = {
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
