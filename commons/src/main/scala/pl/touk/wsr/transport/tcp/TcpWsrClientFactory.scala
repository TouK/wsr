package pl.touk.wsr.transport.tcp

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props, Stash, Status}
import akka.io.Tcp._
import akka.io._
import pl.touk.wsr.protocol.{ClientMessage, ServerMessage}
import pl.touk.wsr.transport._
import pl.touk.wsr.transport.tcp.ConnectingActor._
import pl.touk.wsr.transport.tcp.codec.{ClientMessageCodec, MessagesExtractor}

import scala.language.postfixOps

class TcpWsrClientFactory(actorRefFactory: ActorRefFactory,
                          initialExtractor: MessagesExtractor[ServerMessage],
                          remote: InetSocketAddress) extends WsrClientFactory {

  override def connect(handler: WsrClientHandler): WsrClientSender = {
    val connect = Connect(remote)
    // TODO: pooling, supervision, notifications about connection lost
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
                      var extractor: MessagesExtractor[ServerMessage],
                      connect: Connect) extends Actor with Stash {

  import Tcp._
  import context.system

  self ! connect

  def receive = {
    case connect: Connect =>
      IO(Tcp) ! connect
      context.become(waitingForConnection(sender()))
  }

  def waitingForConnection(originalSender: ActorRef): Receive = {
    case failed: CommandFailed =>
      originalSender ! Status.Failure(new ConnectFailedException(failed.toString))
      context stop self
    case c: Connected =>
      val connection = sender()
      connection ! Register(self)
      unstashAll()
      context.become(connected(connection))
    case other =>
      stash()
  }

  def connected(connection: ActorRef): Receive = {
    case msgToSend: ClientMessage =>
      connection ! Write(ClientMessageCodec.encoder.encode(msgToSend))
    case failed: CommandFailed =>
      throw new WriteFiledException(failed.toString)
    case Received(data) =>
      val (messages, newExtractor) = extractor.extract(data)
      extractor = newExtractor
      messages.foreach(handler.onMessage)
    case Close =>
      connection ! Close
      context.become(closingConnection)
    case closed: ConnectionClosed =>
      throw new UnexpectedCloseException(closed.toString)
  }

  val closingConnection: Receive = {
    case closed: ConnectionClosed =>
      context.stop(self)
    case failed: CommandFailed =>
      throw new Exception(failed.toString)
  }

}

object ConnectingActor {

  class ConnectFailedException(msg: String) extends Exception(msg)

  class WriteFiledException(msg: String) extends Exception(msg)

  class UnexpectedCloseException(msg: String) extends Exception(msg)

}