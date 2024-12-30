//package io.github.edadma.apion
//
//import io.github.edadma.nodejs.*
//import scala.concurrent.{Future, Promise}
//import scala.scalajs.js
//import scala.concurrent.ExecutionContext.Implicits.global
//import java.nio.file.{Path, Paths}
//import scala.util.{Success, Failure}
//
//object Middlewares {
//
//  /** CORS (Cross-Origin Resource Sharing) Middleware
//    *
//    * @param allowOrigin
//    *   Allowed origins (default allows all)
//    * @param allowMethods
//    *   Allowed HTTP methods
//    * @param allowHeaders
//    *   Allowed headers
//    */
//  def cors(
//      allowOrigin: String = "*",
//      allowMethods: Set[String] = Set("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"),
//      allowHeaders: Set[String] = Set("Content-Type", "Authorization"),
//  ): Handler =
//    endpoint =>
//      request => {
//        // Handle preflight requests
//        if request.method == "OPTIONS" then
//          Future.successful(Response(
//            status = 204,
//            headers = Map(
//              "Access-Control-Allow-Origin"  -> allowOrigin,
//              "Access-Control-Allow-Methods" -> allowMethods.mkString(", "),
//              "Access-Control-Allow-Headers" -> allowHeaders.mkString(", "),
//              "Access-Control-Max-Age"       -> "86400",
//            ),
//            body = "",
//          ))
//        else
//          endpoint(request).map { response =>
//            response.copy(
//              headers = response.headers ++ Map(
//                "Access-Control-Allow-Origin"  -> allowOrigin,
//                "Access-Control-Allow-Methods" -> allowMethods.mkString(", "),
//                "Access-Control-Allow-Headers" -> allowHeaders.mkString(", "),
//              ),
//            )
//          }
//      }
//
//  /** Security Headers Middleware Adds common security-related HTTP headers
//    */
//  def securityHeaders(): Handler =
//    endpoint =>
//      request => {
//        endpoint(request).map { response =>
//          response.copy(
//            headers = response.headers ++ Map(
//              "X-Content-Type-Options"    -> "nosniff",
//              "X-Frame-Options"           -> "DENY",
//              "X-XSS-Protection"          -> "1; mode=block",
//              "Referrer-Policy"           -> "no-referrer",
//              "Strict-Transport-Security" -> "max-age=31536000; includeSubDomains",
//            ),
//          )
//        }
//      }
//
//  /** Request Logging Middleware Logs incoming requests with method, URL, and headers
//    */
//  def requestLogger(): Handler =
//    endpoint =>
//      request => {
//        logger.info(s"[Request] ${request.method} ${request.url}")
//        request.headers.foreach { case (key, value) =>
//          logger.debug(s"[Request Header] $key: $value")
//        }
//
//        val start = System.currentTimeMillis()
//
//        endpoint(request).map { response =>
//          val duration = System.currentTimeMillis() - start
//          logger.info(s"[Response] ${request.method} ${request.url} - ${response.status} (${duration}ms)")
//          response
//        }
//      }
//
//  /** Error Handling Middleware Provides consistent error responses
//    */
//  def errorHandler(): Handler =
//    endpoint =>
//      request => {
//        endpoint(request).recover {
//          case e: Exception =>
//            logger.error(s"Unhandled error: ${e.getMessage}")
//            e.printStackTrace() // This will print stack trace to console
//            Response(
//              status = 500,
//              headers = Map("Content-Type" -> "application/json"),
//              body = s"""{"error": "Internal Server Error", "message": "${e.getMessage}"}""",
//            )
//        }
//      }
//
//  /** Content Negotiation Middleware Ensures consistent Content-Type handling
//    */
//  def contentNegotiation(): Handler =
//    endpoint =>
//      request => {
//        endpoint(request).map { response =>
//          // Ensure Content-Type is always set
//          val headersWithContentType =
//            if !response.headers.contains("Content-Type") then
//              response.headers + ("Content-Type" -> "application/json")
//            else
//              response.headers
//
//          response.copy(headers = headersWithContentType)
//        }
//      }
//}
