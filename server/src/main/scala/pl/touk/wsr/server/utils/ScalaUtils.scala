package pl.touk.wsr.server.utils

object ScalaUtils {

  implicit class SeqOps[T](seq: Seq[T]) {
    def findWithIndex(predicate: T => Boolean): Option[(T, Int)] = {
      seq.indexWhere(predicate) match {
        case -1 => None
        case idx => Some(seq(idx), idx)
      }
    }

    def findAndUpdateWithReturningOld(predicate: T => Boolean, update: T => T): (Seq[T], Option[T]) = {
      seq.findWithIndex(predicate)
        .map { case (found, idx) => (seq.updated(idx, update(found)), Some(found)) }
        .getOrElse((seq, None))
    }

    def findAndUpdate(predicate: T => Boolean, update: T => T): Seq[T] = {
      findAndUpdateWithReturningOld(predicate, update)._1
    }
  }
}
