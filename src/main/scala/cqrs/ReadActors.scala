package cqrs

import akka.actor.ActorSystem
import cqrs.read.OrdersView

trait ReadActors {
  implicit val system: ActorSystem
  lazy val ordersView = system.actorOf(OrdersView.props, "OrdersView")

}
