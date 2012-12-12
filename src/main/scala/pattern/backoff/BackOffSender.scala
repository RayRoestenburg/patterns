package pattern.backoff

import akka.actor._
import akka.actor.Terminated
import scala.concurrent.duration._
import scala.collection.immutable.SortedMap

/**
 * Sends messages to a child actor, backs off exponentially when errors occur.
 * This actor uses a stopping strategy.
 * It creates a child actor from given Props that might do something dangerous.
 * when the child crashes it terminates the child and recreates a new one from Props to replace
 * the crashed child and continues to send using the new child.
 */
class BackOffSender(dangerousProps: Props, slotTime: FiniteDuration = 10 millis, ceiling: Int = 10, stayAtCeiling: Boolean = false) extends Actor with ActorLogging {
  import BackOffProtocol._
  var backOff = ExponentialBackOff(slotTime, ceiling, stayAtCeiling)

  override def supervisorStrategy = SupervisorStrategy.stoppingStrategy
  var dangerousActor = context.actorOf(dangerousProps)
  context.watch(dangerousActor)
  // messages that have not been acknowledged (yet)
  var possiblyFailed = Vector[TrackedMsg]()

  implicit val ec = context.system.dispatcher
  var waitTime = Duration.Zero
  val Tick = "tick"

  def receive = {
    case msg: Msg ⇒
      val trackedMsg = TrackedMsg(msg, sender)
      possiblyFailed = possiblyFailed :+ trackedMsg
      // only send immediately if the backOff was not needed yet, or reset by an ack.
      // otherwise schedule with delay specified by backOff.
      if (!backOff.isStarted) {
        dangerousActor.forward(trackedMsg)
      } else {
        context.system.scheduler.scheduleOnce(backOff.waitTime, self, Tick)
      }
    case Tick ⇒
      possiblyFailed.foreach(trackedMsg ⇒ dangerousActor.tell(trackedMsg, trackedMsg.sender))
    case Terminated(failedRef) ⇒
      // re-create and watch
      dangerousActor = context.actorOf(dangerousProps)
      context.watch(dangerousActor)
      // if there where failed messages schedule after the next wait in the back off alg.
      // if one of these fails, it will result in another termination and a possibly higher delay
      backOff = backOff.nextBackOff
      context.system.scheduler.scheduleOnce(backOff.waitTime, self, Tick)

    case Sent(idKey) ⇒
      //Ack of the dangerous actor. Remove successful message and reset the backOff.
      possiblyFailed = possiblyFailed.filterNot(tracked ⇒ tracked.msg.id == idKey)
      backOff = backOff.reset()
  }
}