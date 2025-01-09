package io.github.edadma.apion

import io.github.edadma.nodejs.*

import scala.scalajs.js

trait FSInterface:
  def readFile(path: String): js.Promise[Buffer]

  def readFile(path: String, options: ReadFileOptions): js.Promise[String | Buffer]

  def createReadStream(path: String): ReadableStream
  def createReadStream(path: String, options: ReadStreamOptions): ReadableStream // Add this overload

  def stat(path: String): js.Promise[Stats]

object RealFS extends FSInterface:
  def readFile(path: String): js.Promise[Buffer] = fs.promises.readFile(path)

  def readFile(path: String, options: ReadFileOptions): js.Promise[String | Buffer] =
    fs.promises.readFile(path, options)

  def createReadStream(path: String): ReadableStream =
    fs.createReadStream(
      path,
      ReadStreamOptions(
        highWaterMark = 64 * 1024, // 64KB chunks
        encoding = null,           // Binary
      ),
    )

  def createReadStream(path: String, options: ReadStreamOptions): ReadableStream =
    fs.createReadStream(path, options)

  def stat(path: String): js.Promise[Stats] =
    fs.promises.stat(path)
