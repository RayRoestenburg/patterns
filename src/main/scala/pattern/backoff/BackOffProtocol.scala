package pattern.backoff

import akka.actor.ActorRef

/**
 * Message Protocol for the BackOffSender.
 */
object BackOffProtocol {
  /**
   * The message type that is used in the domain. The BackOffSender forwards this type of message
   * to its dangerous child, which performs some dangerous operation on it.
   */
  case class Msg(id: Long, data: String)

  /**
   * Used between the supervisor (the BackOffSender) and the child dangerous actor to track the original sender of the Msg message.
   * When a message fails for a while and succeeds after a number of retries, the original sender of the
   * Msg can be responded to.
   */
  case class TrackedMsg(msg: Msg, sender: ActorRef)

  /**
   * The dangerous actor needs to send this message to its supervisor (the BackOffSender) when it has successfully
   * processed the message. When the BackOffSender receives this message it resets the back off algorithm.
   */
  case class Sent(id: Long)
}
