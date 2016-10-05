package pl.touk.wsr.reader

trait ReaderMetricsReporter {

  def reportSequenceStarted(): Unit

  def reportSequenceFinished(): Unit

}

class NoOpMetrics extends ReaderMetricsReporter {

  def reportSequenceStarted(): Unit = {}

  def reportSequenceFinished(): Unit = {}

}

trait ReaderMetricsMBean {

  def getCompletedSequencesCount: Int

  def getSequencesInProgressCount: Int

}

class ReaderMetrics
  extends ReaderMetricsReporter
    with ReaderMetricsMBean {

  private var completedSequencesCount = 0
  private var sequencesInProgressCount = 0

  def reportSequenceStarted(): Unit = {
    synchronized {
      sequencesInProgressCount += 1
    }
  }

  def reportSequenceFinished(): Unit = {
    synchronized {
      sequencesInProgressCount -= 1
      completedSequencesCount += 1
    }
  }

  def getCompletedSequencesCount: Int = {
    synchronized {
      completedSequencesCount
    }
  }

  def getSequencesInProgressCount: Int = {
    synchronized {
      sequencesInProgressCount
    }
  }

}
