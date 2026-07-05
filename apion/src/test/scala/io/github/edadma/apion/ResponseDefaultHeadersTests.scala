package io.github.edadma.apion

class ResponseDefaultHeadersTests extends AnyBaseSpec {
  private def value(r: Response, name: String): Option[String] =
    r.headers.toMap.get(r.headers.normalize(name)).flatMap(_.headOption)

  "ServerConfig.applyDefaults" - {
    "should stamp the built-in default headers and a Date" in {
      val r = ServerConfig().applyDefaults(Response.text("hi"))
      value(r, "Server") shouldBe Some("Apion")
      value(r, "X-Powered-By") shouldBe Some("Apion")
      value(r, "Cache-Control") shouldBe Some("no-store, no-cache, must-revalidate, max-age=0")
      value(r, "Date") should not be empty
    }

    "should not overwrite a header the response already set" in {
      val r = ServerConfig().applyDefaults(
        Response.text("hi", additionalHeaders = Seq("Server" -> "Custom")),
      )
      value(r, "Server") shouldBe Some("Custom")
      r.headers.toMap(r.headers.normalize("Server")) should have size 1
    }

    "should apply per-server custom defaults" in {
      val config = ServerConfig(defaultHeaders = Seq("Server" -> "Mine", "X-Custom" -> "yes"))
      val r      = config.applyDefaults(Response.text("hi"))
      value(r, "Server") shouldBe Some("Mine")
      value(r, "X-Custom") shouldBe Some("yes")
      // built-in defaults are not present under a custom config
      value(r, "X-Powered-By") shouldBe None
    }

    // Two configs no longer share global state — the whole point of the change.
    "should be independent between configs" in {
      val a = ServerConfig(defaultHeaders = Seq("Server" -> "A"))
      val b = ServerConfig(defaultHeaders = Seq("Server" -> "B"))
      value(a.applyDefaults(Response.text("x")), "Server") shouldBe Some("A")
      value(b.applyDefaults(Response.text("x")), "Server") shouldBe Some("B")
    }
  }

  "ServerConfig.seedContext" - {
    "should seed body-limit keys from the config" in {
      val config = ServerConfig(maxBodySize = 123L, bodyTimeout = 456)
      val ctx    = config.seedContext(Context.empty)
      ctx.get(Request.maxBodySizeKey) shouldBe Some(123L)
      ctx.get(Request.bodyTimeoutKey) shouldBe Some(456)
    }
  }

  "Response factories" - {
    "should not carry default headers on their own (applied at the send boundary)" in {
      value(Response.text("hi"), "Server") shouldBe None
      value(Response.text("hi"), "Content-Type") shouldBe Some("text/plain; charset=utf8")
    }
  }
}
