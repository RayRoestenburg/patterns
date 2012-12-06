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
 * @param dangerousProps the props to create and re-create the 'dangerous actor'
 * @param backOff the backoff algotihm
 */
class BackOffSender(dangerousProps: Props, backOff: BackOff) extends Actor with ActorLogging {
  import BackOffProtocol._
  // Any crashed actor will be stopped.
  override def supervisorStrategy = SupervisorStrategy.stoppingStrategy
  // Create and watch the dangerous actor.
  var dangerousActor = context.actorOf(dangerousProps)
  context.watch(dangerousActor)
  // messages that have not been acknowledged (yet)
  var possiblyFailed = SortedMap[Long, FailedMsg]()
  implicit val ec = context.system.dispatcher
  var waitTime = 0 millis

  def receive = {
    case msg: Msg ⇒
      val trackedMsg = TrackedMsg(msg, sender)
      possiblyFailed = possiblyFailed + (trackedMsg.msg.id -> FailedMsg(trackedMsg))
      // only send immediately if the backOff was not needed yet,
      // or if the backOff was reset by an acknowledgement. otherwise schedule with delay
      // specified by backOff.
      if (!backOff.isStarted) {
        dangerousActor.forward(trackedMsg)
      } else {
        context.system.scheduler.scheduleOnce(waitTime) {
          self ! trackedMsg
        }
      }
    case trackedMsg: TrackedMsg ⇒
      dangerousActor.tell(trackedMsg, trackedMsg.sender)
    case Terminated(failedRef) ⇒
      // re-create and watch
      dangerousActor = context.actorOf(dangerousProps)
      context.watch(dangerousActor)
      // if there where failed messages scheduled them, after the next wait in the back off alg.
      // if one of these fails, it will result in another termination, and a possibly higher delay based on backOff alg.
      waitTime = backOff.nextWait
      context.system.scheduler.scheduleOnce(waitTime) {
        possiblyFailed.keySet.foreach { redoId ⇒
          possiblyFailed.get(redoId).foreach { redo ⇒
            // send to self makes sure that we get a live dangerousActor
            self ! redo.trackedMsg
          }
        }
      }
    case Sent(idKey) ⇒
      possiblyFailed = possiblyFailed - idKey
      backOff.reset()
  }
}