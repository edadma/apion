package io.github.edadma.apion

class AuthExcludePathsTests extends AnyBaseSpec {
  import AuthMiddleware.isExcluded

  "AuthMiddleware.isExcluded" - {
    val excludes = Set("/public")

    "should exclude the prefix itself" in {
      isExcluded("/public", excludes) shouldBe true
    }

    "should exclude paths nested under the prefix" in {
      isExcluded("/public/hello", excludes) shouldBe true
      isExcluded("/public/a/b/c", excludes) shouldBe true
    }

    // Regression: the old check used request.url.startsWith, so "/publicfoo" (and any
    // path merely sharing a character prefix) slipped past auth.
    "should NOT exclude a path that only shares a character prefix" in {
      isExcluded("/publicfoo", excludes) shouldBe false
      isExcluded("/public-api", excludes) shouldBe false
    }

    "should not exclude an unrelated path" in {
      isExcluded("/private", excludes) shouldBe false
      isExcluded("/secure/data", excludes) shouldBe false
    }

    "should honor multiple exclude prefixes" in {
      val multi = Set("/public", "/health")
      isExcluded("/health", multi) shouldBe true
      isExcluded("/health/live", multi) shouldBe true
      isExcluded("/admin", multi) shouldBe false
    }

    "should treat trailing slashes on the prefix as equivalent" in {
      isExcluded("/public/x", Set("/public/")) shouldBe true
    }
  }
}
