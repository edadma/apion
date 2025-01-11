package io.github.edadma.apion

import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.annotation.*
import io.github.edadma.nodejs.*
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

/** File Upload middleware for handling multipart/form-data uploads */
object FileUploadMiddleware {

  @js.native
  @JSImport("busboy", JSImport.Default)
  class Busboy(config: js.Dynamic) extends js.Object {
    def on(event: String, callback: js.Function): this.type = js.native
  }

  private def isMultipart(request: Request): Boolean = {
    request.header("content-type").exists(_.toLowerCase.startsWith("multipart/form-data"))
  }

  /** Create file upload middleware */
  def apply(options: FileUploadOptions = FileUploadOptions()): Handler = request => {
    if (!isMultipart(request)) {
      skip // Not a file upload request
    } else {
      processFileUpload(request, options).map { files =>
        Continue(request.copy(
          context = request.context + ("files" -> files),
        ))
      }
    }
  }

  private def processFileUpload(
      request: Request,
      options: FileUploadOptions,
  ): Future[Map[String, List[UploadedFile]]] = {
    val promise        = Promise[Map[String, List[UploadedFile]]]()
    var fileCount      = 0
    val uploadedFiles  = scala.collection.mutable.Map[String, List[UploadedFile]]()
    val pendingUploads = scala.collection.mutable.Set[Promise[UploadedFile]]()

    try {
      // Configure busboy
      val busboy = new Busboy(js.Dynamic.literal(
        headers = request.rawRequest.headers,
      ))

      // Handle file uploads
      busboy.on(
        "file",
        { (fieldname: String, file: ReadableStream, filename: String, encoding: String, mimetype: String) =>
          if (fileCount >= options.maxFiles) {
            file.resume() // Drain the stream
            promise.failure(TooManyFilesError(fileCount + 1, options.maxFiles))
          } else if (options.allowedMimes.nonEmpty && !options.allowedMimes.contains(mimetype)) {
            file.resume() // Drain the stream
            promise.failure(UnsupportedMimeTypeError(mimetype))
          } else {
            fileCount += 1
            var fileSize      = 0L
            val uploadPromise = Promise[UploadedFile]()
            pendingUploads.add(uploadPromise)

            // Track progress
            file.on(
              "data",
              { (data: Buffer) =>
                fileSize += data.length
                if (fileSize > options.maxFileSize) {
                  file.resume() // Drain the stream
                  uploadPromise.failure(FileTooLargeError(fileSize, options.maxFileSize))
                } else {
                  options.onProgress(UploadProgress(
                    fieldname = fieldname,
                    filename = filename,
                    bytesReceived = fileSize,
                    bytesExpected = -1, // Not always available
                  ))
                }
              },
            )

            // Store file
            options.storage.store(fieldname, file, filename, encoding, mimetype).onComplete {
              case scala.util.Success(uploadedFile) =>
                uploadPromise.success(uploadedFile)
                // Add to files map
                uploadedFiles.updateWith(fieldname) {
                  case Some(files) => Some(files :+ uploadedFile)
                  case None        => Some(List(uploadedFile))
                }
              case scala.util.Failure(e) =>
                uploadPromise.failure(e)
            }
          }
        },
      )

      // Handle end of parsing
      busboy.on(
        "finish",
        { () =>
          // Wait for all files to finish uploading
          Future.sequence(pendingUploads.map(_.future)).onComplete {
            case scala.util.Success(_) =>
              promise.success(uploadedFiles.toMap)
            case scala.util.Failure(e) =>
              promise.failure(e)
              // Clean up any successfully uploaded files
              uploadedFiles.values.flatten.foreach(options.storage.remove)
          }
        },
      )

      // Handle parsing errors
      busboy.on(
        "error",
        { (err: js.Error) =>
          promise.failure(FileUploadSystemError(new Exception(err.message)))
        },
      )

      // Pipe request to busboy
      request.rawRequest.pipe(busboy)

    } catch {
      case e: Throwable =>
        promise.failure(FileUploadSystemError(e))
    }

    promise.future
  }
}

/** Request extensions for file upload operations */
implicit class RequestFileUploadOps(val request: Request) extends FileUploadOps {
  private def getFiles = request.context.get("files")
    .map(_.asInstanceOf[Map[String, List[UploadedFile]]])
    .getOrElse(Map.empty)

  def file(fieldname: String): Future[Option[UploadedFile]] =
    Future.successful(getFiles.get(fieldname).flatMap(_.headOption))

  def files(fieldname: String): Future[List[UploadedFile]] =
    Future.successful(getFiles.getOrElse(fieldname, Nil))

  def allFiles: Future[Map[String, List[UploadedFile]]] =
    Future.successful(getFiles)
}
