package pl.touk.wsr.transport.tcp

import java.net.InetSocketAddress

import collection.immutable._
import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, Props, Stash, Status}
import akka.io.Tcp.SO.KeepAlive
import akka.io.Tcp._
import akka.io._
import pl.touk.wsr.protocol.{ClientMessage, ServerMessage}
import pl.touk.wsr.transport._
import pl.touk.wsr.transport.tcp.ConnectingActor._
import pl.touk.wsr.transport.tcp.codec.{ClientMessageCodec, MessagesExtractor, SingleMessageExtractor}

import scala.language.postfixOps

class TcpWsrClientFactory(actorRefFactory: ActorRefFactory,
                          initialExtractor: SingleMessageExtractor[ServerMessage],
                          remote: InetSocketAddress) extends WsrClientFactory {

  override def connect(handler: WsrClientHandler): WsrClientSender = {
    val options = Seq(KeepAlive(true))
    val connect = Connect(remote, options = options)
    // TODO: pooling
    val connectingActor = actorRefFactory.actorOf(Props(new ConnectingActor(handler, initialExtractor, connect)))
    new TcpWsrClientSender(connectingActor)
  }

}

class TcpWsrClientSender(connectingActor: ActorRef) extends WsrClientSender {
  override def send(message: ClientMessage): Unit = {
    connectingActor ! message
  }
}

class ConnectingActor(handler: WsrClientHandler,
                      initialExtractor: SingleMessageExtractor[ServerMessage],
                      connect: Connect) extends Actor with Stash with ActorLogging {

  import Tcp._
  import context.system

  private var extractor = MessagesExtractor.empty(initialExtractor)

  IO(Tcp) ! connect

  log.info("Connecting...")

  def receive = {
    case failed: CommandFailed =>
      throw new ConnectFailedException(failed.toString)
    case c: Connected =>
      val connection = sender()
      connection ! Register(self)
      unstashAll()
      handler.onConnectionEstablished() // if will be available pooling, should be invoked only for first connection
      context.become(connected(connection))
    case other =>
      stash()
  }

  def connected(connection: ActorRef): Receive = ({
    case msgToSend: ClientMessage =>
      connection ! Write(ClientMessageCodec.encoder.encode(msgToSend), Ack)
      context.become(waitingForAck, discardOld = false)
  }: Receive) orElse handleCommonEvents

  private val waitingForAck: Receive = ({
    case msgToSend: ClientMessage =>
      stash()
    // Waiting for acks could be dramatically slow, better approach would be to use backpressure
    case Ack =>
      unstashAll()
      context.unbecome()
  }: Receive) orElse handleCommonEvents

  private lazy val handleCommonEvents: Receive = {
    case failed: CommandFailed =>
      throw new WriteFiledException(failed.toString)
    case Received(data) =>
      val (messages, newExtractor) = extractor.extract(data)
      extractor = newExtractor
      messages.foreach(handler.onMessage)
    case closed: ConnectionClosed =>
      throw new UnexpectedCloseException(closed.toString)
  }

  override def postStop(): Unit = {
    handler.onConnectionLost() // if will be available pooling, should be invoked only for last closed connection
  }

}

object ConnectingActor {

  case object Ack extends Event

  class ConnectFailedException(msg: String) extends Exception(msg)

  class WriteFiledException(msg: String) extends Exception(msg)

  class UnexpectedCloseException(msg: String) extends Exception(msg)

}