patterns
========



This repository contains Akka pattern examples. Right now it only contains an example
of using exponential backoff to retry sending messages to a producer,
while using a stopping supervisor strategy.

BackOffSender
-------------
This example shows how you can use an exponential backoff algorithm to back off from a failing endpoint. In the example a simple Camel endpoint is used. The BackOffSender sends message in request response style to the endpoint and starts to send in a delayed fashion when errors occur in the endpoint as described by the back off algorithm.

The Camel endpoint could fail intermittently and instead of retrying in a fixed interval the backoff mechanism is used as described [here](http://en.wikipedia.org/wiki/Exponential_backoff).

This example uses a supervisor, the <code>BackOffSender</code>, which creates a child producer actor based on a <code>Props</code> configuration object. The child actor is seen as a dangerous element and is used to send the actual messages to the endpoint. 

The <code>BackOffSender</code> uses a <code>SupervisorStrategy.stoppingStrategy</code>. If the child fails for any kind of reason it is terminated. The <code>BackOffSender</code> watches the child and creates a new child on receiving a <code>Terminated</code> message and continues to send messages with the new child. The terminated child sends its last failed message up to the supervisor (the <code>BackOffSender</code>). This failed message is retried by the <code>BackOffSender</code>. 

The failed message is scheduled when the child dies after a delay that is decided by the back off algorithm. When the message succeeds the back off algorithm is reset.  

The child actor used in the test simply throws an <code>IllegalArgumentException</code> when it receives a 500 status code to simulate a crash. The <code>BackOffSenderTest</code> shows a very minimal test to verify that the sender receives responses as is expected even though the consumer it is sent to fails for a number of consecutive calls. 