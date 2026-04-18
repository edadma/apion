---
title: File Uploads
description: Multipart file upload handling with validation
---

`FileUploadMiddleware` handles `multipart/form-data` file uploads with size validation, MIME type filtering, and configurable storage.

## Basic Usage

```scala
server.post("/upload", FileUploadMiddleware(), request => {
  request.file("avatar").flatMap {
    case Some(file) =>
      Map("filename" -> file.filename, "size" -> file.size).asJson(201)
    case None =>
      "No file uploaded".asText(400)
  }
})
```

## Configuration

```scala
server.post("/upload", FileUploadMiddleware(FileUploadOptions(
  storage = DiskStorage(
    tempDir = "/tmp",
    preserveExtension = true,
    createHash = true,
  ),
  maxFileSize = 10 * 1024 * 1024,  // 10 MB
  maxFiles = 5,
  allowedMimes = Set("image/jpeg", "image/png", "application/pdf"),
  preserveExtension = true,
  createHashOnUpload = false,
  onProgress = progress => println(s"${progress.filename}: ${progress.bytesReceived}/${progress.bytesExpected}"),
  filter = file => Future.successful(file.size < 5 * 1024 * 1024),
)), uploadHandler)
```

### Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `storage` | `StorageEngine` | `DiskStorage()` | Where to store files |
| `maxFileSize` | `Long` | `50 MB` | Maximum file size |
| `maxFiles` | `Int` | `10` | Maximum number of files |
| `allowedMimes` | `Set[String]` | `Set.empty` (all) | Allowed MIME types |
| `tempDir` | `String` | `"/tmp"` | Temporary directory |
| `preserveExtension` | `Boolean` | `true` | Keep original extension |
| `createHashOnUpload` | `Boolean` | `false` | Generate SHA-256 hash |
| `onProgress` | `UploadProgress => Unit` | no-op | Progress callback |
| `filter` | `UploadedFile => Future[Boolean]` | `_ => true` | Post-upload filter |

## Accessing Uploaded Files

After the middleware processes the upload, file data is available through request extension methods:

```scala
// Single file by field name
request.file("avatar")    // Future[Option[UploadedFile]]

// Multiple files from same field
request.files("photos")   // Future[List[UploadedFile]]

// All uploaded files
request.allFiles           // Future[Map[String, List[UploadedFile]]]
```

## UploadedFile

```scala
case class UploadedFile(
  fieldname: String,           // Form field name
  filename: String,            // Original filename
  encoding: String,            // Content encoding
  mimetype: String,            // MIME type
  size: Long,                  // File size in bytes
  path: String,                // Path on disk
  hash: Option[String] = None, // SHA-256 hash (if enabled)
)
```

## Storage Engines

### DiskStorage

Saves files to disk with UUID-based names:

```scala
DiskStorage(
  tempDir = "/tmp",
  preserveExtension = true,
  createHash = true,  // SHA-256 hash
)
```

### Custom Storage

Implement the `StorageEngine` trait:

```scala
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
```

## Error Types

```scala
sealed trait FileUploadError extends ServerError

case class FileTooLargeError(size: Long, limit: Long)
case class UnsupportedMimeTypeError(mime: String)
case class TooManyFilesError(count: Int, limit: Int)
case class FileUploadSystemError(underlying: Throwable)
```

## Multipart Parser

Under the hood, `FileUploadMiddleware` uses the built-in `MultipartParser` — a pure Scala.js implementation with no npm dependencies. The parser handles RFC 2046-compliant multipart boundary detection, header parsing, and body extraction.
