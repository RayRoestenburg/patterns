package pattern.backoff

import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import scala.concurrent.duration._
import language.postfixOps

/**
 * Test for ExponentialBackOff
 */
class ExponentialBackOffTest extends WordSpec with MustMatchers {
  val backoff = new ExponentialBackOff(slotTime = 10 millis, ceiling = 10)

  "Exponential Truncated Backoff" must {

    "return the first delay between 0 and 1 times the slotTime" in {
      val delay = backoff.nextWait
      delay must (be(0 millis) or be(10 millis))
      backoff.resets must be(0)
    }
    "return the next delay between 0, 1, 3 times the slotTime" in {
      val delay = backoff.nextWait
      delay must (be(0 millis) or be(10 millis) or be(30 millis))
      backoff.resets must be(0)
    }
    "return the next delay between 0, 1, 3, 7 times the slotTime" in {
      val delay = backoff.nextWait
      delay must (be(0 millis) or be(10 millis) or be(30 millis) or be(70 millis))
      backoff.resets must be(0)
    }
    "reset when stayAtCeiling is false and the slot is beyond the ceiling" in {
      var delay = 0 millis

      for (i ← 1 to 8) {
        delay = backoff.nextWait
      }

      backoff.resets must be(1)
      delay must (be(0 millis) or be(10 millis))
    }
  }
}
