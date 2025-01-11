package io.github.edadma.apion

import scala.concurrent.Future
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.{DurationInt, Duration}
import zio.json._

object RateLimiterMiddleware {
  // Default configuration (similar to express-rate-limit defaults)
  private val DefaultLimit = RateLimit(
    maxRequests = 60,  // 60 requests
    window = 1.minute, // per minute
    burst = 0,
    headers = true,
  )

  private val DefaultOptions = Options(limit = DefaultLimit)

  def apply(): Handler = apply(DefaultOptions) // No-args constructor using defaults

  // Configuration
  case class RateLimit(
      maxRequests: Int, // Max requests allowed in window
      window: Duration, // Time window
      burst: Int = 0,   // Additional burst allowance
      skipFailedRequests: Boolean = false,
      skipSuccessfulRequests: Boolean = false,
      statusCode: Int = 429, // Status code for rate limit errors
      errorMessage: String = "Too many requests. Please try again later.",
      headers: Boolean = true, // Whether to send rate limit headers
  )

  // Response format for rate limit errors
  case class RateLimitError(
      error: String,
      retryAfter: Long,
      limit: Int,
      remaining: Int,
      reset: Long,
  ) derives JsonEncoder

  // Storage interface for rate limit data
  trait RateLimitStore {
    def increment(key: String, window: Duration): Future[(Int, Int)] // Returns (count, remaining)
    def reset(key: String): Future[Unit]
    def cleanup(): Future[Unit]
  }

  // In-memory implementation
  class InMemoryStore extends RateLimitStore {
    private case class WindowData(
        count: Int,
        startTime: Long = System.currentTimeMillis(),
    )

    // Change from TrieMap to mutable Map
    private val windows = scala.collection.mutable.Map[String, WindowData]()

    def increment(key: String, window: Duration): Future[(Int, Int)] = Future {
      val now      = System.currentTimeMillis()
      val windowMs = window.toMillis

      val data = windows.get(key) match {
        case Some(WindowData(count, start)) if now - start < windowMs =>
          WindowData(count + 1, start)
        case _ =>
          WindowData(1, now)
      }

      windows(key) = data
      (data.count, -1) // -1 indicates remaining not tracked
    }

    def reset(key: String): Future[Unit] = Future {
      windows.remove(key)
    }

    def cleanup(): Future[Unit] = Future {
      val now = System.currentTimeMillis()
      windows.filterInPlace { case (_, WindowData(_, start)) =>
        now - start < 24 * 60 * 60 * 1000 // Remove entries older than 24h
      }
    }
  }

  // IP resolution strategies
  object IpSource extends Enumeration {
    val Direct, Forward, Real, Cloudflare = Value
  }

  case class Options(
      limit: RateLimit,
      store: RateLimitStore = new InMemoryStore,
      ipSources: Seq[IpSource.Value] = Seq(IpSource.Forward, IpSource.Real, IpSource.Direct),
      keyGenerator: Request => Future[String] = defaultKeyGenerator,
      skip: Request => Boolean = _ => false,
      onRateLimited: (Request, RateLimitError, Options) => Future[Response] = defaultErrorHandler,
  )

  private def defaultKeyGenerator(request: Request): Future[String] = Future.successful {
    def getIp(sources: Seq[IpSource.Value]): String = sources match {
      case IpSource.Forward +: rest =>
        request.header("x-forwarded-for")
          .map(_.split(',')(0).trim)
          .getOrElse(getIp(rest))
      case IpSource.Real +: rest =>
        request.header("x-real-ip").getOrElse(getIp(rest))
      case IpSource.Cloudflare +: rest =>
        request.header("cf-connecting-ip").getOrElse(getIp(rest))
      case IpSource.Direct +: rest =>
        request.rawRequest.socket.remoteAddress
      case Nil =>
        throw new Exception("No IP source configured")
    }

    getIp(Seq(IpSource.Forward, IpSource.Real, IpSource.Direct))
  }

  private def defaultErrorHandler(request: Request, error: RateLimitError, options: Options): Future[Response] =
    Future.successful(Response.json(
      error,
      options.limit.statusCode,
      Seq(
        "Retry-After"           -> error.retryAfter.toString,
        "X-RateLimit-Limit"     -> error.limit.toString,
        "X-RateLimit-Remaining" -> "0",
        "X-RateLimit-Reset"     -> (error.reset / 1000).toString,
      ),
    ))

  def apply(options: Options): Handler = { request =>
    if (options.skip(request)) {
      Future.successful(Skip)
    } else {
      for {
        key               <- options.keyGenerator(request)
        (hits, remaining) <- options.store.increment(key, options.limit.window)
        result <- if (hits <= options.limit.maxRequests + options.limit.burst) {
          val now   = System.currentTimeMillis()
          val reset = now + options.limit.window.toMillis

          // Add rate limit headers to successful request
          val rateLimitFinalizer: Finalizer = (req, res) =>
            Future.successful {
              if (!options.limit.headers) res
              else {
                val headers = Seq(
                  "X-RateLimit-Limit"     -> options.limit.maxRequests.toString,
                  "X-RateLimit-Remaining" -> (options.limit.maxRequests - hits).toString,
                  "X-RateLimit-Reset"     -> (reset / 1000).toString,
                  "X-RateLimit-Used"      -> hits.toString,
                )
                res.copy(headers = res.headers.addAll(headers))
              }
            }

          Future.successful(Continue(request.addFinalizer(rateLimitFinalizer)))
        } else {
          val error = RateLimitError(
            error = options.limit.errorMessage,
            retryAfter = options.limit.window.toSeconds,
            limit = options.limit.maxRequests,
            remaining = 0,
            reset = System.currentTimeMillis() + options.limit.window.toMillis,
          )

          options.onRateLimited(request, error, options).map(Complete(_))
        }
      } yield result
    }
  }

  // Convenience methods for common configurations
  def moderate(maxRequests: Int, window: Duration): Handler =
    apply(Options(RateLimit(maxRequests, window)))

  def strict(maxRequests: Int, window: Duration): Handler =
    apply(Options(
      RateLimit(maxRequests, window, burst = 0, skipFailedRequests = false),
    ))

  def flexible(maxRequests: Int, window: Duration): Handler =
    apply(Options(
      RateLimit(maxRequests, window, burst = maxRequests / 2, skipFailedRequests = true),
    ))
}
