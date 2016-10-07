package pl.touk.wsr.transport.tcp

import java.net.InetSocketAddress

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, OneForOneStrategy, Props, Stash, Status, SupervisorStrategy}
import akka.io.Tcp.SO.KeepAlive
import akka.io.Tcp._
import akka.io.{IO, Inet, Tcp}
import akka.pattern._
import akka.routing.{ActorRefRoutee, RoundRobinRoutingLogic, Router}
import akka.util.Timeout
import pl.touk.wsr.protocol.{ClientMessage, ServerMessage}
import pl.touk.wsr.transport.tcp.BindingActor._
import pl.touk.wsr.transport.tcp.ConnectionHandlerActor._
import pl.touk.wsr.transport.tcp.codec.{MessagesExtractor, ServerMessageCodec, SingleMessageExtractor}
import pl.touk.wsr.transport.{WsrServerFactory, WsrServerHandler, WsrServerSender}

import scala.collection.immutable._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.control.NonFatal

class TcpWsrServerFactory(actorRefFactory: ActorRefFactory,
                          initialExtractor: SingleMessageExtractor[ClientMessage],
                          localAddress: InetSocketAddress) extends WsrServerFactory{

  override def bind(handler: WsrServerHandler)
                   (implicit ec: ExecutionContext): Future[WsrServerSender] = {
    val bindingActor = actorRefFactory.actorOf(Props(new BindingActor(handler, initialExtractor)))
    val sender = new TcpWsrServerSender(bindingActor)
    implicit val bindTimeout = Timeout(35 seconds) // TODO: from configuration
    (bindingActor ? DoBind(localAddress)).map(_ => sender)
  }

}

class TcpWsrServerSender(actor: ActorRef) extends WsrServerSender {

  override def send(message: ServerMessage): Unit = {
    actor ! message
  }

}

class BindingActor(handler: WsrServerHandler, initialExtractor: SingleMessageExtractor[ClientMessage]) extends Actor with Stash with ActorLogging {

  import Tcp._
  import context.system

  var router = Router(RoundRobinRoutingLogic())

  def receive = {
    case DoBind(localAddress) =>
      val options = Seq(KeepAlive(true))
      IO(Tcp) ! Bind(self, localAddress, options = options)
      context.become(waitingForBound(sender()))
  }

  def waitingForBound(originalSender: ActorRef): Receive = {
    case failed: CommandFailed =>
      originalSender ! Status.Failure(new BindFailedException(failed.toString))
      context stop self
    case b: Bound =>
      unstashAll()
      context.become(bound)
      originalSender ! Status.Success(())
    case other =>
      stash()
  }

  val bound: Receive = {
    case c: Connected =>
      val connection = sender()
      val handlerActor = context.actorOf(Props(new ConnectionHandlerActor(handler, initialExtractor, connection)))
      router = Router(RoundRobinRoutingLogic(), context.children.map(ActorRefRoutee).toIndexedSeq)
      connection ! Register(handlerActor)
    case msg: ServerMessage =>
      router.route(msg, sender())
  }

  override def supervisorStrategy: SupervisorStrategy = {
    OneForOneStrategy() {
      case NonFatal(ex) =>
        log.error("Connection handler stopping because of exception", ex)
        if (context.children.size == 1) {
          // lost connection only when there is no other open connection
          handler.onConnectionLost()
        }
        router = Router(RoundRobinRoutingLogic(), context.children.map(ActorRefRoutee).toIndexedSeq)
        Stop
    }
  }

}

class ConnectionHandlerActor(handler: WsrServerHandler,
                             initialExtractor: SingleMessageExtractor[ClientMessage],
                             connection: ActorRef) extends Actor {

  private var extractor = MessagesExtractor.empty(initialExtractor)

  override def receive: Receive = {
    case msg: ServerMessage =>
      connection ! Write(ServerMessageCodec.encoder.encode(msg))
    case failed: CommandFailed =>
      throw new WriteFiledException(failed.toString)
    case Received(data) =>
      val (messages, newExtractor) = extractor.extract(data)
      extractor = newExtractor
      messages.foreach(handler.onMessage)
    case closed: ConnectionClosed =>
      throw new UnexpectedCloseException(closed.toString)
  }

}


object BindingActor {

  case class DoBind(localAddress: InetSocketAddress)

  class BindFailedException(msg: String) extends Exception(msg)

}

object ConnectionHandlerActor {

  class WriteFiledException(msg: String) extends Exception(msg)

  class UnexpectedCloseException(msg: String) extends Exception(msg)

}