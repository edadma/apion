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

  /** Create a file serving middleware
    *
    * @param path
    *   URL path prefix for static files
    * @param root
    *   Root directory to serve files from
    * @param index
    *   Default file to serve for directories
    * @param mimeTypes
    *   Additional MIME type mappings
    * @param fs
    *   Optional filesystem interface (uses RealFS by default)
    */
  def fileServing(
      path: String,
      root: String,
      index: String = "index.html",
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

          val relativePath = request.context.get("*") match {
            case Some(path) => stripSlashes(path.toString)
            case None =>
              logger.debug("[FileServing] No wildcard match found in context")
              ""
          }

          logger.debug(s"[FileServing] Relative path: $relativePath")

          if relativePath.contains("..") then
            logger.debug("[FileServing] Directory traversal attempt detected")
            Future.successful(Response(
              status = 403,
              body = "Forbidden: Directory traversal not allowed",
            ))
          else
            val fullPath = s"$normalizedRoot/$relativePath"
            logger.debug(s"[FileServing] Full path: $fullPath")

            fs.stat(fullPath).toFuture
              .flatMap { stats =>
                if stats.isDirectory() then
                  logger.debug(s"[FileServing] Serving index file for directory")
                  serveFile(s"$fullPath/$index", allMimeTypes, fs)
                else
                  logger.debug(s"[FileServing] Serving file directly")
                  serveFile(fullPath, allMimeTypes, fs)
              }
              .recover {
                case e: Exception =>
                  logger.debug(s"[FileServing] Error: ${e.getMessage}")
                  Response(
                    status = 404,
                    body = s"Not Found: ${e.getMessage}",
                  )
              }
        },
      )
    }

  private def serveFile(
      path: String,
      mimeTypes: Map[String, String],
      fs: FSInterface,
  ): Future[Response] = {
    logger.debug(s"[FileServing] Reading file: $path")
    val extension = path.split('.').lastOption.getOrElse("")
    val mimeType  = mimeTypes.getOrElse(extension, "application/octet-stream")
    logger.debug(s"[FileServing] MIME type: $mimeType for extension: $extension")

    fs.readFile(path, ReadFileOptions()).toFuture.map { content =>
      val strContent = content.toString
      Response(
        status = 200,
        headers = Map("Content-Type" -> mimeType),
        body = strContent,
      )
    }
  }
}
