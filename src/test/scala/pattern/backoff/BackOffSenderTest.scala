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

/**
 * Simple test for BackOffSender.
 */
class BackOffSenderTest extends TestKit(ActorSystem("test")) with WordSpec with MustMatchers with BeforeAndAfterAll {

  val camel = CamelExtension(system)

  "A backoff sender" must {
    "send messages and receive responses after temporary error states" in {
      implicit val timeoutDuration: FiniteDuration = 10 seconds
      implicit val timeout: Timeout = Timeout(timeoutDuration)
      implicit val ec = system.dispatcher
      val uri = "direct:test1"
      val consumer = system.actorOf(Props(new EchoConsumer(uri)), "echoConsumer")
      Await.ready(camel.activationFutureFor(consumer), timeoutDuration)
      val dangerousProps = Props(new DangerousProducer(uri))
      val backOffSender = system.actorOf(Props(new BackOffSender(dangerousProps, 50 millis, 10, false)), "BackOffSender")
      import BackOffProtocol._
      backOffSender.tell(Msg(1, "test"), testActor)
      expectMsg(Msg(1, "test"))

      backOffSender.tell(Msg(1, "err"), testActor)
      expectMsg(15 seconds, Msg(1, "err"))

      backOffSender.tell(Msg(1, "test"), testActor)
      expectMsg(Msg(1, "test"))

      backOffSender.tell(Msg(1, "err"), testActor)
      expectMsg(15 seconds, Msg(1, "err"))
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

    case msg: CamelMessage ⇒
      errorCount = 0
      sender ! msg.copy(headers = msg.headers ++ Map(Exchange.HTTP_RESPONSE_CODE -> 200))
      errorState = true
  }
}