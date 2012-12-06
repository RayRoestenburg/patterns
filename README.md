patterns
========



This repository contains Akka pattern examples. Right now it only contains an example
of using exponential backoff to retry sending messages to a producer,
while using a stopping supervisor strategy.

BackOffSender
-------------
This example shows how you can use an exponential backoff algorithm to back off from a failing endpoint. In the test example in <code>BackOffSenderTest</code> and <code>BackOffSendManyTest</code> a so-called dangerous actor uses a dangerous resource that might fail intermittently. The BackOffSender sends message in request response style to the dangerous actor and starts to send in a delayed fashion when errors occur in the endpoint as described by the back off algorithm.

The dangerous actor could fail intermittently and instead of retrying in a fixed interval a backoff algorithm is used as described [here](http://en.wikipedia.org/wiki/Exponential_backoff).

This example uses a supervisor (the <code>BackOffSender</code>) which creates and re-creates a child actor based on a <code>Props</code> configuration object. The child actor is seen as a dangerous element and is used to send the actual messages to the endpoint. 

The <code>BackOffSender</code> uses a <code>SupervisorStrategy.stoppingStrategy</code>. If the child fails for any kind of reason it is terminated. The <code>BackOffSender</code> watches the child and creates a new child on receiving a <code>Terminated</code> message and continues to send messages to the new child. Any message that was not acknowledged by the child is retried by the <code>BackOffSender</code>. 

The failed message is scheduled when the child dies after a delay that is decided by the back off algorithm. When the message succeeds (acknowledged by the child) the back off algorithm is reset.  

The child actor used in the tests simply throws an <code>Exception</code> while it is in an intermittent error state. The <code>BackOffSenderTest</code> and <code>BackOffSendManyTest</code> show some very minimal tests to verify that the sender sends requests and receives responses as is expected even though the dangerous actor child it is sending to fails for a number of consecutive calls. The <code>ExponentialBackOffTest</code> shows a small test for the back off algorithm.