package pattern.backoff

import util.Random
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit
import concurrent.duration.{ FiniteDuration, Duration }

trait BackOff {
  def reset()
  def isStarted: Boolean
  def nextWait: FiniteDuration
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
class ExponentialBackOff(slotTime: FiniteDuration, ceiling: Int, stayAtCeiling: Boolean = false) extends BackOff {
  private[this] val rand = new Random()
  private[this] var slot = 1
  private[this] var countResets = 0
  private[this] var countRetries = 0
  private[this] var countTotalRetries = 0
  def isStarted = countRetries > 0
  /**
   * Resets this back off
   */
  def reset() {
    slot = 1
    countResets += 1
    countRetries = 0
  }

  def resets = countResets
  def totalRetries = countTotalRetries
  /**
   * Returns the next wait time.
   * (if stayAtCeiling is true, reset to start again)
   */
  def nextWait: FiniteDuration = {
    def time = slotTime * times
    def times = {
      slot += 1
      val exp = rand.nextInt(slot)
      math.round(math.pow(2, exp) - 1)
    }
    countRetries += 1
    countTotalRetries += 1
    if (slot > ceiling) {
      if (stayAtCeiling) {
        slot = ceiling
        time
      } else {
        reset()
        nextWait
      }
    } else {
      time
    }
  }
}