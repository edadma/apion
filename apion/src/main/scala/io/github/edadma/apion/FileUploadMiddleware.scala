package io.github.edadma.apion

import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import io.github.edadma.nodejs.*

/** File Upload middleware for handling multipart/form-data uploads. */
object FileUploadMiddleware {

  private def isMultipart(request: Request): Boolean =
    request.header("content-type").exists(_.toLowerCase.startsWith("multipart/form-data"))

  /** Create file upload middleware. */
  def apply(options: FileUploadOptions = FileUploadOptions()): Handler = request => {
    if (!isMultipart(request)) {
      skip // Not a file upload request
    } else {
      val contentType = request.header("content-type").getOrElse("")
      MultipartParser.extractBoundary(contentType) match {
        case None =>
          failValidation("Missing boundary in multipart Content-Type")
        case Some(boundary) =>
          processFileUpload(request, options, boundary).map { files =>
            Continue(request.copy(
              context = request.context + ("files" -> files),
            ))
          }.recover { case e: Throwable =>
            Fail(FileUploadSystemError(e))
          }
      }
    }
  }

  private def processFileUpload(
      request: Request,
      options: FileUploadOptions,
      boundary: String,
  ): Future[Map[String, List[UploadedFile]]] = {
    MultipartParser
      .parseStream(request.rawRequest, boundary, options.maxFileSize * options.maxFiles)
      .flatMap { parts =>
        val fileParts = parts.filter(_.filename.isDefined)

        // Validate file count
        if (fileParts.length > options.maxFiles) {
          Future.failed(TooManyFilesError(fileParts.length, options.maxFiles))
        }
        // Validate MIME types
        else if (options.allowedMimes.nonEmpty) {
          val disallowed = fileParts.find(p => !options.allowedMimes.contains(p.contentType))
          disallowed match {
            case Some(p) => Future.failed(UnsupportedMimeTypeError(p.contentType))
            case None    => storeParts(fileParts, options)
          }
        } else {
          storeParts(fileParts, options)
        }
      }
  }

  private def storeParts(
      parts: List[MultipartParser.Part],
      options: FileUploadOptions,
  ): Future[Map[String, List[UploadedFile]]] = {
    // Validate individual file sizes
    val oversized = parts.find(_.data.length > options.maxFileSize)
    oversized match {
      case Some(p) => Future.failed(FileTooLargeError(p.data.length.toLong, options.maxFileSize))
      case None =>
        val futures = parts.map { part =>
          // Create a readable stream from the buffer for the storage engine
          val readable = stream.Readable.from(part.data)
          options.storage.store(
            part.name,
            readable,
            part.filename.getOrElse(""),
            part.encoding,
            part.contentType,
          ).map(part.name -> _)
        }

        Future.sequence(futures).map { results =>
          results.groupMap(_._1)(_._2)
        }
    }
  }
}

/** Request extensions for file upload operations. */
implicit class RequestFileUploadOps(val request: Request) extends FileUploadOps {
  private def getFiles = request.context
    .get("files")
    .map(_.asInstanceOf[Map[String, List[UploadedFile]]])
    .getOrElse(Map.empty)

  def file(fieldname: String): Future[Option[UploadedFile]] =
    Future.successful(getFiles.get(fieldname).flatMap(_.headOption))

  def files(fieldname: String): Future[List[UploadedFile]] =
    Future.successful(getFiles.getOrElse(fieldname, Nil))

  def allFiles: Future[Map[String, List[UploadedFile]]] =
    Future.successful(getFiles)
}
