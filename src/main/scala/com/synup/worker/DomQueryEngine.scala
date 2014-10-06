package com.synup.worker

import akka.actor.{Props, ActorLogging, Actor, ActorSystem, ActorRef}
import com.synup.http._
import com.synup.db._
import com.synup.parsing._
import spray.http.HttpResponse
import akka.io.IO
import spray.can.Http

class DomQueryEngine (implicit system: ActorSystem) extends Actor with ActorLogging {

  val domEngine = context.actorOf(Props[DomEngine], "domEngine")
  val publisher = system.actorOf(Props(new Publisher()), "publisherActor")
  val authorityCallback = context.actorOf(Props[AuthorityCallback], "authorityCallback")
  IO(Http) ! Http.Bind(authorityCallback, interface = "162.243.247.227", port = 8080)

  def receive = {
    case domRequest: DomRequest =>
      domEngine ! domRequest
    case queryResponse: QueryResponse =>
    	log.info(s"received queryResponse in DomQueryEngine")
      publisher ! queryResponse
        
  }
}
