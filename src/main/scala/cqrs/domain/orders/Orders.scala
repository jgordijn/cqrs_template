package cqrs.domain.orders

import akka.actor.{ Status, ActorLogging, ActorRef, Props }
import akka.persistence.PersistentActor
import cqrs.settings.SettingsActor

object Orders {
  case class Get(orderId: String)
  case class CreateOrderForUser(id: String, username: String)
  case class InitializedOrderAck(id: String, username: String)

  sealed trait Event
  case class OrderCreated(orderId: String, username: String) extends Event

  sealed class FunctionalException(message: String) extends Exception(message)

  case class DuplicateOrderKeyException(orderId: String) extends FunctionalException(s"There already is an order with id: $orderId")

  val persistenceId: String = "orders"

  def props(orderRegion: ActorRef): Props =
    Props(new Orders(orderRegion))

  def actorName(orderId: String): String = s"order-$orderId"
}

class Orders(orderRegion: ActorRef) extends PersistentActor with SettingsActor with ActorLogging {
  import cqrs.domain.orders.Orders._

  override val persistenceId: String = Orders.persistenceId

  var existingOrders = Set.empty[String]

  def updateState(event: Event): Unit = event match {
    case OrderCreated(orderId, username) ⇒
      existingOrders += orderId;
  }

  override def receiveCommand: Receive = {
    case CreateOrderForUser(orderId, username) if !existingOrders.contains(orderId) ⇒
      log.debug("Creating Order")
      orderRegion ! OrderCommandHandler.Command(orderId, Order.InitializeOrder(username))
      sender() ! Status.Success()
    case CreateOrderForUser(orderId, username) ⇒
      log.debug("Order already created")
      sender() ! Status.Failure(DuplicateOrderKeyException(orderId))
    case InitializedOrderAck(orderId, username) ⇒
      log.debug("Registering initialized order")
      persist(OrderCreated(orderId, username))(updateState)
  }

  override def receiveRecover: Receive = {
    case event: Event ⇒
      log.debug("Receiving recover message: {}", event)
      updateState(event)
  }

}
