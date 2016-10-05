package pl.touk.wsr.server.receiver

import pl.touk.wsr.protocol.wrtsrv.RequestForNumbers
import pl.touk.wsr.transport.WsrServerSender

trait SequenceRequester {
  def request(size: Int, offset: Int): Unit
}

class SimpleSequenceRequester(server: WsrServerSender) extends SequenceRequester {
  override def request(size: Int, offset: Int): Unit =
    server.send(RequestForNumbers(offset, size))
}
