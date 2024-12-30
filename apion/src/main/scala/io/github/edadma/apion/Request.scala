package io.github.edadma.apion

import zio.json.*
import io.github.edadma.nodejs.ServerRequest

case class Request(
    method: String,
    url: String,
    headers: Map[String, String],
    auth: Option[Auth] = None,
    context: Map[String, Any] = Map(),
    rawRequest: Option[ServerRequest] = None,
):
  def header(h: String): Option[String] = headers.get(h.toLowerCase)
