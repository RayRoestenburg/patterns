package pattern.backoff

import akka.actor._
import akka.actor.Terminated
import scala.Some
import concurrent.duration.FiniteDuration
import akka.camel.{ CamelMessage, Producer }
import org.apache.camel.Exchange

/**
 * Sends messages to a child actor, backs off exponentially when errors occur.
 * This actor uses a stopping strategy.
 * It creates a child actor from given Props that might do something dangerous.
 * when the child crashes it terminates the child and recreates a new one from Props to replace
 * the crashed child and continues to send using the new child.
 * @param dangerousProps the props to create and re-create the 'dangerous actor'
 * @param backOff the backoff algotihm
 */
class BackOffSender(dangerousProps: Props, backOff: BackOff) extends Actor {
  import BackOffProtocol._
  // Any crashed actor will be stopped.
  override def supervisorStrategy = SupervisorStrategy.stoppingStrategy
  // Create and watch the dangerous actor.
  var dangerousActor = context.actorOf(dangerousProps, "danger")
  context.watch(dangerousActor)
  var backedUp = Vector[TrackedMsg]()
  var possibleRedoMsg: Option[FailedMsg] = None
  implicit val ec = context.system.dispatcher

  def receive = {
    case msg: Msg ⇒
      // only send to child if there is currently no redo scheduled or pending resolution.
      if (possibleRedoMsg.isEmpty) {
        backedUp.foreach(trackedMsg ⇒ dangerousActor.forward(trackedMsg))
        backedUp = Vector()
        dangerousActor.forward(TrackedMsg(msg, sender))
      } else backedUp = backedUp :+ TrackedMsg(msg, sender)
    case Terminated(failedRef) ⇒
      // re-create and watch
      dangerousActor = context.actorOf(dangerousProps, "danger")
      context.watch(dangerousActor)
      // if there was a failed message schedule it, after the next wait in the back off alg.
      possibleRedoMsg.foreach { redo ⇒
        val wait = backOff.nextWait
        context.system.scheduler.scheduleOnce(wait) {
          dangerousActor.tell(redo.trackedMsg, redo.trackedMsg.sender)
        }
      }
    case msg: FailedMsg ⇒
      possibleRedoMsg = Some(msg)
    case Sent(idKey) ⇒
      possibleRedoMsg.filter(_.trackedMsg.msg.id == idKey).foreach { failedMsg ⇒
        possibleRedoMsg = None
      }
      backOff.reset()
  }
}

/**
 * A 'Dangerous' producer. It crashes when it receives a 500 status from the camel endpoint.
 * The producer translates the 'Msg' type that is part of the domain to a CamelMessage.
 */
class DangerousProducer(val endpointUri: String) extends Actor with Producer {
  import BackOffProtocol._

  var possiblyFailed: Map[Long, FailedMsg] = Map()
  // transforms a TrackedMsg to a CamelMessage.
  override protected def transformOutgoingMessage(msg: Any) = {
    msg match {
      case trackedMsg: TrackedMsg ⇒
        possiblyFailed = possiblyFailed + (trackedMsg.msg.id -> FailedMsg(trackedMsg))
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
          possiblyFailed = possiblyFailed - idKey
          context.parent ! Sent(idKey)
          Msg(idKey, cmsg.bodyAs[String])
        }.get
        response
    }
  }
  // sends the failed messages (if there are any) to its supervisor (the BackOffSender)
  override def postStop() {
    possiblyFailed.values.foreach(context.parent ! _)
    possiblyFailed = Map()
    super.postStop()
  }
}