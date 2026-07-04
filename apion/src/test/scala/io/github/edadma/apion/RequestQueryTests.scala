package io.github.edadma.apion

import io.github.edadma.nodejs.ServerRequest
import scala.scalajs.js

class RequestQueryTests extends AnyBaseSpec {
  private def requestForUrl(url: String): Request = {
    val req = js.Dynamic.literal(
      method = "GET",
      url = url,
      headers = js.Dictionary[String](),
      on = (_: String, _: js.Function1[js.Any, Unit]) => js.Dynamic.literal(),
    )
    Request.fromServerRequest(req.asInstanceOf[ServerRequest])
  }

  "Query string parsing" - {
    // Regression: query was a Map[String, String], so repeated keys were silently dropped.
    "should preserve repeated keys in order" in {
      val req = requestForUrl("/search?a=1&a=2&a=3")
      req.query("a") shouldBe Seq("1", "2", "3")
    }

    "should decode '+' as space and percent-encoded values" in {
      val req = requestForUrl("/search?q=hello+world&city=S%C3%A3o+Paulo")
      req.query("q") shouldBe Seq("hello world")
      req.query("city") shouldBe Seq("São Paulo")
    }

    "should decode percent-encoded keys" in {
      val req = requestForUrl("/search?first%20name=ed")
      req.query("first name") shouldBe Seq("ed")
    }

    "should map a bare key with no '=' to a single empty value" in {
      val req = requestForUrl("/search?flag")
      req.query("flag") shouldBe Seq("")
    }

    "queryParam should return the first value or None" in {
      val req = requestForUrl("/search?a=1&a=2")
      req.queryParam("a") shouldBe Some("1")
      req.queryParam("missing") shouldBe None
    }

    "should be empty for a query-less URL" in {
      requestForUrl("/search").query shouldBe empty
    }
  }
}
