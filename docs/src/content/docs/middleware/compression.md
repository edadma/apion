---
title: Compression
description: Response compression with Brotli, Gzip, and Deflate
---

`CompressionMiddleware` compresses response bodies based on the client's `Accept-Encoding` header.

## Basic Usage

```scala
server.use(CompressionMiddleware())
```

## Configuration

```scala
server.use(CompressionMiddleware(CompressionMiddleware.Options(
  level = 6,              // Compression level (0-9)
  threshold = 1024,       // Min size in bytes to compress (default 1KB)
  memLevel = 8,           // Memory usage (1-9)
  windowBits = 15,        // Window size (9-15)
  brotliQuality = 11,     // Brotli quality (0-11)
  brotliBlockSize = 4096, // Brotli block size
  filter = _ => true,     // Filter function
  encodings = List("br", "gzip", "deflate"),
)))
```

### Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `level` | `Int` | `6` | Gzip/Deflate compression level (0-9) |
| `threshold` | `Int` | `1024` | Minimum response size to compress (bytes) |
| `memLevel` | `Int` | `8` | Memory usage level (1-9) |
| `windowBits` | `Int` | `15` | Window size (9-15) |
| `brotliQuality` | `Int` | `11` | Brotli compression quality (0-11) |
| `brotliBlockSize` | `Int` | `4096` | Brotli block size |
| `filter` | `Request => Boolean` | `_ => true` | Whether to compress this request |
| `encodings` | `List[String]` | `["br", "gzip", "deflate"]` | Supported encodings in preference order |

## How It Works

1. The middleware adds a response finalizer on each request
2. When the response is ready, the finalizer checks:
   - The client's `Accept-Encoding` header
   - Whether the response exceeds the size threshold
   - Whether the filter function allows compression
3. The response body is compressed using the best matching encoding
4. `Content-Encoding` and `Vary: Accept-Encoding` headers are set

## Encoding Priority

Encodings are tried in the order specified by the `encodings` list, matched against the client's `Accept-Encoding` header:

1. **Brotli** (`br`) — best compression ratio, modern browsers
2. **Gzip** (`gzip`) — universal support
3. **Deflate** (`deflate`) — fallback

## Stream Compression

Streaming responses (`ReadableStreamBody`) are also compressed — the stream is piped through a compression transform.

## Selective Compression

Skip compression for certain requests:

```scala
server.use(CompressionMiddleware(CompressionMiddleware.Options(
  filter = request => {
    // Only compress text-based content
    val accept = request.header("accept").getOrElse("")
    accept.contains("text/") ||
    accept.contains("application/json") ||
    accept.contains("application/javascript")
  }
)))
```
