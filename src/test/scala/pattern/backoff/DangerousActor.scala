package pattern.backoff

import akka.actor.{ ActorLogging, Actor }

// provides some nasty state that stays around to simulate errors
class IntermittentError {
  var errorState = false
  var errorCount = 0
}

// A 'Dangerous' resource which throws exceptions based on an intermittent error.
class DangerousResource(intermittentError: IntermittentError) {
  def danger(data: String): String = {
    if (data == "err" && intermittentError.errorState && intermittentError.errorCount < 8) {
      intermittentError.errorCount += 1
      throw new Exception("Danger, High Voltage!")
    } else if (data == "set-err") {
      intermittentError.errorCount = 0
      intermittentError.errorState = true
      data
    } else {
      intermittentError.errorCount = 0
      intermittentError.errorState = false
      data
    }
  }
}

/**
 * A 'Dangerous' actor which uses a dangerous resource to do its work.
 * This actor is killed the moment it throws any Exception
 */
class DangerousActor(dangerousResource: DangerousResource) extends Actor with ActorLogging {

  import BackOffProtocol._
  def receive = {
    case trackedMsg: TrackedMsg ⇒
      // do the dangerous operation
      val response = dangerousResource.danger(trackedMsg.msg.data)
      //indicate to supervisor that the dangerous operation was successful
      context.parent ! Sent(trackedMsg.msg.id)
      // respond to sender with the result
      sender ! trackedMsg.msg.copy(data = response)
  }
}
