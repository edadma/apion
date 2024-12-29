package io.github.edadma.apion

import io.github.edadma.nodejs.*
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.concurrent.ExecutionContext.Implicits.global
import java.nio.file.{Path, Paths}
import scala.util.{Success, Failure}

object Middlewares {
  private def stripSlashes(str: String): String =
    str.stripPrefix("/").stripSuffix("/")

  /** Default MIME types for common file extensions */
  private val defaultMimeTypes = Map(
    "html"  -> "text/html",
    "htm"   -> "text/html",
    "css"   -> "text/css",
    "js"    -> "application/javascript",
    "json"  -> "application/json",
    "txt"   -> "text/plain",
    "png"   -> "image/png",
    "jpg"   -> "image/jpeg",
    "jpeg"  -> "image/jpeg",
    "gif"   -> "image/gif",
    "svg"   -> "image/svg+xml",
    "ico"   -> "image/x-icon",
    "pdf"   -> "application/pdf",
    "zip"   -> "application/zip",
    "woff"  -> "font/woff",
    "woff2" -> "font/woff2",
    "ttf"   -> "font/ttf",
    "eot"   -> "application/vnd.ms-fontobject",
    "mp4"   -> "video/mp4",
    "webm"  -> "video/webm",
    "mp3"   -> "audio/mpeg",
    "wav"   -> "audio/wav",
  )

  /** CORS (Cross-Origin Resource Sharing) Middleware
    *
    * @param allowOrigin
    *   Allowed origins (default allows all)
    * @param allowMethods
    *   Allowed HTTP methods
    * @param allowHeaders
    *   Allowed headers
    */
  def cors(
      allowOrigin: String = "*",
      allowMethods: Set[String] = Set("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"),
      allowHeaders: Set[String] = Set("Content-Type", "Authorization"),
  ): Middleware =
    endpoint =>
      request => {
        // Handle preflight requests
        if request.method == "OPTIONS" then
          Future.successful(Response(
            status = 204,
            headers = Map(
              "Access-Control-Allow-Origin"  -> allowOrigin,
              "Access-Control-Allow-Methods" -> allowMethods.mkString(", "),
              "Access-Control-Allow-Headers" -> allowHeaders.mkString(", "),
              "Access-Control-Max-Age"       -> "86400",
            ),
            body = "",
          ))
        else
          endpoint(request).map { response =>
            response.copy(
              headers = response.headers ++ Map(
                "Access-Control-Allow-Origin"  -> allowOrigin,
                "Access-Control-Allow-Methods" -> allowMethods.mkString(", "),
                "Access-Control-Allow-Headers" -> allowHeaders.mkString(", "),
              ),
            )
          }
      }

  /** Security Headers Middleware Adds common security-related HTTP headers
    */
  def securityHeaders(): Middleware =
    endpoint =>
      request => {
        endpoint(request).map { response =>
          response.copy(
            headers = response.headers ++ Map(
              "X-Content-Type-Options"    -> "nosniff",
              "X-Frame-Options"           -> "DENY",
              "X-XSS-Protection"          -> "1; mode=block",
              "Referrer-Policy"           -> "no-referrer",
              "Strict-Transport-Security" -> "max-age=31536000; includeSubDomains",
            ),
          )
        }
      }

  /** Request Logging Middleware Logs incoming requests with method, URL, and headers
    */
  def requestLogger(): Middleware =
    endpoint =>
      request => {
        logger.info(s"[Request] ${request.method} ${request.url}")
        request.headers.foreach { case (key, value) =>
          logger.debug(s"[Request Header] $key: $value")
        }

        val start = System.currentTimeMillis()

        endpoint(request).map { response =>
          val duration = System.currentTimeMillis() - start
          logger.info(s"[Response] ${request.method} ${request.url} - ${response.status} (${duration}ms)")
          response
        }
      }

  /** Error Handling Middleware Provides consistent error responses
    */
  def errorHandler(): Middleware =
    endpoint =>
      request => {
        endpoint(request).recover {
          case e: Exception =>
            logger.error(s"Unhandled error: ${e.getMessage}")
            e.printStackTrace() // This will print stack trace to console
            Response(
              status = 500,
              headers = Map("Content-Type" -> "application/json"),
              body = s"""{"error": "Internal Server Error", "message": "${e.getMessage}"}""",
            )
        }
      }

  /** Content Negotiation Middleware Ensures consistent Content-Type handling
    */
  def contentNegotiation(): Middleware =
    endpoint =>
      request => {
        endpoint(request).map { response =>
          // Ensure Content-Type is always set
          val headersWithContentType =
            if !response.headers.contains("Content-Type") then
              response.headers + ("Content-Type" -> "application/json")
            else
              response.headers

          response.copy(headers = headersWithContentType)
        }
      }

  case class FileServingOptions(
      dotFiles: String = "ignore", // "allow" | "deny" | "ignore"
      etag: Boolean = true,
      fallthrough: Boolean = true,
      index: String = "index.html",
      maxAge: Long = 0,
      lastModified: Boolean = true,
  )

  /** Create a file serving middleware
    *
    * @param path
    *   URL path prefix for static files
    * @param root
    *   Root directory to serve files from
    * @param options
    *   Configuration options for file serving
    * @param mimeTypes
    *   Additional MIME type mappings
    * @param fs
    *   Optional filesystem interface (uses RealFS by default)
    */
  def fileServing(
      path: String,
      root: String,
      options: FileServingOptions = FileServingOptions(),
      mimeTypes: Map[String, String] = Map(),
      fs: FSInterface = RealFS,
  ): Router => Router =
    router => {
      val allMimeTypes   = defaultMimeTypes ++ mimeTypes
      val normalizedPath = stripSlashes(path)
      val normalizedRoot = stripSlashes(root)

      logger.debug(s"[FileServing] Setting up file serving for path: /$normalizedPath")
      logger.debug(s"[FileServing] Root directory: $normalizedRoot")

      router.get(
        s"/$normalizedPath/*",
        request => {
          logger.debug(s"[FileServing] Handling request: ${request.url}")

          // Extract full path after the static prefix
          val prefix = s"/$normalizedPath/"
          val relativePath = if request.url.startsWith(prefix) then
            request.url.substring(prefix.length)
          else
            ""

          logger.debug(s"[FileServing] Raw relative path: $relativePath")

          // Check for directory traversal in both original URL and relative path
          if request.url.contains("..") || relativePath.contains("..") then
            logger.debug("[FileServing] Directory traversal attempt detected")
            Future.successful(Response(
              status = 403,
              body = "Forbidden: Directory traversal not allowed",
            ))
          else
            val normalizedRelPath = stripSlashes(relativePath)
            logger.debug(s"[FileServing] Normalized relative path: $normalizedRelPath")

            // Check dotfile handling
            if normalizedRelPath.startsWith(".") then
              options.dotFiles match {
                case "allow" =>
                  val fullPath = s"$normalizedRoot/$normalizedRelPath"
                  handleFileRequest(fullPath, fs, options, allMimeTypes, request, normalizedRoot)
                case "deny" =>
                  Future.successful(Response(
                    status = 403,
                    body = "Forbidden: Access to dotfile denied",
                  ))
                case _ => // "ignore" - act like file doesn't exist
                  Future.successful(Response(status = 404, body = "Not Found"))
              }
            else
              val fullPath = s"$normalizedRoot/$normalizedRelPath"
              handleFileRequest(fullPath, fs, options, allMimeTypes, request, normalizedRelPath)
        },
      )
    }

  private def handleFileRequest(
      fullPath: String,
      fs: FSInterface,
      options: FileServingOptions,
      allMimeTypes: Map[String, String],
      request: Request,
      normalizedPath: String,
  ): Future[Response] = {
    fs.stat(fullPath).toFuture.flatMap { stats =>
      val ifNoneMatch = request.header("if-none-match")
      val ifModifiedSince = request.header("if-modified-since").flatMap { date =>
        try Some(new js.Date(date).getTime)
        catch case _: Exception => None
      }

      val etag = if options.etag then
        Some(s"""W/"${stats.size}-${stats.mtime.getTime}"""")
      else None

      if etag.exists(tag => ifNoneMatch.contains(tag)) ||
        ifModifiedSince.exists(_ >= stats.mtime.getTime)
      then
        Future.successful(Response(status = 304, body = ""))
      else if stats.isDirectory() then
        logger.debug(s"[FileServing] Serving index file for directory")
        // Try directory index first
        val dirIndexPath = s"$fullPath/${options.index}"
        logger.debug(s"[FileServing] Trying directory index at: $dirIndexPath")

        serveFile(dirIndexPath, allMimeTypes, fs, options).recoverWith {
          case _: Exception =>
            logger.debug(s"[FileServing] Directory index not found, trying root index")
            val rootIndexPath = s"public/${options.index}" // Use root directory
            logger.debug(s"[FileServing] Trying root index at: $rootIndexPath")
            serveFile(rootIndexPath, allMimeTypes, fs, options)
        }
      else
        serveFile(fullPath, allMimeTypes, fs, options)
    }.recover {
      case e: Exception =>
        logger.debug(s"[FileServing] Error: ${e.getMessage}")
        Response(status = 404, body = "Not Found")
    }
  }

  private def serveFile(
      path: String,
      mimeTypes: Map[String, String],
      fs: FSInterface,
      options: FileServingOptions,
  ): Future[Response] = {
    logger.debug(s"[FileServing] Reading file: $path")
    val extension = path.split('.').lastOption.getOrElse("")
    val mimeType  = mimeTypes.getOrElse(extension, "application/octet-stream")
    logger.debug(s"[FileServing] MIME type: $mimeType for extension: $extension")

    fs.readFile(path, ReadFileOptions()).toFuture.flatMap { content =>
      fs.stat(path).toFuture.map { stats =>
        val headers = collection.mutable.Map[String, String](
          "Content-Type" -> mimeType,
        )

        // Add caching headers if enabled
        if options.lastModified then
          headers("Last-Modified") = stats.mtime.toUTCString()

        if options.etag then
          headers("ETag") = s"""W/"${stats.size}-${stats.mtime.getTime}""""

        if options.maxAge > 0 then
          headers("Cache-Control") = s"public, max-age=${options.maxAge}"

        Response(
          status = 200,
          headers = headers.toMap,
          body = content.toString,
        )
      }
    }
  }
}
