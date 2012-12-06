package pattern.backoff

import akka.actor.{ ActorLogging, Actor }
import akka.camel.{ CamelMessage, Producer }
import org.apache.camel.Exchange

/**
 * A 'Dangerous' producer, a test example of a Camel producer that crashes on a 500 response code.
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

  /**
   * Transforms a CamelMessage back to a Msg. correlates response to request,
   * removes the message from the possibly failed messages and
   *  sends a Sent() message to the BackOffSender to indicate Success
   */
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