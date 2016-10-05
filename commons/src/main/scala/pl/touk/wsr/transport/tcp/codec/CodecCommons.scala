package pl.touk.wsr.transport.tcp.codec

import java.nio.ByteOrder
import java.util.UUID

import akka.util.ByteStringBuilder

trait CodecCommons {

  protected implicit val byteOrder = ByteOrder.BIG_ENDIAN

  protected def encodeUuid(builder: ByteStringBuilder, seqId: UUID): builder.type = {
    builder.putLong(seqId.getMostSignificantBits)
    builder.putLong(seqId.getLeastSignificantBits)
  }

}