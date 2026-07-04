package io.github.edadma.apion

class ResponseDefaultHeadersTests extends AnyBaseSpec {
  // These tests mutate global default-header state, so each restores it afterward.
  private def headerValue(r: Response, name: String): Option[String] =
    r.headers.toMap.get(r.headers.normalize(name)).flatMap(_.headOption)

  "Response default headers" - {
    "should apply built-in defaults" in {
      val r = Response.text("hi")
      headerValue(r, "Server") shouldBe Some("Apion")
      headerValue(r, "X-Powered-By") shouldBe Some("Apion")
    }

    "configure should override an existing default by name without duplicating" in {
      try {
        Response.configure(Seq("Server" -> "Custom"))
        val r = Response.text("hi")
        headerValue(r, "Server") shouldBe Some("Custom")
        r.headers.toMap(r.headers.normalize("Server")) should have size 1
      } finally Response.resetDefaultHeaders()
    }

    "configure should add a brand-new default header" in {
      try {
        Response.configure(Seq("X-Custom" -> "yes"))
        headerValue(Response.text("hi"), "X-Custom") shouldBe Some("yes")
      } finally Response.resetDefaultHeaders()
    }

    // Regression: resetDefaultHeaders previously reset to a different (lowercased)
    // set than the initial defaults, so a configure+reset round trip changed state.
    "resetDefaultHeaders should restore the original defaults exactly" in {
      Response.configure(Seq("Server" -> "Custom", "X-Custom" -> "yes"))
      Response.resetDefaultHeaders()
      val r = Response.text("hi")
      headerValue(r, "Server") shouldBe Some("Apion")
      headerValue(r, "X-Custom") shouldBe None
    }
  }
}
