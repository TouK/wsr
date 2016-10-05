package pl.touk.wsr.transport.tcp.codec

import java.util.UUID

import akka.util.ByteString

import scala.annotation.tailrec

case class MessagesExtractor[T](singleExtractor: SingleMessageExtractor[T], rest: EnrichedByteString) {

  def extract(str: ByteString): (List[T], MessagesExtractor[T]) = {
    val (messages, extractor) = copy(rest = rest ++ EnrichedByteString(str)).extractAcc(List.empty[T])
    (messages.reverse, extractor)
  }

  @tailrec
  final def extractAcc(messages: List[T]): (List[T], MessagesExtractor[T]) = {
    singleExtractor.extractMessage(rest) match {
      case Some((message, newSingleExtractor, newRest)) =>
        copy(newSingleExtractor, newRest).extractAcc(message :: messages)
      case None =>
        (messages, this)
    }
  }

}

object MessagesExtractor {
  def empty[T](singleExtractor: SingleMessageExtractor[T]): MessagesExtractor[T] =
    new MessagesExtractor[T](singleExtractor, EnrichedByteString(ByteString.empty))
}

trait SingleMessageExtractor[T] {

  def extractMessage(str: EnrichedByteString): Option[(T, SingleMessageExtractor[T], EnrichedByteString)]

}

case class EnrichedByteString(str: ByteString) {

  def ++(other: EnrichedByteString): EnrichedByteString =
    copy(str ++ other.str)

  def readInt(): Option[(Int, EnrichedByteString)] = {
    Option(str).filter { _ =>
      str.size >= Integer.BYTES
    }.map { _ =>
      (str.toByteBuffer.getInt(), copy(str.drop(Integer.BYTES)))
    }
  }

  def readUuid(): Option[(UUID, EnrichedByteString)] = {
    for {
      (mostSignificant, afterMost) <- readLong()
      (leastSignificant, afterLeast) <- afterMost.readLong()
    } yield (new UUID(mostSignificant, leastSignificant), afterLeast)
  }

  def readLong(): Option[(Long, EnrichedByteString)] = {
    Option(str).filter { _ =>
      str.size >= java.lang.Long.BYTES
    }.map { _ =>
      (str.toByteBuffer.getLong(), copy(str.drop(java.lang.Long.BYTES)))
    }
  }

}