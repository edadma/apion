package io.github.edadma.apion

import scala.concurrent.Future
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.Duration

object RateLimiterMiddleware {
  case class RateLimit(
      maxRequests: Int, // Max requests allowed in window
      window: Duration, // Time window (e.g. 1.minute)
      burst: Int = 0,   // Additional burst allowance
  )

  class RateLimiter(limit: RateLimit) {
    private case class WindowData(
        count: Int,
        startTime: Long = System.currentTimeMillis(),
    )

    private val windows = TrieMap[String, WindowData]()

    def checkLimit(ip: String): Boolean = {
      val now      = System.currentTimeMillis()
      val windowMs = limit.window.toMillis

      windows.get(ip) match {
        case Some(WindowData(count, start)) if now - start < windowMs =>
          if (count < limit.maxRequests + limit.burst) {
            windows(ip) = WindowData(count + 1, start)
            true
          } else false

        case _ =>
          windows(ip) = WindowData(1, now)
          true
      }
    }
  }

  def apply(limit: RateLimit): Handler = { request =>
    val limiter = new RateLimiter(limit)

    val clientIP = request.header("x-forwarded-for")
      .orElse(request.header("x-real-ip"))
      .getOrElse(request.rawRequest.socket.remoteAddress)

    if (limiter.checkLimit(clientIP)) {
      Future.successful(Continue(request))
    } else {
      Future.successful(Complete(Response(
        status = 429,
        headers = ResponseHeaders(Seq(
          "Retry-After"       -> limit.window.toSeconds.toString,
          "X-RateLimit-Limit" -> limit.maxRequests.toString,
          "X-RateLimit-Reset" -> (System.currentTimeMillis() + limit.window.toMillis).toString,
        )),
      )))
    }
  }
}
