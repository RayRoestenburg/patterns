package pattern.backoff

import util.Random
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit
import concurrent.duration.{ FiniteDuration, Duration }

object ExponentialBackOff {
  def apply(slotTime: FiniteDuration, ceiling: Int, stayAtCeiling: Boolean) = {
    new ExponentialBackOff(slotTime, ceiling, stayAtCeiling)
  }
}
/**
 * Algorithm for truncated exponential back off.
 * http://en.wikipedia.org/wiki/Exponential_backoff
 *
 * Truncated interpreted as staying on the ceiling.
 * This class is not thread safe.
 * @param slotTime the time to wait associated with a slot
 * @param ceiling the ceiling of the slots (number of slots)
 * @param stayAtCeiling true=truncated/stays on ceiling, false resets after ceiling
 */
case class ExponentialBackOff(slotTime: FiniteDuration, ceiling: Int = 10, stayAtCeiling: Boolean = false,
                              slot: Int = 1, rand: Random = new Random(), waitTime: FiniteDuration = Duration.Zero,
                              retries: Int = 0, resets: Int = 0, totalRetries: Long = 0) {
  def isStarted = retries > 0

  def reset(): ExponentialBackOff = {
    copy(slot = 1, waitTime = Duration.Zero, resets = resets + 1, retries = 0)
  }

  def nextBackOff: ExponentialBackOff = {
    def time: FiniteDuration = slotTime * times
    def times = {
      val exp = rand.nextInt(slot + 1)
      math.round(math.pow(2, exp) - 1)
    }
    if (slot > ceiling && !stayAtCeiling) reset()
    else {
      val (newSlot, newWait: FiniteDuration) = if (slot > ceiling) {
        (ceiling, time)
      } else {
        (slot + 1, time)
      }
      copy(slot = newSlot,
        waitTime = newWait,
        retries = retries + 1,
        totalRetries = totalRetries + 1)
    }
  }
}