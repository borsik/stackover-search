import akka.actor.ActorSystem
import akka.http.scaladsl.{ClientTransport, Http}
import akka.http.scaladsl.model.HttpRequest
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.NotUsed
import akka.http.scaladsl.coding.Gzip
import scala.util.{Success, Failure}

import scala.concurrent.Future
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}

import scala.util.Try

class Api(implicit actorSystem: ActorSystem, actorMaterializer: ActorMaterializer) extends JsonSupport {
  implicit val executionContext = actorSystem.dispatcher

  def singleRequest(tag: String): Future[Map[String, Statistics]] = {
    val uri = s"https://api.stackexchange.com/2.2/search?pagesize=100&order=desc&sort=creation&tagged=$tag&site=stackoverflow"

    val responseF = Try(ClientTransport.httpsProxy()) match {
      case Success(httpsProxyTransport) =>
        val settings = ConnectionPoolSettings(actorSystem)
          .withConnectionSettings(ClientConnectionSettings(actorSystem)
            .withTransport(httpsProxyTransport))
        Http().singleRequest(HttpRequest(uri = uri), settings = settings)

      case Failure(exception) =>
        println(exception.getLocalizedMessage)
        Http().singleRequest(HttpRequest(uri = uri))
    }

    val apiResponseF = responseF.flatMap(response =>
      for {
        byteString <- response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _)
        decompressedBytes <- Gzip.decode(byteString)
        result <- Unmarshal(decompressedBytes).to[ApiResponse]
      } yield result
    )

    val tagPairsF = apiResponseF.map(_.items.flatMap(item => item.tags.map(_ -> item.is_answered)))

    tagPairsF.map(_.groupBy(_._1).map { case (apiTag, tagPairs) =>
      val total = tagPairs.size
      val answered = tagPairs.count(_._2)
      apiTag -> Statistics(total, answered)
    })
  }

  def multiRequest(tags: Seq[String], parallelism: Int = 4): Future[Map[String, Statistics]] = {
    val source: Source[String, NotUsed] = Source(tags.toList)
    source.mapAsync(parallelism)(tag => singleRequest(tag)).runFold(Map[String, Statistics]())((a, b) =>
      (a.toSeq ++ b.toSeq).groupBy(_._1).map {
        case (tag, list) => tag -> list.map(_._2).reduce((a, b) =>
          Statistics(a.total + b.total, a.answered + b.answered))
      }
    )
  }
}
