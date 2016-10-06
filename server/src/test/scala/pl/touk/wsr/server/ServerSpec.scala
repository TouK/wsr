package pl.touk.wsr.server

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.{FlatSpecLike, Matchers}
import pl.touk.wsr.protocol.ServerMessage
import pl.touk.wsr.protocol.wrtsrv.{Greeting, RequestForNumbers}
import pl.touk.wsr.server.receiver.SequenceReceiver
import pl.touk.wsr.server.sender.SequenceSenderCoordinator
import pl.touk.wsr.server.storage.{InMemoryStorageWithSerialization, StorageManager}
import pl.touk.wsr.transport._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

class ServerSpec
  extends TestKit(ActorSystem("StorageManagerSpec"))
    with FlatSpecLike
    with Matchers {

  it should "request for sequence size of full db capacity " in {
    val (writerHandler, _) = prepareServer()

    writerHandler.onMessage(Greeting)
    expectMsg(RequestForNumbers(0,15))
  }

  private def prepareServer(): (WsrServerHandler, WsrServerHandler) = {
    def mockServerFactory() = {
      val promise = Promise[WsrServerHandler]()
      val factory = new WsrServerFactory {
        override def bind(handler: WsrServerHandler)
                         (implicit ec: ExecutionContext): Future[WsrServerSender] = Future.successful {
          promise.success(handler)
          new WsrServerSender {
            override def send(message: ServerMessage): Unit = testActor ! message
          }
        }
      }
      (factory, promise)
    }

    val (readerSideFactory, readerHandlerPromise) = mockServerFactory()
    val (writerSideFactory, writerHandlerPromise) = mockServerFactory()

    implicit val metrics = new ServerMetrics
    implicit val ec = system.dispatcher

    val storage = new InMemoryStorageWithSerialization(3, 5, "/tmp/wsr")
    val storageManager = system.actorOf(StorageManager.props(storage))
    system.actorOf(SequenceReceiver.props(writerSideFactory, storageManager))
    system.actorOf(SequenceSenderCoordinator.props(readerSideFactory, storageManager))

    val readerHandler = readerHandlerPromise.future
    val writerHandler = writerHandlerPromise.future

    val timeout = Duration(10, TimeUnit.SECONDS)
    (Await.result(writerHandler, timeout), Await.result(readerHandler, timeout))
  }

}
