package cqrs.read

import akka.actor.Props
import akka.persistence.PersistentView
import cqrs.domain.orders.Order
import cqrs.read.OrderView.Envelope

object OrderView {
  case class Envelope(orderId: String, event: Order.Event)

  def props(orderId: String): Props = Props(new OrderView(orderId))
}

class OrderView(orderId: String) extends PersistentView {
  override def persistenceId: String = Order.persistenceId(orderId)
  override def viewId: String = s"$persistenceId-view"

  override def receive: Receive = {
    case msg: Order.Event ⇒ context.parent forward Envelope(orderId, msg)
  }
}
