package com.synup.http

import scala.collection.mutable.Map
import scala.collection.JavaConversions._

import akka.actor.{Actor, ActorLogging, ActorSystem}
import com.synup.parsing._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

import scala.concurrent.duration._
import akka.pattern.ask
import akka.event.Logging
import akka.io.IO
import spray.can.Http
import spray.can.Http._
import spray.client.pipelining._
import spray.util._
import scala.language.postfixOps
import scala.concurrent.Future
import akka.util.Timeout
import spray.http._
import HttpMethods._
import com.typesafe.config.ConfigFactory
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.util.Timeout
import akka.pattern.ask
import akka.io.IO
import spray.can.Http
import spray.http._
import HttpMethods._
import spray.json._
import DefaultJsonProtocol._
import spray.json.AdditionalFormats
import spray.httpx.SprayJsonSupport
import spray.json.{JsonFormat, DefaultJsonProtocol}
import SprayJsonSupport._

class DomGetter extends Actor with ActorLogging {
	// val conf = ConfigFactory.load()
	
	// val proxies: List[String] = conf.getStringList("synup.proxies").toList
	// val numProxies = proxies.size

	// val username = conf.getString("synup.proxy_username")
	// val password = conf.getString("synup.proxy_password")
	
	// val siteMap: Map[String, Int] = Map[String, Int]()
	
	def getDom(url: String, proxy: String, proxyPort: Int = 29842): Future[HttpResponse] = {
    implicit val timeout: Timeout = Timeout(100.seconds)
		// val urlFormatted = Uri(url)

		// val setup = Http.HostConnectorSetup(
  //     urlFormatted.authority.host.address,
  //     port = urlFormatted.authority.port,
  //     sslEncryption = false,
  //     connectionType = ClientConnectionType.Proxied(proxy, proxyPort)
  //   )

  //   val pipeline: Future[SendReceive] = for (
  //     Http.HostConnectorInfo(connector, _) <- IO(Http)(context.system).ask (setup)
  //   ) yield (
  //     	addHeader("synup", "synup" ) 
  //       ~> addCredentials(BasicHttpCredentials(username,password))
  //       ~> sendReceive(connector)
  //   )
  //   pipeline.flatMap(_(Get(Uri(url))))
  	val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
  	val response: Future[HttpResponse] = pipeline {
      Get(Uri(url))
    }
    return response

  }
	
	def getResponseAuthority(url: String, proxy: String, proxyPort: Int = 29842): Future[HttpResponse] = {
  	implicit val timeout: Timeout = Timeout(15.seconds)

    val pipeline : HttpRequest => Future[HttpResponse] = sendReceive       
    val keyword = url
    val response: Future[HttpResponse] =  pipeline(Post("http://api.authoritylabs.com/keywords/priority", s"""{
        "auth_token": "ZjfA6diAnBSZbzJZTaMg",
        "engine": "google",
        "keyword" : "${keyword}",
        "locale" : "en-us",
        "callback" : "http://162.243.247.227:8080/authority_callback"
    }""".asJson.asJsObject))
    return response
  }

	def receive = {
		case DomRequest(msg_type, request_id, source, site, url)	=>
			val sendingActor = sender()
			
			// val proxyNum = siteMap.getOrElseUpdate(site, 0)
			// val proxy: String = proxies(proxyNum)
			// siteMap(site) += (proxyNum + 1) % numProxies

			// authority logic
			if (site == "authority") {
				val res = getResponseAuthority(url, "")
				res onComplete {
					case Success(dom) => sendingActor ! DomResponse(DomRequest("query_response", request_id, source, site, url), dom.entity.asString)
    			// retry logic
    			case Failure(e) => e.printStackTrace
    											 sendingActor ! QueryResponse("query_response", request_id, source, site, url, "Failed", "")
				}
			} else {
				val response = getDom(url, "") //proxy)
				response onComplete {
    			case Success(dom) => sendingActor ! DomResponse(DomRequest("query_response", request_id, source, site, url), dom.entity.asString)
    			// retry logic
    			case Failure(e) => e.printStackTrace
    											 sendingActor ! QueryResponse("query_response", request_id, source, site, url, "Failed", "")
  			}
			}
	}
}