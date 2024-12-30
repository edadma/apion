package io.github.edadma.apion

import scala.concurrent.Future
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

object LoggingMiddleware:
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
      immediate: Boolean = false,           // Log on request instead of response
      skip: Request => Boolean = _ => false, // Skip logging for certain requests
  )

  private def getToken(token: String, req: Request, startTime: Long, res: Option[Response] = None): String =
    token match
      case ":method" => req.method
      case ":url"    => req.url
      case ":status" => res.map(_.status.toString).getOrElse("-")
      case ":response-time" =>
        if res.isDefined then
          val endTime = System.currentTimeMillis()
          (endTime - startTime).toString
        else "-"
      case ":remote-addr" =>
        req.header("x-forwarded-for")
          .orElse(req.header("x-client-ip"))
          .getOrElse("-")
      case ":remote-user" =>
        req.auth.map(_.user).getOrElse("-")
      case ":date" =>
        val formatter = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
        java.time.ZonedDateTime.now().format(formatter)
      case ":http-version" => "1.1" // Default to HTTP/1.1
      case ":referrer"     => req.header("referer").getOrElse("-")
      case ":user-agent"   => req.header("user-agent").getOrElse("-")
      case token if token.startsWith(":res[") =>
        val header = token.drop(5).dropRight(1)
        res.flatMap(_.headers.get(header)).getOrElse("-")
      case _ => token

  private def formatLog(format: String, req: Request, startTime: Long, res: Option[Response] = None): String =
    // Split format string into tokens and static text
    val tokens = format.split(" ").map { part =>
      if part.startsWith(":") then
        getToken(part, req, startTime, res)
      else
        part
    }
    tokens.mkString(" ")

  /** Creates logging middleware with the specified options
    *
    * @param opts
    *   Options for customizing the logging behavior
    * @return
    *   Handler that logs requests/responses
    */
  def apply(opts: Options = Options()): Handler =
    request => {
      if opts.skip(request) then
        request.skip
      else
        val startTime = System.currentTimeMillis()

        if opts.immediate then
          // Log immediately on request
          logger.info(formatLog(opts.format, request, startTime))
          request.skip
        else
          // Return middleware that logs after response
          Future.successful(Continue(
            request.copy(
              context = request.context +
                ("logging-start-time" -> startTime) +
                ("logging-format"     -> opts.format),
            ),
          ))
    }

  /** Final handler that logs response information Should be added after other middleware
    */
  def finalHandler(): Handler =
    request => {
      // Get stored timing information
      val startTime = request.context.get("logging-start-time").map(_.asInstanceOf[Long])
      val format    = request.context.get("logging-format").map(_.asInstanceOf[String])

      (startTime, format) match
        case (Some(start), Some(fmt)) =>
          // Get response from context
          request.context.get("response") match
            case Some(response: Response) =>
              logger.info(formatLog(fmt, request, start, Some(response)))
              Future.successful(Skip)
            case _ =>
              logger.info(formatLog(fmt, request, start))
              Future.successful(Skip)
        case _ =>
          Future.successful(Skip)
    }

/* Example usage:
server
  .use(LoggingMiddleware(LoggingMiddleware.Options(
    format = LoggingMiddleware.Format.Dev,
    immediate = false,
    skip = req => req.url.startsWith("/health")
  )))
  .use(LoggingMiddleware.finalHandler())
 */
