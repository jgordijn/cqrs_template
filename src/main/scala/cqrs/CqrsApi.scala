package cqrs

import akka.actor.{ Props, ActorRef }
import akka.util.Timeout
import cqrs.domain.orders.OrderCommandRoute
import cqrs.read.OrderReadRoute
import spray.routing.HttpServiceActor
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext
object CqrsApi {

  def props(ordersViewActor: ActorRef, orderCommandHandler: ActorRef): Props =
    Props(new CqrsApi(ordersViewActor, orderCommandHandler))
}

class CqrsApi(ordersViewActor: ActorRef, orderCommandHandlerActor: ActorRef) extends HttpServiceActor with CqrsRoutes {
  override def ordersView: ActorRef = ordersViewActor
  override def orderCommandHandler: ActorRef = orderCommandHandlerActor

  override implicit def executionContext: ExecutionContext = context.dispatcher

  override implicit def timeout: Timeout = 5.seconds //This usually comes from settings

  override def receive: Receive = runRoute(routes)
}

trait CqrsRoutes extends OrderReadRoute with OrderCommandRoute {

  def routes = orderReadRoute ~ orderCommandRoute

}
