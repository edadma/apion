package io.github.edadma.apion

import scala.concurrent.Future
import scala.scalajs.js
import scala.util.{Success, Failure}
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global
import scala.scalajs.js.typedarray.Uint8Array
import io.github.edadma.nodejs.{ReadFileOptions, Stats}

object StaticMiddleware:
  case class StaticOptions(
      index: Boolean = true,       // Whether to serve index.html for directories
      dotfiles: String = "ignore", // How to treat dotfiles (ignore|allow|deny)
      etag: Boolean = true,        // Enable/disable etag generation
      maxAge: Int = 0,             // Cache max-age in seconds
      redirect: Boolean = true,    // Redirect directories to trailing slash
      fallthrough: Boolean = true, // Continue to next handler if file not found
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
      options: StaticOptions = StaticOptions(),
      fs: FSInterface = RealFS,
  ): Handler = { request =>
    def handleDirectory(path: String, urlPath: String): Future[Result] =
      if options.redirect && !urlPath.endsWith("/") then
        // Redirect to add trailing slash
        Future.successful(
          Complete(Response(301, Map("Location" -> s"$urlPath/"), "")),
        )
      else if options.index then
        // Try to serve index.html
        val indexPath = s"$path/index.html".replaceAll("/+", "/")
        fs.stat(indexPath).toFuture.transformWith {
          case Success(stats) =>
            serveFile(indexPath, stats)
          case Failure(_) if options.fallthrough =>
            Future.successful(Skip)
          case Failure(_) =>
            Future.successful(Complete(Response(404, body = "Not Found")))
        }
      else
        Future.successful(Skip)

    def serveFile(path: String, stats: Stats): Future[Result] =
      // Check if file starts with . and handle according to dotfiles option
      val fileName = path.split('/').last
      if fileName.startsWith(".") then
        options.dotfiles match
          case "ignore" => Future.successful(Skip)
          case "deny"   => Future.successful(Complete(Response(403, body = "Forbidden")))
          case _        => sendFile(path, stats)
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
        Future.successful(Complete(Response(304, body = "")))
      else
        // Read and send file
        fs.readFile(path).toFuture
          .map { content =>
            val headers = Map(
              "Content-Type"   -> mimeType,
              "Content-Length" -> stats.size.toString,
              "Cache-Control"  -> s"max-age=${options.maxAge}",
            ) ++ etag.map("ETag" -> _)

            Complete(Response(200, headers, content.toString))
          }

    // Main handler logic
    val rawPath     = request.url.split('?')(0)
    val decodedPath = decodeURIComponent(rawPath)

    // Prevent directory traversal
    if decodedPath.contains("..") then
      Future.successful(Complete(Response(403, body = "Forbidden")))
    else
      // Construct full file path
      val fullPath = s"$root/$decodedPath".replaceAll("/+", "/")

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
          Future.successful(Complete(Response(404, body = "Not Found")))
      }
  }