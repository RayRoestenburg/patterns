package pattern.backoff

import org.scalatest.matchers.MustMatchers
import akka.testkit._
import akka.actor.{ Props, ActorSystem }
import org.scalatest.{ WordSpec, BeforeAndAfterAll }
import akka.camel.{ CamelExtension, CamelMessage, Consumer }
import scala.concurrent.Await
import akka.util.Timeout
import scala.concurrent.duration._
import org.apache.camel.Exchange
import language.postfixOps

/**
 * Simple test for BackOffSender.
 */
class BackOffSenderTest extends TestKit(ActorSystem("test")) with WordSpec with MustMatchers with BeforeAndAfterAll {

  val camel = CamelExtension(system)
  implicit val timeoutDuration: FiniteDuration = 10 seconds
  implicit val timeout: Timeout = Timeout(timeoutDuration)
  implicit val ec = system.dispatcher
  val uri = "direct:test1"
  val slotTime = 10 millis
  val ceiling = 10
  val stayAtCeiling = false
  val consumer = system.actorOf(Props(new EchoConsumer(uri)), "echoConsumer")
  Await.ready(camel.activationFutureFor(consumer), timeoutDuration)
  val dangerousProps = Props(new DangerousProducer(uri))
  val backOff = new ExponentialBackOff(slotTime, ceiling, stayAtCeiling)

  val backOffSender = system.actorOf(Props(new BackOffSender(dangerousProps, backOff)), "BackOffSender")
  import BackOffProtocol._

  "A backoff sender" must {
    "send messages and receive responses after temporary error states" in {
      // any other message than 'err' puts the consumer in the error state
      backOffSender.tell(Msg(1, "reset-err"), testActor)
      expectMsg(Msg(1, "reset-err"))
      // the consumer will fail 7 times, after that the 'err' message will be accepted again.
      // the consumer will be out of error state
      backOffSender.tell(Msg(1, "err"), testActor)
      expectMsg(15 seconds, Msg(1, "err"))

    }
    "send many messages and receive responses after temporary error states" in {
      backOffSender.tell(Msg(1, "reset-err"), testActor)
      expectMsg(Msg(1, "reset-err"))
      // the consumer will fail 7 times, after that the 'err' message will be accepted again.
      // the consumer will be out of error state
      backOffSender.tell(Msg(1, "err"), testActor)
      for (i ← 2 to 10) backOffSender.tell(Msg(i, "test" + i), testActor)
      expectMsg(15 seconds, Msg(1, "err"))
      var count = 0
      receiveWhile(max = 2 seconds) {
        case msg: Msg if !msg.data.endsWith("10") ⇒
          count += 1
      }
      // receive while does not count test10
      count must be(8)
    }
  }
  override protected def afterAll() {
    system.shutdown()
  }
}

/**
 * Test consumer, responds with an 'error' code if an 'err' body is received and stays
 * in an error state for 8 consecutive messages.
 */
class EchoConsumer(val endpointUri: String) extends Consumer {
  var errorState = false
  var errorCount = 0

  def receive = {
    case msg: CamelMessage if msg.bodyAs[String] == "err" && errorState && errorCount < 8 ⇒
      errorCount += 1
      sender ! msg.copy(headers = msg.headers ++ Map(Exchange.HTTP_RESPONSE_CODE -> 500))

    case msg: CamelMessage if msg.bodyAs[String] == "reset-err" ⇒
      errorCount = 0
      sender ! msg.copy(headers = msg.headers ++ Map(Exchange.HTTP_RESPONSE_CODE -> 200))
      errorState = true
    case msg: CamelMessage ⇒
      errorCount = 0
      errorState = false
      sender ! msg.copy(headers = msg.headers ++ Map(Exchange.HTTP_RESPONSE_CODE -> 200))
  }
}