package io.github.edadma.apion

import scala.concurrent.Future
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

object CommonMiddleware:
  /** CORS (Cross-Origin Resource Sharing) middleware
    *
    * @param allowOrigin
    *   Origins to allow (default "*")
    * @param allowMethods
    *   HTTP methods to allow
    * @param allowHeaders
    *   Headers to allow
    */
  def cors(
      allowOrigin: String = "*",
      allowMethods: Set[String] = Set("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"),
      allowHeaders: Set[String] = Set("Content-Type", "Authorization"),
  ): Handler = request =>
    if request.method == "OPTIONS" then
      // Handle preflight directly
      Future.successful(Complete(Response(
        status = 204,
        headers = Map(
          "Access-Control-Allow-Origin"  -> allowOrigin,
          "Access-Control-Allow-Methods" -> allowMethods.mkString(", "),
          "Access-Control-Allow-Headers" -> allowHeaders.mkString(", "),
          "Access-Control-Max-Age"       -> "86400",
        ),
        body = "",
      )))
    else
      // Store CORS headers in context and add finalizer
      val corsHeaders = Map(
        "Access-Control-Allow-Origin"  -> allowOrigin,
        "Access-Control-Allow-Methods" -> allowMethods.mkString(", "),
        "Access-Control-Allow-Headers" -> allowHeaders.mkString(", "),
      )

      val corsFinalizer: Finalizer = (req, res) =>
        Future.successful(res.copy(
          headers = res.headers ++ corsHeaders,
        ))

      Future.successful(Continue(
        request.addFinalizer(corsFinalizer),
      ))
