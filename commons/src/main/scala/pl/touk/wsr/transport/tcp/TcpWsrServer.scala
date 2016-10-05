package pl.touk.wsr.transport.tcp

import akka.actor.{Actor, ActorRef, Props, Stash, Status}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import pl.touk.wsr.protocol.{ClientMessage, ServerMessage}
import pl.touk.wsr.transport.{WsrServerFactory, WsrServerHandler, WsrServerSender}
import pl.touk.wsr.transport.tcp.BindingActor._
import pl.touk.wsr.transport.tcp.ConnectionActor._
import pl.touk.wsr.transport.tcp.codec.{MessagesExtractor, ServerMessageCodec}

class TcpWsrServerFactory extends WsrServerFactory{

  override def bind(server: WsrServerSender => WsrServerHandler): Unit = {

  }

}

class TcpWsrServerSender(actor: ActorRef) extends WsrServerSender {

  override def send(message: ServerMessage): Unit = {
    actor ! message
  }

}

class BindingActor(handler: WsrServerHandler, initialExtractor: MessagesExtractor[ClientMessage]) extends Actor with Stash {

  import Tcp._
  import context.system

  def receive = {
    case bind: Bind =>
      IO(Tcp) ! bind
      context.become(waitingForBound(sender()))
  }

  def waitingForBound(originalSender: ActorRef): Receive = {
    case failed: CommandFailed =>
      originalSender ! Status.Failure(new BindFailedException(failed.toString))
      context stop self
    case b: Bound =>
      unstashAll()
      context.become(bound)
    case other =>
      stash()
  }

  val bound: Receive = {
    case c: Connected =>
      val connection = sender()
      connection ! Register(context.actorOf(Props(new ConnectionActor(handler, initialExtractor, connection))))
    case write: Write =>
      // TODO: router write
    case other =>
      // TODO: Logging, unbind
  }

}

class ConnectionActor(handler: WsrServerHandler,
                      var extractor: MessagesExtractor[ClientMessage],
                      connection: ActorRef) extends Actor {

  override def receive: Receive = {
    case msg: ServerMessage =>
      connection ! Write(ServerMessageCodec.encoder.encode(msg))
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


object BindingActor {

  class BindFailedException(msg: String) extends Exception(msg)

}

object ConnectionActor {

  class WriteFiledException(msg: String) extends Exception(msg)

  class UnexpectedCloseException(msg: String) extends Exception(msg)

}