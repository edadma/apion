package io.github.edadma.apion

import scala.concurrent.Future
import scala.scalajs.js
import scala.util.{Failure, Success}
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global
import io.github.edadma.nodejs.{ReadStreamOptions, Stats}

object StaticMiddleware:
  case class Options(
      index: Boolean = true,       // Whether to serve index.html for directories
      dotfiles: String = "ignore", // How to treat dotfiles (ignore|allow|deny)
      etag: Boolean = true,        // Enable/disable etag generation
      maxAge: Int = 0,             // Cache max-age in seconds
      redirect: Boolean = true,    // Redirect directories to trailing slash
      fallthrough: Boolean = true, // Continue to next handler if file not found
      acceptRanges: Boolean = true,
  )

  private val MimeTypes = Map(
    ".html" -> "text/html",
    ".css"  -> "text/css",
    ".js"   -> "application/javascript",
    ".json" -> "application/json",
    ".png"  -> "image/png",
    ".jpg"  -> "image/jpeg",
    ".gif"  -> "image/gif",
    ".svg"  -> "image/svg+xml",
    ".ico"  -> "image/x-icon",
  )

  /** A byte range specification */
  sealed trait ByteRange {
    def length(totalLength: Double): Double

    def offset(totalLength: Double): Double

    def isValid(totalLength: Double): Boolean
  }

  /** A range specifying a start and optional end position */
  case class RangeFromTo(start: Double, end: Option[Double]) extends ByteRange {
    def length(totalLength: Double): Double =
      end match {
        case Some(endPos) if endPos < totalLength => endPos - start + 1
        case _                                    => totalLength - start
      }

    def offset(totalLength: Double): Double = start

    def isValid(totalLength: Double): Boolean =
      start >= 0 && start < totalLength && end.forall(e => e >= start && e < totalLength)
  }

  case class RangeLastN(n: Double) extends ByteRange {
    def length(totalLength: Double): Double = math.min(n, totalLength)

    def offset(totalLength: Double): Double = math.max(0, totalLength - n)

    def isValid(totalLength: Double): Boolean = n > 0
  }

  /** Range parsing utilities */
  object RangeParser {
    def parseRange(rangeHeader: String): Option[ByteRange] = {
      if (!rangeHeader.startsWith("bytes=")) return None

      val rangeValue = rangeHeader.substring(6)

      rangeValue.split("-", 2) match {
        case Array("", suffixLength) =>
          suffixLength.toDoubleOption.map(n => RangeLastN.apply(n))

        case Array(start, end) =>
          for {
            startPos <- start.toDoubleOption
            endPos   <- if (end.isEmpty) Some(None) else end.toDoubleOption.map(Some(_))
          } yield RangeFromTo(startPos, endPos)

        case _ => None
      }
    }

    def longToDouble(l: Long): Double = l.toDouble

    def unsatisfiableRange(size: Double): String =
      s"bytes */$size"

    def contentRange(range: ByteRange, totalSize: Double): String = {
      val start = range.offset(totalSize)
      val end   = start + range.length(totalSize) - 1
      s"bytes $start-$end/$totalSize"
    }
  }

  /** Creates static file serving middleware
    *
    * @param root
    *   Root directory to serve files from
    * @param options
    *   Configuration options
    * @param fs
    *   File system implementation (defaults to RealFS)
    */
  def apply(
      root: String,
      options: Options = Options(),
      fs: FSInterface = RealFS,
  ): Handler = { request =>
    def handleDirectory(path: String, urlPath: String): Future[Result] =
      if options.redirect && !urlPath.endsWith("/") then
        // Redirect to add trailing slash
        Future.successful(
          Complete(Response(301, ResponseHeaders(Seq("Location" -> s"$urlPath/")))),
        )
      else if options.index then
        // Try to serve index.html
        val indexPath = s"$path/index.html".replaceAll("/+", "/")
        fs.stat(indexPath).toFuture.transformWith {
          case Success(stats)                    => serveFile(indexPath, stats)
          case Failure(_) if options.fallthrough => skip
          case Failure(_)                        => notFound
        }
      else
        Future.successful(Skip)

    def serveFile(path: String, stats: Stats): Future[Result] =
      // Check if file starts with . and handle according to dotfiles option
      val fileName = path.split('/').last
      if fileName.startsWith(".") then
        options.dotfiles match
          case "ignore" => skip
          case "deny"   => "Forbidden".asText(403)
          case "allow"  => sendFile(path, stats)
          case _        => skip
      else
        sendFile(path, stats)

    def sendFile(path: String, stats: Stats): Future[Result] =
      // Get MIME type from extension
      val mimeType = path.split('.').lastOption
        .flatMap(ext => MimeTypes.get(s".$ext"))
        .getOrElse("application/octet-stream")

      // Generate ETag if enabled
      val etag = if options.etag then
        Some(s""""${stats.size}-${stats.mtime.getTime}"""")
      else
        None

      // Check if client cache is valid
      val ifNoneMatch = request.header("if-none-match")
      if etag.exists(e => ifNoneMatch.contains(e)) then
        Future.successful(Complete(Response(304)))
      else
        // Check for range request
        val rangeHeader = request.header("range")
        val ifRange     = request.header("if-range")

        // Validate If-Range header if present
        val validRange = ifRange match {
          case Some(value) =>
            // If-Range can be either ETag or Last-Modified date
            if (value.startsWith("\"")) {
              // ETag comparison
              etag.exists(_ == value)
            } else {
              // Date comparison (simplified - could be more robust)
              true
            }
          case None => true
        }

        if (options.acceptRanges && validRange && rangeHeader.isDefined) {
          // Handle range request
          RangeParser.parseRange(rangeHeader.get) match {
            case Some(range) if range.isValid(stats.size) =>
              // Create read stream for range
              val offset = range.offset(stats.size)
              val length = range.length(stats.size)

              val streamOptions = ReadStreamOptions(start = Some(offset), end = Some(offset + length - 1))
              val stream        = fs.createReadStream(path, streamOptions)
              val headers = ResponseHeaders(Seq(
                "Content-Type"   -> mimeType,
                "Content-Length" -> length.toString,
                "Content-Range"  -> RangeParser.contentRange(range, stats.size),
                "Accept-Ranges"  -> "bytes",
                "Cache-Control"  -> s"max-age=${options.maxAge}",
              ) ++ etag.map("ETag" -> _))

              Future.successful(Complete(Response(206, headers, ReadableStreamBody(stream))))

            case Some(_) =>
              // Range not satisfiable
              Future.successful(Complete(Response(
                416,
                ResponseHeaders(Seq(
                  "Content-Range" -> RangeParser.unsatisfiableRange(stats.size),
                )),
              )))

            case None =>
              // Invalid range header format
              Future.successful(Complete(Response(400)))
          }
        } else {
          // Create read stream
          val stream = fs.createReadStream(path)
          val headers = ResponseHeaders(Seq(
            "Content-Type"   -> mimeType,
            "Content-Length" -> stats.size.toString,
            "Cache-Control"  -> s"max-age=${options.maxAge}",
            "Accept-Ranges"  -> (if options.acceptRanges then "bytes" else "none"),
          ) ++ etag.map("ETag" -> _))

          Future.successful(Complete(Response(200, headers, ReadableStreamBody(stream))))
        }
    end sendFile

    logger.debug(s"static middleware url: ${request.url}, $options")
    logger.debug(s"static middleware path: ${request.path}")
    // Main handler logic
    val rawPath     = request.path
    val decodedPath = decodeURIComponent(rawPath)

    // Prevent directory traversal
    if decodedPath.contains("..") then
      "Forbidden".asText(403)
    else
      // Construct full file path
      val fullPath = s"$root/$decodedPath".replaceAll("/+", "/")
      logger.debug(s"fullPath: $fullPath")

      // Check if path exists and is file/directory
      fs.stat(fullPath).toFuture.transformWith {
        case Success(stats) =>
          if stats.isDirectory() then
            handleDirectory(fullPath, decodedPath)
          else
            serveFile(fullPath, stats)

        case Failure(_) if options.fallthrough =>
          Future.successful(Skip)

        case Failure(_) =>
          logger.debug(s"not found: $fullPath")
          notFound
      }
  }
