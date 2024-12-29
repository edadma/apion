package io.github.edadma.apion

import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global
import io.github.edadma.nodejs.*

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.util.{Failure, Success}

object StaticMiddleware:
  def apply(rootPath: String): Middleware =
    endpoint =>
      request => {
        println(request)
        val filePath   = s"$rootPath${request.url}"
        val filePathJS = filePath.asInstanceOf[js.Dynamic]

        fs.promises.stat(filePath).toFuture.flatMap { stats =>
          if stats.isFile() then
            fs.promises.readFile(filePath).toFuture.map { dataBuffer =>
              val contentType = getContentType(filePath)

              Response(
                status = 200,
                headers = Map("Content-Type" -> contentType),
                body = new Buffer(dataBuffer).toString("utf-8"),
              )
            }
          else
            Future.successful(Response(status = 404, body = "Not Found"))
        }.recover {
          case _: Throwable => Response(status = 404, body = "Not Found")
        }
      }

  private def getContentType(filePath: String): String =
    filePath match
      case path if path.endsWith(".html") => "text/html"
      case path if path.endsWith(".js")   => "application/javascript"
      case path if path.endsWith(".css")  => "text/css"
      case path if path.endsWith(".json") => "application/json"
      case path if path.endsWith(".png")  => "image/png"
      case _                              => "application/octet-stream"
