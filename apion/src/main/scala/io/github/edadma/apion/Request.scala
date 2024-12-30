package io.github.edadma.apion

import zio.json.*
import io.github.edadma.nodejs.ServerRequest

case class Request(
    method: String,
    path: String,
    params: Map[String, String] = Map(),
    headers: Map[String, String],
    context: Map[String, Any] = Map(),
    rawRequest: Option[ServerRequest] = None,
):
  def header(h: String): Option[String] = headers.get(h.toLowerCase)
