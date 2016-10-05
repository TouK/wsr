package pl.touk.wsr.writer

trait WriterMetricsReporter {

  def reportRequestStarted(): Unit

  def reportRequestFinished(): Unit

}

class NoOpMetrics extends WriterMetricsReporter {

  def reportRequestStarted(): Unit = {}

  def reportRequestFinished(): Unit = {}

}

trait WriterMetricsMBean {

  def getCompletedRequestsCount: Int

  def getRequestsInProgressCount: Int

}

class WriterMetrics
  extends WriterMetricsReporter
    with WriterMetricsMBean {

  private var completedRequestsCount = 0
  private var requestsInProgressCount = 0

  def reportRequestStarted(): Unit = {
    synchronized {
      requestsInProgressCount += 1
    }
  }

  def reportRequestFinished(): Unit = {
    synchronized {
      requestsInProgressCount -= 1
      completedRequestsCount += 1
    }
  }

  def getCompletedRequestsCount: Int = {
    synchronized {
      completedRequestsCount
    }
  }

  def getRequestsInProgressCount: Int = {
    synchronized {
      requestsInProgressCount
    }
  }

}
