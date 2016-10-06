package pl.touk.wsr.server.utils


trait BiMap[T, S] {

  def +(key1: T, key2: S): (BiMap[T, S], Option[(T, S)])

  def +(keys: (T, S)): (BiMap[T, S], Option[(T, S)])

  def removeByKey1(key1: T): (BiMap[T, S], Option[S])

  def removeByKey2(key2: S): (BiMap[T, S], Option[T])

  def getByKey1(key: T): Option[S]

  def getByKey2(key: S): Option[T]

  def keys1: Iterable[T]

  def keys2: Iterable[S]
}

object BiMap {
  def empty[T, S]: BiMap[T, S] = new BiMapImpl[T, S]()
}

private class BiMapImpl[T, S](mapByKey1: Map[T, S] = Map.empty[T, S])
  extends BiMap[T, S] {

  val mapByKey2 = mapByKey1.map(_.swap)

  override def +(key1: T, key2: S): (BiMap[T, S], Option[(T, S)]) = {
    if (!mapByKey1.isDefinedAt(key1) && !mapByKey2.isDefinedAt(key2)) {
      val newMapByKey1 = mapByKey1 + ((key1, key2))
      (new BiMapImpl(newMapByKey1), Some((key1, key2)))
    } else {
      (this, None)
    }
  }

  override def +(keys: (T, S)): (BiMap[T, S], Option[(T, S)]) = keys match {
    case (key1, key2) => this + (key1, key2)
  }

  override def removeByKey1(key1: T): (BiMap[T, S], Option[S]) = {
    mapByKey1.get(key1) match {
      case Some(key2) =>
        (new BiMapImpl(mapByKey1 - key1), Some(key2))
      case None =>
        (this, None)
    }
  }

  override def removeByKey2(key2: S): (BiMap[T, S], Option[T]) = {
    mapByKey2.get(key2) match {
      case Some(key1) =>
        (new BiMapImpl(mapByKey1 - key1), Some(key1))
      case None =>
        (this, None)
    }
  }

  override def getByKey1(key: T): Option[S] = mapByKey1.get(key)

  override def getByKey2(key: S): Option[T] = mapByKey2.get(key)

  override def keys1: Iterable[T] = mapByKey1.keys

  override def keys2: Iterable[S] = mapByKey2.keys
}
