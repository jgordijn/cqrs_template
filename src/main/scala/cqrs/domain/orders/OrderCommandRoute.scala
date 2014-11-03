package cqrs.domain.orders

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import cqrs.domain.orders.OrderCommandHandler.UnknownOrderException
import cqrs.domain.orders.Orders.DuplicateOrderKeyException
import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport
import spray.json._
import spray.routing.{ HttpService, Route }

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

case class NewOrder(username: String)

trait UnMarshaller extends DefaultJsonProtocol {
  implicit val newOrderFormat = jsonFormat1(NewOrder)
  implicit val addItemMarshall = jsonFormat3(Order.AddItem)
}

/**
 * Routes for the read side of the app
 */
trait OrderCommandRoute extends HttpService with SprayJsonSupport with UnMarshaller {
  def orderCommandHandler: ActorRef

  implicit def timeout: Timeout

  implicit def executionContext: ExecutionContext

  private def createOrder(orderId: String, newOrder: NewOrder) = {
    onComplete(orderCommandHandler ? Orders.CreateOrderForUser(orderId, newOrder.username)) {
      case Success(x) ⇒
        complete(StatusCodes.NoContent)
      case Failure(ex @ DuplicateOrderKeyException(id)) ⇒
        complete(StatusCodes.BadRequest, ex.msg)
      case Failure(ex) ⇒
        complete(StatusCodes.InternalServerError, s"An error occurred: ${ex.getMessage}")
    }
  }

  private def sendOrderCommand(orderId: String, command: Order.Command) = {
    onComplete(orderCommandHandler ? OrderCommandHandler.Command(orderId, command)) {
      case Success(_) ⇒
        complete(StatusCodes.NoContent)
      case Failure(UnknownOrderException(msg)) ⇒
        complete(StatusCodes.NotFound, msg)
      case Failure(ex: Order.FunctionalException) ⇒
        complete(StatusCodes.BadRequest, ex.getMessage)
      case Failure(ex) ⇒
        complete(StatusCodes.InternalServerError, s"An error occurred: ${ex.getMessage}")
    }
  }

  val orderCommandRoute: Route = post {
    pathPrefix("orders" / Segment) { orderId ⇒
      pathEnd {
        entity(as[NewOrder]) { newOrder ⇒
          createOrder(orderId, newOrder)
        }
      } ~
        path("submit") {
          sendOrderCommand(orderId, Order.SubmitOrder)
        } ~
        path("items") {
          entity(as[Order.AddItem]) { addItem ⇒
            sendOrderCommand(orderId, addItem)
          }
        }
    }
  }

}
