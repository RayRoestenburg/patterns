package pattern.backoff

import akka.actor.{ ActorSystem, Props }
import pattern.backoff.BackOffProtocol.Msg
import akka.testkit.TestKit
import org.scalatest.{ BeforeAndAfterAll, WordSpec }
import org.scalatest.matchers.MustMatchers
import concurrent.duration.FiniteDuration
import akka.util.Timeout
import scala.concurrent.duration._
import language.postfixOps

/**
 * Test for sending many messages while an intermittent error is handled by backOff mechanism
 */
class BackOffSendManyTest extends TestKit(ActorSystem("test2")) with WordSpec with MustMatchers with BeforeAndAfterAll {

  implicit val timeoutDuration: FiniteDuration = 10 seconds
  implicit val timeout: Timeout = Timeout(timeoutDuration)
  implicit val ec = system.dispatcher
  val slotTime = 10 millis
  val ceiling = 10
  val stayAtCeiling = false
  val intermittentError = new IntermittentError
  val dangerousProps = Props(new DangerousActor(new DangerousResource(intermittentError)))

  "A backoff sender" must {
    "send many messages and receive responses after temporary error states using a dangerous actor" in {
      val backOff = new ExponentialBackOff(slotTime, ceiling, stayAtCeiling)
      val backOffSender = system.actorOf(Props(new BackOffSender(dangerousProps, backOff)))
      backOffSender.tell(Msg(1, "set-err"), testActor)
      expectMsg(Msg(1, "set-err"))
      backOffSender.tell(Msg(1, "err"), testActor)
      // immediately send more, lets see if we get all the responses
      for (i ← 2 to 10) backOffSender.tell(Msg(i, "test" + i), testActor)
      val seq = receiveN(10, 10 seconds)
      seq.contains(Msg(1, "err")) must be(true)
      for (i ← 2 to 10) seq.contains((Msg(i, "test" + i))) must be(true)
      system.stop(backOffSender)
    }
  }

  override protected def afterAll() {
    system.shutdown()
  }

}
