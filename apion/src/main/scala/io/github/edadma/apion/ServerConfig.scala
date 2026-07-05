package io.github.edadma.apion

/** Per-server configuration.
  *
  * Everything here used to live in global mutable state (`Response`'s default-header
  * `var`, `Request.maxBodySize`/`bodyTimeout` `var`s), which meant two servers — or
  * two tests — in one process silently shared it. It now travels with the [[Server]]
  * that owns it: default headers are stamped on the way out via [[applyDefaults]],
  * and body limits are seeded into each request's context via [[seedContext]].
  */
case class ServerConfig(
    defaultHeaders: Seq[(String, String)] = ServerConfig.DefaultHeaders,
    maxBodySize: Long = Request.maxBodySize,
    bodyTimeout: Int = Request.bodyTimeout,
):
  /** Stamp this server's default headers (plus a current `Date`) onto a response,
    * without overwriting any header the response has already set.
    */
  def applyDefaults(response: Response): Response =
    val withDefaults = defaultHeaders.foldLeft(response.headers) { (headers, header) =>
      if headers.contains(header._1) then headers else headers.add(header._1, header._2)
    }
    val withDate =
      if withDefaults.contains("Date") then withDefaults
      else withDefaults.add("Date", ServerConfig.httpDate())
    response.copy(headers = withDate)

  /** Seed a request context with this server's body limits, so `Request.body`
    * enforces them. Route-scoped middleware (e.g. BodyLimitMiddleware) can still
    * override them further down the pipeline.
    */
  def seedContext(context: Context): Context =
    context
      .updated(Request.maxBodySizeKey, maxBodySize)
      .updated(Request.bodyTimeoutKey, bodyTimeout)

object ServerConfig:
  /** The out-of-the-box default response headers. */
  val DefaultHeaders: Seq[(String, String)] = Seq(
    "Server"        -> "Apion",
    "Cache-Control" -> "no-store, no-cache, must-revalidate, max-age=0",
    "Pragma"        -> "no-cache",
    "Expires"       -> "0",
    "X-Powered-By"  -> "Apion",
  )

  /** Format the current time as an RFC 1123 HTTP date. */
  def httpDate(): String =
    import java.time.{ZonedDateTime, ZoneOffset}
    import java.time.format.DateTimeFormatter
    import java.util.Locale

    DateTimeFormatter.RFC_1123_DATE_TIME
      .withLocale(Locale.CANADA)
      .withZone(ZoneOffset.UTC)
      .format(ZonedDateTime.now(ZoneOffset.UTC))
