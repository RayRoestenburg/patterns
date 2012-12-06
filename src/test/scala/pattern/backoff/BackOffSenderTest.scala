package pattern.backoff

import org.scalatest.matchers.MustMatchers
import akka.testkit._
import akka.actor.{ ActorLogging, Actor, Props, ActorSystem }
import org.scalatest.{ WordSpec, BeforeAndAfterAll }
import scala.concurrent.Await
import akka.util.Timeout
import scala.concurrent.duration._
import language.postfixOps

/**
 * Simple test for BackOffSender.
 */
class BackOffSenderTest extends TestKit(ActorSystem("test")) with WordSpec with MustMatchers with BeforeAndAfterAll {

  implicit val timeoutDuration: FiniteDuration = 10 seconds
  implicit val timeout: Timeout = Timeout(timeoutDuration)
  implicit val ec = system.dispatcher
  val slotTime = 10 millis
  val ceiling = 10
  val stayAtCeiling = false
  val dangerousActorProps = Props(new DangerousActor(new DangerousResource))

  import BackOffProtocol._

  "A backoff sender" must {
    "send messages and receive responses after temporary error states using a dangerous actor" in {
      doBackOffFailureRecoveryTest(dangerousActorProps, "backOffToActor")
    }
    "send many messages and receive responses after temporary error states using a dangerous actor" in {
      doBackOffFailureManyMessagesTest(dangerousActorProps, "backOffToActorMany")
    }
  }

  def doBackOffFailureRecoveryTest(dangerousProps: Props, name: String) {
    val backOff = new ExponentialBackOff(slotTime, ceiling, stayAtCeiling)
    val backOffSender = system.actorOf(Props(new BackOffSender(dangerousProps, backOff)), name)
    backOff.resets must be(0)
    // any other message than 'err' puts the consumer in the error state
    backOffSender.tell(Msg(1, "set-err"), testActor)
    expectMsg(Msg(1, "set-err"))
    // the consumer will fail 8 times, after that the 'err' message will be accepted again.
    // the consumer will be out of error state
    backOffSender.tell(Msg(1, "err"), testActor)
    expectMsg(15 seconds, Msg(1, "err"))
    expectNoMsg()
    // two successful messages in the end, so it should reset twice
    backOff.resets must be(2)
    backOff.totalRetries must be(8)
    system.stop(backOffSender)
  }

  def doBackOffFailureManyMessagesTest(dangerousProps: Props, name: String) {
    val backOff = new ExponentialBackOff(slotTime, ceiling, stayAtCeiling)
    val backOffSender = system.actorOf(Props(new BackOffSender(dangerousProps, backOff)), name)
    backOffSender.tell(Msg(1, "set-err"), testActor)
    expectMsg(Msg(1, "set-err"))
    backOffSender.tell(Msg(1, "err"), testActor)
    // immediately send more, lets see if we get all the responses
    for (i ← 2 to 10) backOffSender.tell(Msg(i, "test" + i), testActor)
    receiveN(10, 10 seconds)
    system.stop(backOffSender)
  }

  override protected def afterAll() {
    system.shutdown()
  }
}
// provides some state that stays around to simulate errors
object IntermittentError {
  var errorState = false
  var errorCount = 0
}

// A 'Dangerous' resource which throws exceptions consecutively for a while.
class DangerousResource {
  def danger(data: String): String = {
    if (data == "err" && IntermittentError.errorState && IntermittentError.errorCount < 8) {
      IntermittentError.errorCount += 1
      throw new Exception("Danger, High Voltage!")
    } else if (data == "set-err") {
      IntermittentError.errorCount = 0
      IntermittentError.errorState = true
      data
    } else {
      IntermittentError.errorCount = 0
      IntermittentError.errorState = false
      data
    }
  }
}

// A 'Dangerous' actor which uses a dangerous resource to do its work.
// This actor is killed the moment it throws any Exception
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
