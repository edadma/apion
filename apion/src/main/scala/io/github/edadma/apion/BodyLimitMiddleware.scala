package io.github.edadma.apion

import scala.concurrent.Future

/** Middleware to set body size limits for specific routes. */
object BodyLimitMiddleware:

  /**
   * Create middleware that sets the maximum body size for requests passing through.
   * @param maxBytes maximum body size in bytes
   */
  def apply(maxBytes: Long): Handler = request =>
    Future.successful(Continue(request.copy(
      context = request.context.updated(Request.maxBodySizeKey, maxBytes),
    )))

  /**
   * Create middleware that sets both body size limit and read timeout.
   * @param maxBytes maximum body size in bytes
   * @param timeoutMs body read timeout in milliseconds
   */
  def apply(maxBytes: Long, timeoutMs: Int): Handler = request =>
    Future.successful(Continue(request.copy(
      context = request.context
        .updated(Request.maxBodySizeKey, maxBytes)
        .updated(Request.bodyTimeoutKey, timeoutMs),
    )))
