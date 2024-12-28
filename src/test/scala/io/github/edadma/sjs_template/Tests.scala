package io.github.edadma.sjs_template

import org.scalatest._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class Tests extends AnyFreeSpec with Matchers:
  "test" in {
    List(1, 2, 3) mkString "\n" shouldBe
      """
        |1
        |2
        |3
        """.trim.stripMargin
  }
