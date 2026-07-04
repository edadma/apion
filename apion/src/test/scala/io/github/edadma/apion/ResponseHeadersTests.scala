package io.github.edadma.apion

class ResponseHeadersTests extends AnyBaseSpec {
  "ResponseHeaders" - {
    "should be case-insensitive on get and preserve canonical casing on output" in {
      val h = ResponseHeaders.empty.add("content-type", "text/plain")
      h.get("Content-Type") shouldBe Some("text/plain")
      h.toMap.keys should contain("Content-Type")
    }

    "should overwrite non-multi headers, keeping only the last value" in {
      val h = ResponseHeaders.empty
        .add("X-Test", "first")
        .add("X-Test", "second")
      h.toMap("X-Test") shouldBe List("second")
    }

    "should retain multiple Set-Cookie headers" in {
      val h = ResponseHeaders.empty
        .add("Set-Cookie", "a=1")
        .add("Set-Cookie", "b=2")
      h.toMap("Set-Cookie") should contain allOf ("a=1", "b=2")
      h.toMap("Set-Cookie") should have size 2
    }

    // Regression: multiHeader set previously contained the typo "wwww-authenticate"
    // (four w's), so multiple WWW-Authenticate challenges silently overwrote each other.
    "should retain multiple WWW-Authenticate headers" in {
      val h = ResponseHeaders.empty
        .add("WWW-Authenticate", "Bearer")
        .add("WWW-Authenticate", "Basic")
      h.toMap("WWW-Authenticate") should contain allOf ("Bearer", "Basic")
      h.toMap("WWW-Authenticate") should have size 2
    }
  }
}
