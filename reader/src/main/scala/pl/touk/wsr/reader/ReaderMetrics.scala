package pl.touk.wsr.reader

trait ReaderMetricsReporter {

  def reportSequenceStarted(): Unit

  def reportSequenceFinished(): Unit

  def reportError(): Unit

}

class NoOpMetrics extends ReaderMetricsReporter {

  def reportSequenceStarted(): Unit = {}

  def reportSequenceFinished(): Unit = {}

  def reportError(): Unit = {}

}

trait ReaderMetricsMBean {

  def getCompletedSequencesCount: Int

  def getSequencesInProgressCount: Int

  def getErrorsCount: Int

}

class ReaderMetrics
  extends ReaderMetricsReporter
    with ReaderMetricsMBean {

  private var completedSequencesCount = 0
  private var sequencesInProgressCount = 0
  private var errorsCount = 0

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

  def reportError(): Unit = {
    synchronized {
      errorsCount += 1
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

  def getErrorsCount: Int = {
    synchronized {
      errorsCount
    }
  }

}
