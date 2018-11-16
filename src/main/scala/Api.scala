import akka.actor.ActorSystem
import akka.http.scaladsl.{ClientTransport, Http}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.NotUsed
import akka.http.scaladsl.coding.Gzip

import scala.util.{Failure, Success}
import scala.concurrent.{ExecutionContextExecutor, Future}
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}

import scala.util.Try

class Api(implicit actorSystem: ActorSystem,
          actorMaterializer: ActorMaterializer) extends JsonSupport {
  implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatcher

  def singleRequest(tag: String): Future[(String, Statistics)] = {
    val uri =
      s"https://api.stackexchange.com/2.2/search?pagesize=100&order=desc&sort=creation&tagged=$tag&site=stackoverflow"

    val response: Future[HttpResponse] = Try(ClientTransport.httpsProxy()) match {
      case Success(httpsProxyTransport) =>
        val settings = ConnectionPoolSettings(actorSystem)
          .withConnectionSettings(
            ClientConnectionSettings(actorSystem)
              .withTransport(httpsProxyTransport))
        Http().singleRequest(HttpRequest(uri = uri), settings = settings)

      case Failure(exception) =>
        println(exception.getLocalizedMessage)
        Http().singleRequest(HttpRequest(uri = uri))
    }

    val apiResponse: Future[ApiResponse] = response.flatMap(resp =>
      for {
        byteString <- resp.entity.dataBytes.runFold(ByteString.empty)(_ ++ _)
        decompressedBytes <- Gzip.decode(byteString)
        result <- Unmarshal(decompressedBytes).to[ApiResponse]
      } yield result)

    val tagPairs = apiResponse.map(_.items.map(item => tag -> item.is_answered))

    tagPairs.map(tagPair =>
      if (tagPair.isEmpty) tag -> Statistics(0, 0)
      else {
        val total = tagPair.size
        val answered = tagPair.count(_._2)
        tag -> Statistics(total, answered)
      }
    )
  }

  def multiRequest(tags: Seq[String],
                   parallelism: Int = 4): Future[Map[String, Statistics]] = {
    val source: Source[String, NotUsed] = Source(tags.toList)
    source
      .mapAsync(parallelism)(tag => singleRequest(tag))
      .runFold(Map[String, Statistics]())((acc, next) => acc + next)
  }
}
