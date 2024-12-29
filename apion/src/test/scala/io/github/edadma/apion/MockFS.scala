package io.github.edadma.apion

import io.github.edadma.nodejs.*

import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array

trait FSInterface:
  def readFile(path: String): js.Promise[Uint8Array]

  def readFile(path: String, options: ReadFileOptions): js.Promise[String | Uint8Array]

  def stat(path: String): js.Promise[Stats]

// RealFS.scala - wrapper for the real Node.js fs implementation
object RealFS extends FSInterface:
  def readFile(path: String): js.Promise[Uint8Array] = fs.promises.readFile(path)

  def readFile(path: String, options: ReadFileOptions): js.Promise[String | Uint8Array] =
    fs.promises.readFile(path, options)

  def stat(path: String): js.Promise[Stats] =
    fs.promises.stat(path)

def mockStats(
    isDir: Boolean,
    fileSize: Double,
    fileMode: Double,
    modTime: js.Date,
): Stats =
  js.Dynamic.literal(
    isDirectory = () => isDir,
    isFile = () => !isDir,
    size = fileSize,
    mode = fileMode,
    mtime = modTime,
  ).asInstanceOf[Stats]

case class MockFile(content: Array[Byte], stats: Stats)

def mockFile(content: String, isDir: Boolean, fileMode: String) =
  val data = content.getBytes

  new MockFile(data, mockStats(isDir, data.length, Integer.parseInt(fileMode, 8), new js.Date))

class MockFS(files: Map[String, MockFile]) extends FSInterface:
  def readFile(path: String): js.Promise[Uint8Array] =
    files.get(path) match
      case Some(MockFile(content, _)) =>
        js.Promise.resolve(new Uint8Array(js.Array[Short](content.map(b =>
          (b & 0xff).toShort,
        )*)))
      case None =>
        js.Promise.reject(new js.Error(s"File not found: $path"))

  def readFile(path: String, options: ReadFileOptions): js.Promise[String | Uint8Array] =
    files.get(path) match
      case Some(MockFile(content, _)) =>
        options.encoding.toOption match
          case Some("utf8") =>
            val decoder = new java.lang.String(content, "UTF-8")
            js.Promise.resolve(decoder)
          case _ =>
            js.Promise.resolve(new Uint8Array(js.Array[Short](content.map(b => (b & 0xff).toShort)*)))
      case None =>
        js.Promise.reject(new js.Error(s"File not found: $path"))

  def stat(path: String): js.Promise[Stats] =
    files.get(path) match
      case Some(MockFile(_, stats)) => js.Promise.resolve(stats)
      case None                     => js.Promise.reject(new js.Error(s"File not found: $path"))

/*
  val files =
    Map(".scalafmt.conf" -> mockFile("mock file content", false, "644"))

  MockFS(files).readFile(".scalafmt.conf", ReadFileOptions(encoding = "utf8")).toFuture.map { data =>
    println(data)
  }
 */
