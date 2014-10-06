package com.synup.db

import akka.actor.{Props, ActorLogging, Actor}
import com.synup.parsing._
import com.synup.http._
import com.synup.worker._
import com.redis.RedisClient
import com.redis.serialization.{KeyValuePair, Stringified}
import akka.util.Timeout
import scala.concurrent.duration._
import scala.collection.{SeqLike, GenTraversableOnce, GenTraversable}
import scala.language.postfixOps

class DomEngine extends Actor with ActorLogging {
	import scala.concurrent.ExecutionContext.Implicits.global
	implicit val timeout = Timeout(5 seconds)
	val client = RedisClient("localhost", 6379)  // ip of box on which redis is running
	val domGetter = context.actorOf(Props[DomGetter], "domGetter")

	def receive = {	
		case DomRequest(msg_type, request_id, source, site, url) => 
			if (site == "authority"){
				val check = client.hexists(url, "DOM")
				check map {
					case check if (check) =>
						log.info(s"$url already present, updating requestId with $request_id")
						for {
							_ <- client.hset(url, "RequestID", request_id)
							Some(value) <- client.hget(url, "RequestID")
						} yield value
					case check: Boolean =>
					log.info(s"$url is not present in cache, fetching DOM")
					domGetter ! DomRequest(msg_type, request_id, source, site, url)	
				}
			}
			else {
			val check = client.hexists(url, "DOM")
			check map {
				case check if (check) =>
					log.info(s"$url already present, updating requestId with $request_id")
					for {
						_ <- client.hset(url, "RequestID", request_id)
						Some(value) <- client.hget(url, "RequestID")
						actor = context.actorSelection("akka://SynupQueryEngine/user/DomQueryEngine")
						_ = actor ! QueryResponse(msg_type, value, source, site, url, "Success", url)
					} yield value
				case check: Boolean =>
					log.info(s"$url is not present in cache, fetching DOM")
					domGetter ! DomRequest(msg_type, request_id, source, site, url)	
			}
		}
		
		case DomResponse(DomRequest(msg_type, request_id, source, site, url), dom) =>
			if (site == "authority"){
				val queryRes = for {
				set <- client.hmset(url, Map("RequestID" -> request_id, "DOM" -> dom))
				expire <- client.expire(url, 86400)
				} yield expire
			} else {
				val queryRes = for {
				set <- client.hmset(url, Map("RequestID" -> request_id, "DOM" -> dom))
				expire <- client.expire(url, 86400)
				Some(value) <- client.hget(url, "RequestID")
				actor = context.actorSelection("akka://SynupQueryEngine/user/DomQueryEngine")
				_ = actor ! QueryResponse(msg_type, value, source, site, url, "Success", url)
				} yield value
			}

		case queryResponse: QueryResponse =>
			context.actorSelection("akka://SynupQueryEngine/user/DomQueryEngine") ! queryResponse
	}
}