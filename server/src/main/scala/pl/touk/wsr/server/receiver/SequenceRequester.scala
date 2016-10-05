package pl.touk.wsr.server.receiver

import pl.touk.wsr.transport.WsrServer

trait SequenceRequester {
  def request(size: Int, offset: Int): Unit
}

class SimpleSequenceRequester(server: WsrServer) extends SequenceRequester {
  override def request(size: Int, offset: Int): Unit =
    server.send(size, offset)
}
