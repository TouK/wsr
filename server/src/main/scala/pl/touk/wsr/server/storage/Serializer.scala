package pl.touk.wsr.server.storage

import java.io._

import scala.io.Source
import scala.pickling.Defaults._
import scala.pickling.json._
import scala.util.Try

class Serializer(path: String) {

  def serialize(sequence: Seq[Int]): Try[Unit] = Try {
    val bw = new BufferedWriter(new FileWriter(new File(path)))
    bw.write(sequence.pickle.value)
    bw.close()
  }

  def deserialize(): Try[Seq[Int]] = Try {
    val content = Source.fromFile(path).getLines.mkString
    JSONPickle(content).unpickle[Seq[Int]]
  }
}
