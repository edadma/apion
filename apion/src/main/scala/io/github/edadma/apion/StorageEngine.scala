package io.github.edadma.apion

import scala.concurrent.{Promise, Future}
import scala.scalajs.js
import scala.scalajs.js.annotation.*
import io.github.edadma.nodejs.{fs, crypto, ReadableStream, Buffer}
import zio.json.*
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

/** Represents an uploaded file with metadata */
case class UploadedFile(
    fieldname: String,          // Form field name
    filename: String,           // Original filename
    encoding: String,           // File encoding
    mimetype: String,           // Content type
    size: Long,                 // File size in bytes
    path: String,               // Path to temp file
    hash: Option[String] = None, // Optional hash for verification
) derives JsonEncoder, JsonDecoder

/** Interface for file storage engines */
trait StorageEngine {
  def store(
      fieldname: String,
      file: ReadableStream,
      filename: String,
      encoding: String,
      mimetype: String,
  ): Future[UploadedFile]

  def remove(file: UploadedFile): Future[Unit]
}

/** File upload progress event */
case class UploadProgress(
    fieldname: String,
    filename: String,
    bytesReceived: Long,
    bytesExpected: Long,
)

/** Options for file upload handling */
case class FileUploadOptions(
    storage: StorageEngine = DiskStorage(),
    maxFileSize: Long = 50 * 1024 * 1024, // 50MB default
    maxFiles: Int = 10,
    allowedMimes: Set[String] = Set.empty, // Empty means all allowed
    tempDir: String = "/tmp",
    preserveExtension: Boolean = true,
    createHashOnUpload: Boolean = false,
    onProgress: UploadProgress => Unit = _ => (),
    filter: UploadedFile => Future[Boolean] = _ => Future.successful(true),
)

/** File upload specific errors */
sealed trait FileUploadError extends ServerError {
  def message: String
  def toResponse: Response = Response.json(
    Map("error" -> "file_upload_error", "message" -> message),
    400,
  )
}

case class FileTooLargeError(size: Long, limit: Long) extends FileUploadError {
  val message = s"File size $size exceeds limit of $limit bytes"
}

case class UnsupportedMimeTypeError(mime: String) extends FileUploadError {
  val message = s"Unsupported MIME type: $mime"
}

case class TooManyFilesError(count: Int, limit: Int) extends FileUploadError {
  val message = s"Too many files: $count exceeds limit of $limit"
}

case class FileUploadSystemError(underlying: Throwable) extends FileUploadError {
  val message = s"File upload failed: ${underlying.getMessage}"
}

/** Request extensions for file uploads */
trait FileUploadOps {
  def file(fieldname: String): Future[Option[UploadedFile]]
  def files(fieldname: String): Future[List[UploadedFile]]
  def allFiles: Future[Map[String, List[UploadedFile]]]
}

case class DiskStorage(
    tempDir: String = "/tmp",
    preserveExtension: Boolean = true,
    createHash: Boolean = false,
) extends StorageEngine {

  def store(
      fieldname: String,
      file: ReadableStream,
      filename: String,
      encoding: String,
      mimetype: String,
  ): Future[UploadedFile] = {
    val promise  = Promise[UploadedFile]()
    var fileSize = 0L
    var hash     = if (createHash) Some(crypto.createHash("sha256")) else None

    val ext = if (preserveExtension && filename.contains('.')) {
      "." + filename.split('.').last
    } else ""
    val uniqueName = s"${generateUUID()}$ext"
    val destPath   = s"$tempDir/$uniqueName"

    val writeStream = fs.createWriteStream(destPath)

    file.on(
      "data",
      (chunk: Buffer) => {
        fileSize += chunk.length
        writeStream.write(chunk)
        hash.foreach(_.update(chunk))
      },
    )

    file.on(
      "end",
      () => {
        writeStream.end()
        val fileHash = hash.map(h => h.digest("hex"))

        promise.success(UploadedFile(
          fieldname = fieldname,
          filename = filename,
          encoding = encoding,
          mimetype = mimetype,
          size = fileSize,
          path = destPath,
          hash = fileHash,
        ))
      },
    )

    file.on(
      "error",
      (err: js.Error) => {
        writeStream.end()
        try {
          fs.unlink(destPath, (_: js.Error | Null) => ())
        } catch {
          case _: Throwable => // Ignore cleanup errors
        }
        promise.failure(FileUploadSystemError(new Exception(err.toString)))
      },
    )

    promise.future
  }

  def remove(file: UploadedFile): Future[Unit] = {
    val promise = Promise[Unit]()

    fs.unlink(
      file.path,
      (err: js.Error | Null) => {
        if (err != null) {
          promise.failure(FileUploadSystemError(new Exception(err.toString)))
        } else {
          promise.success(())
        }
      },
    )

    promise.future
  }
}
