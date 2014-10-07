package com.synup.worker

import akka.actor.{Props, ActorRef, ActorSystem, ActorLogging, Actor}
import com.rabbitmq.client.Channel
import com.rabbitmq.client.AMQP.BasicProperties
import com.typesafe.scalalogging.slf4j.LazyLogging
import com.synup.parsing._
import com.synup.worker.DomQueryEngine
import scala.language.postfixOps
import com.thenewmotion.akka.rabbitmq._
import net.liftweb.json._
import org.json4s.native.Serialization.write
import org.json4s.DefaultFormats

class Publisher(implicit system: ActorSystem)  extends Actor with ActorLogging {

  import concurrent.duration._
  val factory = new ConnectionFactory()
  factory.setHost("192.168.1.139")
  val connectionActor: ActorRef = system.actorOf(ConnectionActor.props(factory, reconnectionDelay = 10.seconds), "rabbitmq-publisher")
  def setupPublisher(channel: Channel, self: ActorRef) {
    val queue = channel.queueDeclare("query.response", true, false, false, null).getQueue
  }
  connectionActor ! CreateChannel(ChannelActor.props(setupPublisher), Some("publisher"))
  def toBytes(x: String) = x.getBytes("UTF-8")
  implicit val formats = DefaultFormats


  def receive = {
    case queryResponse: QueryResponse =>
      val publisher = context.actorSelection("akka://SynupQueryEngine/user/rabbitmq-publisher/publisher")
      def publish(channel: Channel) {
        channel.basicPublish("", "query.response", null, toBytes(write(queryResponse)))
      }
      publisher ! ChannelMessage(publish, dropIfNoChannel = false)
  }    

}
