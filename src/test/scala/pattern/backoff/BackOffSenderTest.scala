package pattern.backoff

import org.scalatest.matchers.MustMatchers
import akka.testkit._
import akka.actor.{ Props, ActorSystem }
import org.scalatest.{ WordSpec, BeforeAndAfterAll }
import akka.util.Timeout
import scala.concurrent.duration._
import language.postfixOps

/**
 * Tests if the backOff algorithm is used to slow down the sending to the dangerous actor.
 */
class BackOffSenderTest extends TestKit(ActorSystem("test1")) with WordSpec with MustMatchers with BeforeAndAfterAll {

  implicit val timeoutDuration: FiniteDuration = 10 seconds
  implicit val timeout: Timeout = Timeout(timeoutDuration)
  implicit val ec = system.dispatcher
  val slotTime = 10 millis
  val ceiling = 10
  val stayAtCeiling = false
  val intermittentError = new IntermittentError()
  val dangerousProps = Props(new DangerousActor(new DangerousResource(intermittentError)))

  import BackOffProtocol._

  "A backoff sender" must {
    "send messages and receive responses after temporary error states using a dangerous actor" in {
      val backOff = new ExponentialBackOff(slotTime, ceiling, stayAtCeiling)
      val backOffSender = system.actorOf(Props(new BackOffSender(dangerousProps, backOff)))
      backOff.resets must be(0)
      // any other message than 'err' puts the consumer in the error state
      backOffSender.tell(Msg(1, "set-err"), testActor)
      expectMsg(Msg(1, "set-err"))
      // the consumer will fail 8 times, after that the 'err' message will be accepted again.
      // the consumer will be out of error state
      backOffSender.tell(Msg(1, "err"), testActor)
      expectMsg(15 seconds, Msg(1, "err"))
      // two successful messages in the end, so it should reset twice
      backOff.resets must be(2)
      backOff.totalRetries must be(8)
      system.stop(backOffSender)
    }
  }

  override protected def afterAll() {
    system.shutdown()
  }
}
