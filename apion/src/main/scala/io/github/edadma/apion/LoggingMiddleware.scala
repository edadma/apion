package io.github.edadma.apion

import scala.concurrent.Future
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

object LoggingMiddleware:
  // Public interface for formatting logs (needed for testing)
  def formatRequestLog(format: String, req: Request, startTime: Long, res: Response): String =
    logger.debug(s"Formatting log with response headers: ${res.headers}") // Debug headers
    formatLog(format, req, startTime, Some(res))

  // Predefined formats similar to morgan
  object Format:
    val Combined =
      ":remote-addr - :remote-user [:date] \":method :url HTTP/:http-version\" :status :res[content-length] \":referrer\" \":user-agent\""
    val Common = ":remote-addr - :remote-user [:date] \":method :url HTTP/:http-version\" :status :res[content-length]"
    val Dev    = ":method :url :status :response-time ms - :res[content-length]"
    val Short =
      ":remote-addr :remote-user :method :url HTTP/:http-version :status :res[content-length] - :response-time ms"
    val Tiny = ":method :url :status :res[content-length] - :response-time ms"

  case class Options(
      format: String = Format.Dev,
      immediate: Boolean = false,
      skip: Request => Boolean = _ => false,
      handler: String => Unit = println,
      debug: Boolean = false,
  )

  private def getToken(token: String, req: Request, startTime: Long, res: Option[Response] = None): String =
    token match
      case ":method" => req.method
      case ":url"    => req.url
      case ":status" => res.map(_.status.toString).getOrElse("-")
      case ":response-time" =>
        val endTime = System.currentTimeMillis()
        (endTime - startTime).toString
      case ":remote-addr" =>
        req.header("x-forwarded-for")
          .orElse(req.header("x-client-ip"))
          .getOrElse("-")
      case ":remote-user" => "-"
//        req.auth.map(_.user).getOrElse("-")
      case ":date" =>
        val formatter = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
        java.time.ZonedDateTime.now().format(formatter)
      case ":http-version" => "1.1"
      case ":referrer"     => req.header("referer").getOrElse("-")
      case ":user-agent"   => req.header("user-agent").getOrElse("-")
      case token if token.startsWith(":res[") =>
        val header = token.drop(5).dropRight(1).toLowerCase
        if header == "content-length" then
          res.map(r =>
            logger.debug(s"Looking up content-length header in: ${r.headers}")
            r.headers.get("content-length").getOrElse("-"),
          ).getOrElse("-")
        else
          res.flatMap(_.headers.get(header)).getOrElse("-")
      case _ => token

  private def formatLog(format: String, req: Request, startTime: Long, res: Option[Response] = None): String =
    // Split by quotes first to handle quoted sections properly
    val segments = format.split("\"")
    val formattedSegments = segments.zipWithIndex.map { case (segment, index) =>
      if index % 2 == 0 then
        // Regular (unquoted) segment
        segment.split(" ").map { part =>
          if part.startsWith(":") then
            logger.debug(s"Processing token: $part")
            val value = getToken(part, req, startTime, res)
            logger.debug(s"Token $part value: $value")
            value
          else
            part
        }.mkString(" ")
      else
        // Quoted segment - process tokens but maintain quotes
        "\"" + segment.split(" ").map { part =>
          if part.startsWith(":") then
            logger.debug(s"Processing token: $part")
            val value = getToken(part, req, startTime, res)
            logger.debug(s"Token $part value: $value")
            value
          else
            part
        }.mkString(" ") + "\""
    }
    formattedSegments.mkString("")

  def apply(opts: Options = Options()): Handler = request =>
    val debug = (msg: String) => if opts.debug then logger.debug(msg)

    if opts.skip(request) then
      debug("Skipping logging due to skip option")
      Future.successful(Continue(request))
    else
      val startTime = System.currentTimeMillis()
      debug(s"Starting request handling at $startTime")

      if opts.immediate then
        val logMsg = formatLog(opts.format, request, startTime)
        debug(s"Immediate logging: $logMsg")
        opts.handler(logMsg)
        Future.successful(Continue(request))
      else
        // Store timing info in request context
        val reqWithTiming = request.copy(
          context = request.context +
            ("logging-start-time" -> startTime) +
            ("logging-format"     -> opts.format) +
            ("logging-handler"    -> opts.handler),
        )
        debug("Added timing info to request context")

        val loggingFinalizer: Finalizer = (req, res) => {
          val logMsg = formatRequestLog(opts.format, req, startTime, res)
          debug(s"Finalizer logging: $logMsg")
          opts.handler(logMsg)
          Future.successful(res)
        }

        Future.successful(Continue(reqWithTiming.addFinalizer(loggingFinalizer)))
