package io.github.edadma.apion

import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global
import io.github.edadma.nodejs.{zlib, Buffer, bufferMod, ZlibOptions, BrotliOptions}

object CompressionMiddleware:
  case class Options(
      // Compression filter options
      level: Int = 6,        // compression level 0-9
      threshold: Int = 1024, // minimum size in bytes to compress
      memLevel: Int = 8,     // memory usage level 1-9
      windowBits: Int = 15,  // window size 9-15

      // Brotli-specific options
      brotliQuality: Int = 11,     // compression quality 0-11
      brotliBlockSize: Int = 4096, // block size 16-24

      // Filter options
      filter: Request => Boolean = _ => true, // function to determine if response should be compressed

      // Which encodings to support/prefer (in order of preference)
      encodings: List[String] = List("br", "gzip", "deflate"),
  )

  private def shouldCompress(request: Request, response: Response, options: Options): Boolean =
    if !options.filter(request) then false
    else if response.headers.get("Content-Encoding").exists(_.nonEmpty) then false
    else if response.headers.get("Transfer-Encoding").exists(_.nonEmpty) then false
    else
      // Get size based on response body type
      val bodySize = response.body match
        case ResponseBody.Text(content, _) => content.length
        case ResponseBody.Binary(content)  => content.byteLength
//        case ResponseBody.Stream(_)        =>
//          // For streams, rely on Content-Length header if present
//          response.headers.get("Content-Length")
//            .map(_.toInt)
//            .getOrElse(options.threshold + 1) // Assume it's worth compressing if no length known
        case ResponseBody.Empty => 0

      if bodySize < options.threshold then false
      else
        response.headers.get("Content-Type") match
          case Some(contentType) =>
            val compressible = contentType.toLowerCase match
              case ct if ct.contains("text/")                  => true
              case ct if ct.contains("application/json")       => true
              case ct if ct.contains("application/javascript") => true
              case ct if ct.contains("application/xml")        => true
              case _                                           => false
            compressible
          case None => false

  private def getBestEncoding(acceptEncoding: Option[String], supported: List[String]): Option[String] =
    acceptEncoding match
      case Some(accepts) =>
        // Parse accept-encoding header
        val encodings = accepts.split(",").map(_.trim.toLowerCase).toList
        // Find first supported encoding that is accepted
        supported.find(enc => encodings.exists(_.startsWith(enc)))
      case None => None

  private def compress(data: String | Buffer, encoding: String, options: Options): Future[Buffer] =
    val promise = Promise[Buffer]()

    encoding match
      case "br" =>
        val brotliOpts = BrotliOptions(Map(
          "quality"   -> options.brotliQuality,
          "blockSize" -> options.brotliBlockSize,
        ))

        zlib.brotliCompress(
          data,
          brotliOpts,
          (err, result) =>
            if err != null then promise.failure(new Exception(err.message))
            else promise.success(result),
        )

      case "gzip" =>
        val zlibOpts = ZlibOptions(
          level = Some(options.level),
          memLevel = Some(options.memLevel),
          windowBits = Some(options.windowBits),
        )

        zlib.gzip(
          data,
          zlibOpts,
          (err, result) =>
            if err != null then promise.failure(new Exception(err.message))
            else promise.success(result),
        )

      case "deflate" =>
        val zlibOpts = ZlibOptions(
          level = Some(options.level),
          memLevel = Some(options.memLevel),
          windowBits = Some(options.windowBits),
        )

        zlib.deflate(
          data,
          zlibOpts,
          (err, result) =>
            if err != null then promise.failure(new Exception(err.message))
            else promise.success(result),
        )

      case _ =>
        promise.failure(new Exception(s"Unsupported encoding: $encoding"))

    promise.future

  def apply(options: Options = Options()): Handler = request =>
    // Create compression finalizer
    val compressionFinalizer: Finalizer = (req, res) =>
      if !shouldCompress(req, res, options) then
        Future.successful(res)
      else
        // Get best encoding based on Accept-Encoding header
        getBestEncoding(req.header("accept-encoding"), options.encodings) match
          case Some(encoding) =>
            // Compress the response body
            val body =
              res.body match
                case ResponseBody.Text(content, encoding) => bufferMod.Buffer.from(content, encoding)
                case ResponseBody.Binary(content)         => content
                case ResponseBody.Empty                   => sys.error("compressionFinalizer: empty body")

            compress(body, encoding, options).map { compressed =>
              res.copy(
                headers = res.headers.addAll(Seq(
                  "Content-Encoding" -> encoding,
                  "Content-Length"   -> compressed.byteLength.toString,
                  "Vary"             -> "Accept-Encoding",
                )),
                body = ResponseBody.Binary(compressed),
              )
            }.recover { case e =>
              logger.error(s"Compression error: ${e.getMessage}")
              res // Return uncompressed response on error
            }

          case None =>
            Future.successful(res)

    Future.successful(Continue(request.addFinalizer(compressionFinalizer)))
