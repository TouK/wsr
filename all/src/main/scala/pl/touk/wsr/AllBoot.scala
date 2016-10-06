package pl.touk.wsr

import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit
import javax.management.ObjectName

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import pl.touk.wsr.protocol.{ClientMessage, ServerMessage}
import pl.touk.wsr.reader.{ReaderMetrics, SequencesManager}
import pl.touk.wsr.server.ServerMetrics
import pl.touk.wsr.server.receiver.SequenceReceiver
import pl.touk.wsr.server.sender.SequenceSenderCoordinator
import pl.touk.wsr.server.storage.{InMemoryStorageWithSerialization, StorageManager}
import pl.touk.wsr.transport._
import pl.touk.wsr.writer.{Writer, WriterMetrics}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

object AllBoot extends App with LazyLogging {

  val system = ActorSystem("wsr")
  implicit val ex = system.dispatcher

  implicit val serverMetrics = {
    val metrics = new ServerMetrics

    val mbs = ManagementFactory.getPlatformMBeanServer
    val mBeanName = new ObjectName("pl.touk.wsr.server:name=Server")
    mbs.registerMBean(metrics, mBeanName)

    metrics
  }

  implicit val writerMetrics = {
    val metrics = new WriterMetrics

    val mbs = ManagementFactory.getPlatformMBeanServer
    val mBeanName = new ObjectName("pl.touk.wsr.writer:name=Writer")
    mbs.registerMBean(metrics, mBeanName)

    metrics
  }

  implicit val readerMetrics = {
    val metrics = new ReaderMetrics

    val mbs = ManagementFactory.getPlatformMBeanServer
    val mBeanName = new ObjectName("pl.touk.wsr.reader:name=Reader")
    mbs.registerMBean(metrics, mBeanName)

    metrics
  }

  val writerServerHandlerPromise = Promise[WsrServerHandler]
  val writerServerSenderPromise = Promise[WsrServerSender]

  val writerSideFactory = new WsrServerFactory {
    override def bind(handler: WsrServerHandler)
                     (implicit ec: ExecutionContext): Future[WsrServerSender] = {
      logger.info("Server is waiting for writer...")
      writerServerHandlerPromise.success(handler)
      writerServerSenderPromise.future.andThen {
        case _ =>
          logger.info("Server has connected with writer...")
      }
    }
  }

  val readerServerHandlerPromise = Promise[WsrServerHandler]
  val readerServerSenderPromise = Promise[WsrServerSender]

  val readerSideFactory = new WsrServerFactory {
    override def bind(handler: WsrServerHandler)
                     (implicit ec: ExecutionContext): Future[WsrServerSender] = {
      logger.info("Server is waiting for reader...")
      readerServerHandlerPromise.success(handler)
      readerServerSenderPromise.future.andThen {
        case _ =>
          logger.info("Server has connected with reader...")
      }
    }
  }

  val storage = new InMemoryStorageWithSerialization(10, 20, "/tmp/wsr")
  val storageManager = system.actorOf(StorageManager.props(storage), "storage-manager")
  val sequenceReceiver = system.actorOf(SequenceReceiver.props(writerSideFactory, storageManager), "sequence-receiver")
  val sequencesSenderCoordinator = system.actorOf(SequenceSenderCoordinator.props(readerSideFactory, storageManager), "sequence-sender-coordinator")

  val writer = {
    val writerServerHandler = Await.result(writerServerHandlerPromise.future, Duration(10, TimeUnit.SECONDS))

    val writerClientFactory: WsrClientFactory = new WsrClientFactory {
      def connect(handler: WsrClientHandler): WsrClientSender = {
        logger.info("Writer has connected with server...")
        handler.onConnectionEstablished()
        writerServerSenderPromise.success(new WsrServerSender {
          def send(message: ServerMessage): Unit = {
            handler.onMessage(message)
          }
        })
        new WsrClientSender {
          def send(message: ClientMessage): Unit = {
            writerServerHandler.onMessage(message)
          }
        }
      }
    }

    system.actorOf(Writer.props(writerClientFactory))
  }

  val readerManager = {
    val readerServerHandler = Await.result(readerServerHandlerPromise.future, Duration(10, TimeUnit.SECONDS))

    val readerClientFactory: WsrClientFactory = new WsrClientFactory {
      def connect(handler: WsrClientHandler): WsrClientSender = {
        logger.info("Reader has connected with server...")
        handler.onConnectionEstablished()
        readerServerSenderPromise.success(new WsrServerSender {
          def send(message: ServerMessage): Unit = {
            handler.onMessage(message)
          }
        })
        new WsrClientSender {
          def send(message: ClientMessage): Unit = {
            readerServerHandler.onMessage(message)
          }
        }
      }
    }

    system.actorOf(
      SequencesManager.props(
        1000,
        readerClientFactory))
  }

}
