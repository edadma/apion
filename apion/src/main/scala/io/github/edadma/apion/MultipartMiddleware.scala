package io.github.edadma.apion

import io.github.edadma.nodejs.{Buffer, ReadableStream, bufferMod, fs, path}

import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.util.{Failure, Success}

trait StorageEngine {

  /** Store a file from the multipart stream */
  def store(
      request: Request,
      headers: PartHeaders,
      stream: ReadableStream,
  ): Future[StorageResult]

  /** Clean up any resources */
  def cleanup(result: StorageResult): Future[Unit]
}

case class StorageResult(
    path: Option[String] = None,  // For disk storage
    buffer: Option[Buffer] = None, // For memory storage
)

case class PartHeaders(
    contentDisposition: String,  // Required
    contentType: Option[String], // Optional
    contentLength: Option[Long], // Added content length
    filename: Option[String],    // For files
    fieldName: String,           // Form field name
)

// File metadata from multipart headers
case class FileInfo(
    fieldName: String,    // Form field name
    originalName: String, // Original filename from client
    mimetype: String,     // Content type
    size: Long,           // Size in bytes if known from headers
)

// Result after storage
case class StoredFile(
    info: FileInfo,
    path: Option[String] = None,  // For disk storage - path to file
    buffer: Option[Buffer] = None, // For memory storage - file contents
)

case class DiskStorage(
    destination: (Request, FileInfo) => String, // Changed from File to FileInfo
    filename: (Request, FileInfo) => String,
    preserveExtension: Boolean = true,
    tempDir: String = sys.props("java.io.tmpdir"),
) extends StorageEngine {
  private def createTempFile(): String = {
    // Use Node's path module to join paths safely
    val tempName = s"upload-${System.currentTimeMillis()}-${generateUUID()}"
    path.join(tempDir, tempName)
  }

  def store(request: Request, headers: PartHeaders, stream: ReadableStream): Future[StorageResult] = {
    val fileInfo = FileInfo(
      fieldName = headers.fieldName,
      originalName = headers.filename.getOrElse(""),
      mimetype = headers.contentType.getOrElse("application/octet-stream"),
      size = headers.contentLength.getOrElse(0L),
    )

    val tempFile    = createTempFile()
    val writeStream = fs.createWriteStream(tempFile)

    val promise = Promise[StorageResult]()

    stream.pipe(writeStream).on(
      "finish",
      () => {
        val finalPath = destination(request, fileInfo)

        fs.promises.rename(tempFile, finalPath)
          .toFuture
          .map(_ => StorageResult(path = Some(finalPath)))
          .onComplete {
            case Success(result) => promise.success(result)
            case Failure(e)      => promise.failure(e)
          }
      },
    )

    promise.future
  }

  def cleanup(result: StorageResult): Future[Unit] = {
    result.path.map(p => fs.promises.unlink(p).toFuture)
      .getOrElse(Future.successful(()))
  }
}

case class MemoryStorage(
    maxSize: Long = 5 * 1024 * 1024, // 5MB default
) extends StorageEngine {
  def store(request: Request, headers: PartHeaders, stream: ReadableStream): Future[StorageResult] = {
    val chunks    = new js.Array[Buffer]()
    var totalSize = 0L
    val promise   = Promise[StorageResult]()

    stream.on(
      "data",
      (chunk: Buffer) => {
        totalSize += chunk.length
        if (totalSize > maxSize) {
          stream.destroy(new js.Error("File too large"))
        } else {
          chunks += chunk
        }
      },
    )

    stream.on(
      "end",
      () => {
        val buffer = bufferMod.Buffer.concat(chunks)
        promise.success(StorageResult(buffer = Some(buffer)))
      },
    )

    promise.future
  }

  def cleanup(result: StorageResult): Future[Unit] = Future.successful(())
}

case class FileFilter(
    filter: (Request, FileInfo) => Boolean, // Changed from File to FileInfo
    errorMessage: String = "File type not allowed",
)
