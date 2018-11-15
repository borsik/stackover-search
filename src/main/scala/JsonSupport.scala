import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, PrettyPrinter}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val printer = PrettyPrinter
  implicit val statisticsFormat = jsonFormat2(Statistics)
  implicit val itemFormat = jsonFormat2(Item)
  implicit val apiResponseFormat = jsonFormat1(ApiResponse)
}
