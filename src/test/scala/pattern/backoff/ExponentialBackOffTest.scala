package pattern.backoff

import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import scala.concurrent.duration._
import language.postfixOps

/**
 * Test for ExponentialBackOff
 */
class ExponentialBackOffTest extends WordSpec with MustMatchers {
  val backOff = new ExponentialBackOff(slotTime = 10 millis, ceiling = 10)

  "Exponential Truncated Backoff" must {

    "return the first delay between 0 and 1 times the slotTime" in {
      val next = backOff.nextBackOff
      next.waitTime must (be(0 millis) or be(10 millis))
      next.resets must be(0)
    }
    "return the next delay between 0, 1, 3 times the slotTime" in {
      val next = backOff.nextBackOff
      next.waitTime must (be(0 millis) or be(10 millis) or be(30 millis))
      next.resets must be(0)
    }
    "return the next delay between 0, 1, 3, 7 times the slotTime" in {
      val next = backOff.nextBackOff
      next.waitTime must (be(0 millis) or be(10 millis) or be(30 millis) or be(70 millis))
      next.resets must be(0)
    }
    "reset when stayAtCeiling is false and the slot is beyond the ceiling" in {
      var next = backOff
      for (i ← 1 to 11) {
        next = next.nextBackOff
      }
      next.resets must be(1)
      next.waitTime must (be(0 millis) or be(10 millis))
    }
  }
}
