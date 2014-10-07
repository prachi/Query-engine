package com.synup

import akka.actor.{Props, ActorRef, ActorSystem}
import com.rabbitmq.client.Channel
import com.rabbitmq.client.AMQP.BasicProperties
import com.typesafe.scalalogging.slf4j.LazyLogging
import com.synup.parsing._
import com.synup.worker.DomQueryEngine
import scala.language.postfixOps
import com.thenewmotion.akka.rabbitmq._
import net.liftweb.json._

object Listener extends App with LazyLogging {
  import concurrent.duration._
  implicit val system = ActorSystem("SynupQueryEngine")

  val factory = new ConnectionFactory()
  factory.setHost("192.168.1.139")

  val connectionActor: ActorRef = system.actorOf(ConnectionActor.props(factory, reconnectionDelay = 10.seconds), "rabbitmq-subscriber")
  val domQueryEngineActor = system.actorOf(Props(new DomQueryEngine()), "DomQueryEngine")

  def setupChannel(channel: Channel, self: ActorRef) {
    val queue = channel.queueDeclare("query.request", true, false, false, null).getQueue
    val consumer = new DefaultConsumer(channel) {
      override def handleDelivery(consumerTag: String, envelope: Envelope, properties: BasicProperties, body: Array[Byte]) {
        println("received: " + fromBytes(body))
        domQueryEngineActor ! jsonParser(fromBytes(body))
      }
    }
    channel.basicConsume(queue, true, consumer)
  }
  val channelActor: ActorRef = connectionActor.createChannel(ChannelActor.props(setupChannel), Some("subscriber"))

  def fromBytes(x: Array[Byte]) = new String(x, "UTF-8")

  def jsonParser(x: String) = {
    implicit val formats = DefaultFormats
    val json = parse(x)
    json.extract[DomRequest]
  }
}
