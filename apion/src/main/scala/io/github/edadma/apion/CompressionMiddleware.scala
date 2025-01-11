package io.github.edadma.apion

import scala.concurrent.{Future, Promise}
import io.github.edadma.nodejs.{Buffer, bufferMod, ZlibOptions, BrotliOptions, ReadableStream, zlib}

object CompressionMiddleware:
  case class Options(
      level: Int = 6,
      threshold: Int = 1024,
      memLevel: Int = 8,
      windowBits: Int = 15,
      brotliQuality: Int = 11,
      brotliBlockSize: Int = 4096,
      filter: Request => Boolean = _ => true,
      encodings: List[String] = List("br", "gzip", "deflate"),
  )

  private def compressStream(
      stream: ReadableStream,
      encoding: String,
      options: Options,
  ): ReadableStream =
    encoding match
      case "br" =>
        val brotliOpts = BrotliOptions(Map(
          "quality"   -> options.brotliQuality,
          "blockSize" -> options.brotliBlockSize,
        ))
        val brotli = zlib.createBrotliCompress(brotliOpts)
        stream.pipe(brotli)
        brotli

      case "gzip" =>
        val zlibOpts = ZlibOptions(
          level = Some(options.level),
          memLevel = Some(options.memLevel),
          windowBits = Some(options.windowBits),
        )
        val gzip = zlib.createGzip(zlibOpts)
        stream.pipe(gzip)
        gzip

      case "deflate" =>
        val zlibOpts = ZlibOptions(
          level = Some(options.level),
          memLevel = Some(options.memLevel),
          windowBits = Some(options.windowBits),
        )
        val deflate = zlib.createDeflate(zlibOpts)
        stream.pipe(deflate)
        deflate

      case _ => stream

  private def shouldCompress(request: Request, response: Response, options: Options): Boolean =
    if !options.filter(request) then false
    else if response.headers.get("Content-Encoding").exists(_.nonEmpty) then false
    else if response.headers.get("Transfer-Encoding").exists(_.nonEmpty) then false
    else
      val bodySize = response.body match
        case StringBody(_, content) => bufferMod.Buffer.from(content).byteLength
        case BufferBody(content)    => content.byteLength
        case ReadableStreamBody(_)  => options.threshold + 1 // Assume it's worth compressing
        case EmptyBody              => 0

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
        val encodings = accepts.split(",").map(_.trim.toLowerCase).toList
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
    val compressionFinalizer: Finalizer = (req, res) =>
      if !shouldCompress(req, res, options) then
        Future.successful(res)
      else
        getBestEncoding(req.header("accept-encoding"), options.encodings) match
          case Some(encoding) =>
            res.body match
              case StringBody(_, content) =>
                compress(content, encoding, options).map { compressed =>
                  res.copy(
                    headers = res.headers.addAll(Seq(
                      "Content-Encoding" -> encoding,
                      "Content-Length"   -> compressed.byteLength.toString,
                      "Vary"             -> "Accept-Encoding",
                    )),
                    body = BufferBody(compressed),
                  )
                }.recover { case e =>
                  logger.error(s"Compression error: ${e.getMessage}")
                  res
                }
              case BufferBody(content) =>
                compress(content, encoding, options).map { compressed =>
                  res.copy(
                    headers = res.headers.addAll(Seq(
                      "Content-Encoding" -> encoding,
                      "Content-Length"   -> compressed.byteLength.toString,
                      "Vary"             -> "Accept-Encoding",
                    )),
                    body = BufferBody(compressed),
                  )
                }.recover { case e =>
                  logger.error(s"Compression error: ${e.getMessage}")
                  res
                }
              case ReadableStreamBody(stream) =>
                Future.successful(res.copy(
                  headers = res.headers.addAll(Seq(
                    "Content-Encoding" -> encoding,
                    "Vary"             -> "Accept-Encoding",
                  )),
                  body = ReadableStreamBody(compressStream(stream, encoding, options)),
                ))
              case EmptyBody =>
                Future.successful(res)
          case None =>
            Future.successful(res)

    Future.successful(Continue(request.addFinalizer(compressionFinalizer)))
