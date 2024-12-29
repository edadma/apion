package io.github.edadma.apion

import io.github.edadma.nodejs.*
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.concurrent.ExecutionContext.Implicits.global
import java.nio.file.{Path, Paths}
import scala.util.{Success, Failure}

object Middlewares:
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
    */
  def fileServing(
      path: String,
      root: String,
      index: String = "index.html",
      mimeTypes: Map[String, String] = Map(),
  ): Middleware =
    val allMimeTypes   = defaultMimeTypes ++ mimeTypes
    val normalizedPath = path.stripPrefix("/").stripSuffix("/")
    val normalizedRoot = root.stripSuffix("/")

    endpoint =>
      request => {
        if !request.url.startsWith(s"/$normalizedPath/") then
          endpoint(request)
        else
          // Extract the file path from the URL
          val relativePath = request.url.stripPrefix(s"/$normalizedPath/")

          // Prevent directory traversal attacks
          if relativePath.contains("..") then
            Future.successful(Response(
              status = 403,
              body = "Forbidden: Directory traversal not allowed",
            ))
          else
            val fullPath = s"$normalizedRoot/${relativePath}"

            // Convert to JS Promise and handle in Future
            val statPromise = Promise[js.Dynamic]()
            fs.promises.stat(fullPath)
              .`then`[Stats](
                { (stats: Stats) =>
                  statPromise.success(stats.asInstanceOf[js.Dynamic])
                  stats
                }: js.Function1[Stats, Stats],
                { (error: Any) =>
                  statPromise.failure(new Exception(error.toString))
                  null.asInstanceOf[Stats]
                }: js.Function1[Any, Stats],
              )

            statPromise.future
              .flatMap { stats =>
                if stats.isDirectory().asInstanceOf[Boolean] then
                  // For directories, try to serve index file
                  serveFile(s"$fullPath/$index", allMimeTypes)
                else
                  // For files, serve directly
                  serveFile(fullPath, allMimeTypes)
              }
              .recover {
                case e: Exception =>
                  Response(
                    status = 404,
                    body = s"Not Found: ${e.getMessage}",
                  )
              }
      }

  /** Serve a single file with appropriate MIME type
    *
    * @param path
    *   File path
    * @param mimeTypes
    *   MIME type mappings
    */
  private def serveFile(
      path: String,
      mimeTypes: Map[String, String],
  ): Future[Response] =
    val extension = path.split('.').lastOption.getOrElse("")
    val mimeType  = mimeTypes.getOrElse(extension, "application/octet-stream")

    val contentPromise = Promise[String]()

    fs.promises
      .readFile(
        path,
        js.Dynamic.literal(
          encoding = "utf8",
        ),
      )
      .`then`[String](
        { (content: String | js.typedarray.Uint8Array) =>
          val strContent = content.toString
          contentPromise.success(strContent)
          strContent
        }: js.Function1[String | js.typedarray.Uint8Array, String],
        { (error: Any) =>
          contentPromise.failure(new Exception(error.toString))
          null
        }: js.Function1[Any, String],
      )

    contentPromise.future.map { content =>
      Response(
        status = 200,
        headers = Map("Content-Type" -> mimeType),
        body = content,
      )
    }
