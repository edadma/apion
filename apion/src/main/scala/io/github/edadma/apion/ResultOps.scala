package io.github.edadma.apion

import scala.concurrent.Future
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

implicit class ResultOps(result: Future[Result]) {
  def withHeader(name: String, value: String): Future[Result] =
    result.map {
      case Complete(r) => Complete(r.copy(headers = r.headers.add(name, value)))
      case other       => other
    }

  def withHeaders(newHeaders: (String, String)*): Future[Result] =
    result.map {
      case Complete(r) => Complete(r.copy(headers = r.headers.addAll(newHeaders)))
      case other       => other
    }
}
