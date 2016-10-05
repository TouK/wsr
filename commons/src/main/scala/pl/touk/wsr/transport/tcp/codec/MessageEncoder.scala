package pl.touk.wsr.transport.tcp.codec

import akka.util.{ByteString, ByteStringBuilder}

class MessageEncoder[T](build: T => ByteStringBuilder) {

  def encode(message: T): ByteString =
    build(message).result()

}