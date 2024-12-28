package io.github.edadma.apion

import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.time.{Span, Seconds, Millis}
import org.scalatest.exceptions.TestFailedException
import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatest.freespec.{AnyFreeSpec, AsyncFreeSpec}
import org.scalatest.matchers.should.Matchers

import io.github.edadma.logger.{FileHandler, LogLevel}

import scala.scalajs.js

import scala.concurrent.{ExecutionContext, Future, Promise}

trait BaseSpec extends Matchers with BeforeAndAfterEach { this: Suite =>
  override def beforeEach(): Unit = {
    super.beforeEach()

    logger.setLogLevel(LogLevel.OFF)
    logger.resetOpId()
  }
}

trait JSEventually extends PatienceConfiguration {
  def eventually[T](block: => T)(implicit config: PatienceConfig): Future[T] = {
    val promise               = Promise[T]()
    val startTime             = System.currentTimeMillis
    val timeoutMillis         = config.timeout.toMillis
    val initialIntervalMillis = config.interval.toMillis

    def nextInterval(currentInterval: Double): Double = {
      val nextDouble = currentInterval * 2

      math.min(nextDouble, initialIntervalMillis.toDouble)
    }

    def attempt(currentInterval: Double): Unit = {
      try {
        val result = block
        promise.success(result)
      } catch {
        case e: Throwable =>
          val elapsedTime = System.currentTimeMillis - startTime

          if (elapsedTime >= timeoutMillis) {
            promise.failure(new TestFailedException(
              s"Eventually block timed out after ${timeoutMillis}ms. Last error: ${e.getMessage}",
              e,
              10,
            ))
          } else {
            js.timers.setTimeout(currentInterval) {
              attempt(nextInterval(currentInterval))
            }
          }
      }
    }

    attempt((initialIntervalMillis / 10).toDouble)
    promise.future
  }
}

// Base class for async DOM tests
class AsyncBaseSpec extends AsyncFreeSpec with BaseSpec with JSEventually {

  // Use JS execution context for all async operations
  import scala.scalajs.concurrent.JSExecutionContext
  implicit override def executionContext: ExecutionContext = JSExecutionContext.queue

  // Provide reasonable default patience config
  implicit override val patienceConfig: PatienceConfig = PatienceConfig(
    timeout = Span(200, Millis),
    interval = Span(50, Millis),
  )

  def withDebugLogging[T](testName: String)(test: => Future[T]): Future[T] = {
    logger.setLogLevel(LogLevel.DEBUG)
    logger.setHandler(new FileHandler("log"))
    logger.debug(s"<<<< Starting Test: $testName >>>>", category = "Test")

    test transform { result =>
      logger.setLogLevel(LogLevel.OFF)
      result
    }
  }

  def after[T](millis: Int)(block: => T): Future[T] = {
    val promise = Promise[T]()

    js.timers.setTimeout(millis) {
      try {
        val result = block
        promise.success(result)
      } catch {
        case e: Throwable =>
          promise.failure(e)
      }
    }

    promise.future
  }
}

class AnyBaseSpec extends AnyFreeSpec with BaseSpec:
  def withDebugLogging(testName: String)(test: => Unit): Unit = {
    logger.setLogLevel(LogLevel.DEBUG)
    logger.setHandler(new FileHandler("log"))
    logger.debug(s"<<<< Starting Test: $testName >>>>", category = "Test")

    try {
      test
    } finally {
      logger.setLogLevel(LogLevel.OFF)
    }
  }
