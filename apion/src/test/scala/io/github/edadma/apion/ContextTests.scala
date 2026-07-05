package io.github.edadma.apion

class ContextTests extends AnyBaseSpec {
  "Context" - {
    "should return None for an absent key" in {
      Context.empty.get(TypedKey[Int]("x")) shouldBe None
    }

    "should round-trip a value with its static type recovered (no cast at call site)" in {
      val key: TypedKey[Int] = TypedKey("count")
      val ctx                = Context.empty.updated(key, 42)
      val out: Option[Int]   = ctx.get(key) // typed as Option[Int]
      out shouldBe Some(42)
    }

    "should keep distinct keys of different types independent" in {
      val nKey = TypedKey[Int]("n")
      val sKey = TypedKey[String]("s")
      val ctx  = Context.empty.updated(nKey, 7).updated(sKey, "hi")
      ctx.get(nKey) shouldBe Some(7)
      ctx.get(sKey) shouldBe Some("hi")
    }

    "should treat two keys with the same name as distinct (identity, not name)" in {
      val a   = TypedKey[Int]("dup")
      val b   = TypedKey[Int]("dup")
      val ctx = Context.empty.updated(a, 1)
      ctx.get(a) shouldBe Some(1)
      ctx.get(b) shouldBe None
    }

    "updated should overwrite the same key and be immutable" in {
      val key  = TypedKey[String]("k")
      val base = Context.empty.updated(key, "first")
      val next = base.updated(key, "second")
      base.get(key) shouldBe Some("first")
      next.get(key) shouldBe Some("second")
    }

    "removed should drop a key; contains should reflect membership" in {
      val key = TypedKey[Int]("k")
      val ctx = Context.empty.updated(key, 1)
      ctx.contains(key) shouldBe true
      val without = ctx.removed(key)
      without.contains(key) shouldBe false
      without.get(key) shouldBe None
    }

    "apply should throw for a missing key" in {
      assertThrows[NoSuchElementException] {
        Context.empty(TypedKey[Int]("missing"))
      }
    }
  }
}
