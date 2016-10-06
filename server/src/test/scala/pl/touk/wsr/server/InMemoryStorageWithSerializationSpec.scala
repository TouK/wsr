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
    val firstPack = await(storage.getUnreservedDataPack)
    firstPack.map(_.sequence) should be(Some(Seq(0, 1, 2, 3, 4)))
    val secondPack = await(storage.getUnreservedDataPack)
    secondPack.map(_.sequence) should be(Some(Seq(5, 6, 7, 8, 9)))
    await(storage.requestForFreeDataSpace) should be(NoFreeDataSpace)
    val thirdPack = await(storage.getUnreservedDataPack)
    thirdPack.map(_.sequence) should be(Some(Seq(10, 11, 12, 13, 14)))
    await(storage.deleteData(secondPack.get.id))
    add(storage, 15, 10)
    await(storage.requestForFreeDataSpace) should be(FreeDataSpace(5, 50))
    await(storage.requestForFreeDataSpace) should be(NoFreeDataSpace)
    await(storage.requestForFreeDataSpace) should be(NoFreeDataSpace)
    await(storage.deleteData(firstPack.get.id))
    await(storage.deleteData(thirdPack.get.id))
    await(storage.requestForFreeDataSpace) should be(FreeDataSpace(10, 55))
    add(storage, 25, 30)
    await(storage.requestForFreeDataSpace) should be(NoFreeDataSpace)
    val forthPack = await(storage.getUnreservedDataPack)
    forthPack.map(_.sequence) should be(Some(Seq(15, 16, 17, 18, 19)))
    val fifthPack = await(storage.getUnreservedDataPack)
    fifthPack.map(_.sequence) should be(Some(Seq(20, 21, 22, 23, 24)))
    add(storage, 55, 3)
    await(storage.requestForFreeDataSpace) should be(NoFreeDataSpace)
  }

  it should "pass too" in {
    val storage = new InMemoryStorageWithSerialization(5, 10, "/tmp/test", false)
    await(storage.getUnreservedDataPack) should be(None)
    await(storage.requestForFreeDataSpace) should be(FreeDataSpace(50, 0))
    await(storage.requestForFreeDataSpace) should be(NoFreeDataSpace)
    add(storage, 0, 3)
    await(storage.requestForFreeDataSpace) should be(NoFreeDataSpace)
    add(storage, 3, 3)
    await(storage.requestForFreeDataSpace) should be(NoFreeDataSpace)
    val firstPack = await(storage.getUnreservedDataPack)
    firstPack.map(_.sequence) should be(Some(Seq(0, 1, 2, 3, 4)))
    await(storage.requestForFreeDataSpace) should be(NoFreeDataSpace)
    await(storage.deleteData(firstPack.get.id))
    await(storage.requestForFreeDataSpace) should be(FreeDataSpace(4, 50))
    add(storage, 6, 1)
    await(storage.requestForFreeDataSpace) should be(NoFreeDataSpace)
    add(storage, 7, 2)
    await(storage.requestForFreeDataSpace) should be(NoFreeDataSpace)
    await(storage.getUnreservedDataPack) should be(None)
//    add(storage, 9, 1)
//    await(storage.requestForFreeDataSpace) should be(NoFreeDataSpace)
//    val secondPack = await(storage.getUnreservedDataPack)
//    secondPack.map(_.sequence) should be(Some(Seq(5, 6, 7, 8, 9)))
//    await(storage.requestForFreeDataSpace) should be(NoFreeDataSpace)
//    await(storage.deleteData(secondPack.get.id))
//    await(storage.requestForFreeDataSpace) should be(FreeDataSpace(5, 55))
  }

  private def await[T](value: Future[T]): T = {
    Await.result(value, Duration(10, TimeUnit.SECONDS))
  }

  private def add(storage: Storage, offset: Int, size: Int) = {
    (offset until offset + size) foreach { v =>
      await(storage.addData(v))
    }
  }
}
