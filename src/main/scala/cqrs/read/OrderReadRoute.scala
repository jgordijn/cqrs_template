package cqrs.read

import akka.actor.ActorRef
import akka.util.Timeout
import spray.routing.{ Route, HttpService }
import akka.pattern._
import spray.json._
import spray.httpx.SprayJsonSupport

import scala.concurrent.ExecutionContext

/**
 * Routes for the read side of the app
 */
trait OrderReadRoute extends HttpService with OrderMarshalling with SprayJsonSupport with DefaultJsonProtocol {
  def ordersView: ActorRef
  implicit def timeout: Timeout
  implicit def executionContext: ExecutionContext

  val orderReadRoute: Route = {
    path("users" / Segment / "orders") { username ⇒
      get {
        complete((ordersView ? OrdersView.GetOrdersForUser(username)).mapTo[List[OrdersView.Order]])
      }
    }
  }
}
