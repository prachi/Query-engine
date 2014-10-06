package com.synup.http

import akka.actor.{Props, ActorLogging, Actor}
import com.synup.parsing._
import spray.http._
import akka.util.Timeout

import scala.concurrent.duration._

import akka.io.IO
import spray.can.Http
import spray.json.{JsonFormat, DefaultJsonProtocol}
import spray.httpx._
import spray.client.pipelining._
import spray.util._
import scala.language.postfixOps
import HttpMethods._
import scala.util.{Success, Failure}
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.event.Logging
import akka.io.IO
import spray.json.{JsonFormat, DefaultJsonProtocol}
import spray.can.Http
import spray.httpx.SprayJsonSupport
import spray.client.pipelining._
import spray.util._
import scala.concurrent.Future
import akka.util.Timeout
import spray.http._
import HttpMethods._
import com.redis.RedisClient
import java.net.URLDecoder


class AuthorityCallback extends Actor with ActorLogging {
	import context.dispatcher
  implicit val timeout = Timeout(5 seconds)
  val client = RedisClient("localhost", 6379)  

	def search(url: String): Future[HttpResponse] = {
		val pipeline = sendReceive
		val response: Future[HttpResponse] = pipeline {
      Get(Uri(url))
    }
    return response
  }

	def receive = {
		case _: Http.Connected => sender ! Http.Register(self)
		
		case HttpRequest(POST, Uri.Path("/authority_callback"), headers, entity: HttpEntity.NonEmpty, protocol) =>
			val keywordEntity = entity.asString.split("keyword=")(1)
      val keyword = keywordEntity.split("&engine")(0)
      val asciiKeyword = URLDecoder.decode(keyword, "utf-8")

      val element = entity.asString.split("json_callback=")
			val url = element(1).split("&html_callback")(0)
      val asciiUrl = URLDecoder.decode(url, "utf-8")

			val response = search(asciiUrl)
        response onSuccess {
          case x =>
            val queryRes = for {
              set <- client.hset(asciiKeyword, "DOM", x.entity.asString)
              Some(value) <- client.hget(asciiKeyword, "RequestID")
              actor = context.actorSelection("akka://SynupQueryEngine/user/DomQueryEngine")
              _ = actor ! QueryResponse("query_response", value, "listing", "authority", asciiKeyword, "Success", asciiKeyword)
            } yield value
				}
	}
}
