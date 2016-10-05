package pl.touk.wsr.server

trait ServerMetricsReporter {

  def reportNumberReceived(): Unit

  def reportNumberSent(): Unit

  def reportError(): Unit

}

class NoOpMetrics extends ServerMetricsReporter {

  def reportNumberReceived(): Unit = {}

  def reportNumberSent(): Unit = {}

  def reportError(): Unit = {}

}

trait ServerMetricsMBean {

  def getReceivedNumbersCount: Int

  def getSentNumbersCount: Int

  def getErrorsCount: Int

}

class ServerMetrics
  extends ServerMetricsReporter
    with ServerMetricsMBean {

  @volatile private var receivedNumbersCount = 0
  @volatile private var sentNumbersCount = 0
  @volatile private var errorsCount = 0

  def reportNumberReceived(): Unit = {
    receivedNumbersCount += 1
  }

  def reportNumberSent(): Unit = {
    sentNumbersCount += 1
  }

  def reportError(): Unit = {
    errorsCount += 1
  }

  def getReceivedNumbersCount: Int = {
    receivedNumbersCount
  }

  def getSentNumbersCount: Int = {
    sentNumbersCount
  }

  def getErrorsCount: Int = {
    errorsCount
  }

}
