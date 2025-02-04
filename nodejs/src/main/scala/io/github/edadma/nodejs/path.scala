package io.github.edadma.nodejs

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

// In nodejs facade library
@js.native
@JSImport("path", JSImport.Namespace)
object path extends js.Object {
  def join(paths: String*): String = js.native
}
