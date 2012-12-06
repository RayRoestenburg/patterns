package pattern.backoff

import akka.actor._
import akka.actor.Terminated
import scala.Some
import concurrent.duration.FiniteDuration
import akka.camel.{ CamelMessage, Producer }
import org.apache.camel.Exchange
import collection.immutable.SortedMap

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
  var dangerousActor = context.actorOf(dangerousProps, "danger")
  context.watch(dangerousActor)
  var possiblyFailed = SortedMap[Long, FailedMsg]()
  implicit val ec = context.system.dispatcher

  def receive = {
    case msg: Msg ⇒
      val trackedMsg = TrackedMsg(msg, sender)
      possiblyFailed = possiblyFailed + (trackedMsg.msg.id -> FailedMsg(trackedMsg))
      dangerousActor.forward(TrackedMsg(msg, sender))
    case Terminated(failedRef) ⇒
      log.debug("Terminated, new child.")
      // re-create and watch
      dangerousActor = context.actorOf(dangerousProps, "danger")
      context.watch(dangerousActor)
      // if there where failed messages scheduled them, after the next wait in the back off alg.
      val wait = backOff.nextWait
      log.debug("Scheduling once in the future after {}", wait)
      context.system.scheduler.scheduleOnce(wait) {
        possiblyFailed.keySet.foreach { redoId ⇒
          possiblyFailed.get(redoId).foreach { redo ⇒
            log.debug("Sending {} again to dangerous actor", redo)
            dangerousActor.tell(redo.trackedMsg, redo.trackedMsg.sender)
          }
        }
      }
    case Sent(idKey) ⇒
      possiblyFailed = possiblyFailed - idKey
      backOff.reset()
  }
}

/**
 * A 'Dangerous' producer. It crashes when it receives a 500 status from the camel endpoint.
 * The producer translates the 'Msg' type that is part of the domain to a CamelMessage.
 */
class DangerousProducer(val endpointUri: String) extends Actor with Producer with ActorLogging {
  import BackOffProtocol._

  // transforms a TrackedMsg to a CamelMessage.
  override protected def transformOutgoingMessage(msg: Any) = {
    msg match {
      case trackedMsg: TrackedMsg ⇒
        log.debug("Sending {} from dangerous producer", trackedMsg)
        CamelMessage(trackedMsg.msg.data, Map(CamelMessage.MessageExchangeId -> trackedMsg.msg.id.toString))
    }
  }
  // transforms a CamelMessage back to a Msg. correlates response to request,
  // removes the message from the possibly failed messages and
  // sends a Sent() message to the BackOffSender to indicate Success
  override protected def transformResponse(msg: Any) = {
    msg match {
      case cmsg: CamelMessage ⇒
        val status = cmsg.headerAs[Int](Exchange.HTTP_RESPONSE_CODE).get
        if (status == 500) throw new IllegalArgumentException("500 error")
        val response = cmsg.headerAs[Long](CamelMessage.MessageExchangeId).map { idKey ⇒
          context.parent ! Sent(idKey)
          Msg(idKey, cmsg.bodyAs[String])
        }.get
        response
    }
  }
}