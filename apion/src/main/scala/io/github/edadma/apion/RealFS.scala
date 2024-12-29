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
