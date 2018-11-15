import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}

import scala.io.StdIn
import akka.http.scaladsl.server.Directives

import scala.concurrent.ExecutionContextExecutor

object WebServer extends Directives with JsonSupport {

  def main(args: Array[String]): Unit = {
    val decider: Supervision.Decider = _ ⇒ Supervision.Resume

    implicit val system: ActorSystem = ActorSystem("stackover-search")
    implicit val materializer: ActorMaterializer =
      ActorMaterializer(
        ActorMaterializerSettings(system).withSupervisionStrategy(decider))
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher

    val api = new Api()

    val route =
      path("search") {
        get {
          parameters('tag.*) { tags =>
            onSuccess(api.multiRequest(tags.toSeq)) { stats =>
              if (stats.isEmpty) complete("Nothing found")
              else complete(stats)
            }
          }
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
