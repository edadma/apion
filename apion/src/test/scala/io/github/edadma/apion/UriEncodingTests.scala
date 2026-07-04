package io.github.edadma.apion

class UriEncodingTests extends AnyBaseSpec {
  "decodeURIComponent" - {
    // Regression: the previous hand-rolled decoder decoded each %xx byte to a
    // single char, corrupting any multi-byte UTF-8 sequence.
    "should decode multi-byte UTF-8 sequences correctly" in {
      decodeURIComponent("%E2%82%AC") shouldBe "€" // €
      decodeURIComponent("caf%C3%A9") shouldBe "café" // café
      decodeURIComponent("%F0%9F%98%80") shouldBe "😀" // 😀
    }

    "should treat '+' literally (JS semantics), not as a space" in {
      decodeURIComponent("a+b") shouldBe "a+b"
    }

    "should return malformed input unchanged instead of throwing" in {
      decodeURIComponent("%") shouldBe "%"
      decodeURIComponent("%zz") shouldBe "%zz"
    }
  }

  "decodeFormComponent" - {
    "should decode '+' as a space" in {
      decodeFormComponent("hello+world") shouldBe "hello world"
    }

    "should decode an encoded literal plus" in {
      decodeFormComponent("1%2B1") shouldBe "1+1"
    }

    "should decode multi-byte UTF-8 with '+' spaces" in {
      decodeFormComponent("caf%C3%A9+bar") shouldBe "café bar"
    }
  }

  "encodeURIComponent" - {
    "should percent-encode reserved characters and UTF-8" in {
      encodeURIComponent("a b/c") shouldBe "a%20b%2Fc"
      encodeURIComponent("café") shouldBe "caf%C3%A9"
    }

    "should round-trip with decodeURIComponent" in {
      val s = "a/b?c=d e&f=é€"
      decodeURIComponent(encodeURIComponent(s)) shouldBe s
    }
  }
}
