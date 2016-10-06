package pl.touk.wsr.server

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.{FlatSpecLike, Matchers}
import pl.touk.wsr.server.storage.{FreeDataSpace, InMemoryStorageWithSerialization, NoFreeDataSpace, Storage}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class InMemoryStorageWithSerializationSpec
  extends TestKit(ActorSystem("StorageManagerSpec"))
    with FlatSpecLike
    with Matchers {

  it should "pass" in {
    val storage = new InMemoryStorageWithSerialization(5, 10, "/tmp/test", false)
    add(storage, 0, 15)
    await(storage.requestForFreeDataSpace) should be(FreeDataSpace(35, 15))
    await(storage.getUnreservedDataPack).map(_.sequence) should be(Some(Seq(0, 1, 2, 3, 4)))
    val secondPack = await(storage.getUnreservedDataPack)
    secondPack.map(_.sequence) should be(Some(Seq(5, 6, 7, 8, 9)))
    await(storage.requestForFreeDataSpace) should be(NoFreeDataSpace)
    await(storage.getUnreservedDataPack).map(_.sequence) should be(Some(Seq(10, 11, 12, 13, 14)))
    await(storage.deleteData(secondPack.get.id))
    add(storage, 15, 25)
    await(storage.requestForFreeDataSpace) should be(FreeDataSpace(5, 50))
    await(storage.requestForFreeDataSpace) should be(NoFreeDataSpace)
  }

  private def await[T](value: Future[T]): T = {
    Await.result(value, Duration(10, TimeUnit.SECONDS))
  }

  private def add(storage: Storage, offset: Int, size: Int) = {
    (offset until offset+size) foreach { v =>
      await(storage.addData(v))
    }
  }
}
