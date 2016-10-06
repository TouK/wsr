package pl.touk.wsr.reader

trait ReaderMetricsReporter {

  def reportSequenceStarted(): Unit

  def reportSequenceFinished(): Unit

  def reportOutOfOrderOldError(): Unit

  def reportOutOfOrderDuplicateError(): Unit

  def reportOutOfOrderSkipError(): Unit

  def reportUnknownSequenceValueError(): Unit

  def reportUnknownSequenceEndError(): Unit

  def reportConnectionLostError(): Unit

}

class NoOpMetrics extends ReaderMetricsReporter {

  def reportSequenceStarted(): Unit = {}

  def reportSequenceFinished(): Unit = {}

  def reportOutOfOrderOldError(): Unit = {}

  def reportOutOfOrderDuplicateError(): Unit = {}

  def reportOutOfOrderSkipError(): Unit = {}

  def reportUnknownSequenceValueError(): Unit = {}

  def reportUnknownSequenceEndError(): Unit = {}

  def reportConnectionLostError(): Unit = {}

}

trait ReaderMetricsMBean {

  def getCompletedSequencesCount: Int

  def getSequencesInProgressCount: Int

  def getOutOfOrderOldErrorsCount: Int

  def getOutOfOrderDuplicateErrorsCount: Int

  def getOutOfOrderSkipErrorsCount: Int

  def getUnknownSequenceValueErrorsCount: Int

  def getUnknownSequenceEndErrorsCount: Int

  def getConnectionLostErrorsCount: Int

}

class ReaderMetrics
  extends ReaderMetricsReporter
    with ReaderMetricsMBean {

  private var completedSequencesCount = 0
  private var sequencesInProgressCount = 0
  @volatile private var outOfOrderOldErrorsCount = 0
  @volatile private var outOfOrderDuplicateErrorsCount = 0
  @volatile private var outOfOrderSkipErrorsCount = 0
  @volatile private var unknownSequenceValueErrorsCount = 0
  @volatile private var unknownSequenceEndErrorsCount = 0
  @volatile private var connectionLostErrorsCount = 0

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

  def reportOutOfOrderOldError(): Unit = {
    outOfOrderOldErrorsCount += 1
  }

  def reportOutOfOrderDuplicateError(): Unit = {
    outOfOrderDuplicateErrorsCount += 1
  }

  def reportOutOfOrderSkipError(): Unit = {
    outOfOrderSkipErrorsCount += 1
  }

  def reportUnknownSequenceValueError(): Unit = {
    unknownSequenceValueErrorsCount += 1
  }

  def reportUnknownSequenceEndError(): Unit = {
    unknownSequenceEndErrorsCount += 1
  }

  def reportConnectionLostError(): Unit = {
    connectionLostErrorsCount += 1
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

  def getOutOfOrderOldErrorsCount: Int = {
    outOfOrderOldErrorsCount
  }

  def getOutOfOrderDuplicateErrorsCount: Int = {
    outOfOrderDuplicateErrorsCount
  }

  def getOutOfOrderSkipErrorsCount: Int = {
    outOfOrderSkipErrorsCount
  }

  def getUnknownSequenceValueErrorsCount: Int = {
    unknownSequenceValueErrorsCount
  }

  def getUnknownSequenceEndErrorsCount: Int = {
    unknownSequenceEndErrorsCount
  }

  def getConnectionLostErrorsCount: Int = {
    connectionLostErrorsCount
  }

}
