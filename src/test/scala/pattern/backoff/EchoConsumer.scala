package pattern.backoff

import akka.camel.{ CamelMessage, Consumer }
import org.apache.camel.Exchange

/**
 * A Test consumer, responds with an 'error' code if an 'err' body is received and stays
 * in an error state for 8 consecutive messages. Can be used to test the BackOffSender with the DangerousProducer.
 */
class EchoConsumer(val endpointUri: String) extends Consumer {
  var errorState = false
  var errorCount = 0

  def receive = {
    case msg: CamelMessage if msg.bodyAs[String] == "err" && errorState && errorCount < 8 ⇒
      errorCount += 1
      sender ! msg.copy(headers = msg.headers ++ Map(Exchange.HTTP_RESPONSE_CODE -> 500))
    case msg: CamelMessage if msg.bodyAs[String] == "set-err" ⇒
      errorCount = 0
      sender ! msg.copy(headers = msg.headers ++ Map(Exchange.HTTP_RESPONSE_CODE -> 200))
      errorState = true
    case msg: CamelMessage ⇒
      errorCount = 0
      errorState = false
      sender ! msg.copy(headers = msg.headers ++ Map(Exchange.HTTP_RESPONSE_CODE -> 200))
  }
}