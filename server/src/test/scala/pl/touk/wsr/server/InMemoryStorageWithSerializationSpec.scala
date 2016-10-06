package pl.touk.wsr.server

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.{FlatSpecLike, Matchers}
import pl.touk.wsr.server.storage.{FreeDataSpace, InMemoryStorageWithSerialization, NoFreeDataSpace}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class InMemoryStorageWithSerializationSpec
  extends TestKit(ActorSystem("StorageManagerSpec"))
    with FlatSpecLike
    with Matchers {

  it should "pass" in {
    val storage = new InMemoryStorageWithSerialization(5, 10, "/tmp/test", false)
    (0 until 15) foreach { v =>
      await(storage.addData(v))
    }
    await(storage.requestForFreeDataSpace) should be (FreeDataSpace(35, 15))
    await(storage.getUnreservedDataPack).map(_.sequence) should be (Some(Seq(0,1,2,3,4)))
    await(storage.getUnreservedDataPack).map(_.sequence) should be (Some(Seq(5,6,7,8,9)))
    await(storage.requestForFreeDataSpace) should be (NoFreeDataSpace)
  }

  private def await[T](value: Future[T]): T = {
    Await.result(value, Duration(10, TimeUnit.SECONDS))
  }
}
