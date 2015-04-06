package cqrs.domain.orders

import akka.actor.{ Status, ActorLogging, Props }
import akka.persistence.PersistentActor
import cqrs.domain.orders.OrderCommandHandler.UnknownOrderException
import cqrs.settings.SettingsActor

object Order {
  sealed trait Command
  case class AddItem(quantity: Int, productName: String, pricePerItem: Double) extends Command
  case object SubmitOrder extends Command
  case object Initialize extends Command

  abstract class FunctionalException(msg: String) extends Exception(msg)
  case class OrderIsSubmittedException(orderId: String) extends FunctionalException(s"Cannot handle any commands. The Order is submitted: $orderId")
  case class MaxOrderPriceReached(currentOrderPrice: Double, maxPrice: Double) extends FunctionalException(s"Cannot add item to order. It would exceed the max order price of $maxPrice. Current order price: $currentOrderPrice")

  sealed trait Event
  case class ItemAdded(quantity: Int, productName: String, pricePerItem: Double) extends Event
  case object OrderSubmitted extends Event

  def persistenceId(orderId: String): String = s"order_$orderId"

  val shardName: String = "order"
  val role: String = "orderRole"

  def props(maxOrderPrice: Double): Props =
    Props(new Order(maxOrderPrice))
}

class Order(maxOrderPrice: Double) extends PersistentActor with SettingsActor with ActorLogging {
  import cqrs.domain.orders.Order._
  val orderId = self.path.name

  override def persistenceId: String = Order.persistenceId(orderId)

  var orderPrice: Double = 0

  def updateState(event: Event): Unit = event match {
    case ItemAdded(quantity, productName, pricePerItem) ⇒
      log.debug(s"UPDATE: $persistenceId")
      orderPrice += quantity * pricePerItem
    case OrderSubmitted ⇒
      context become submitted
  }

  def uninitialized : Receive = {
    case Initialize ⇒ context become initialized
    case _ ⇒ sender() ! Status.Failure(UnknownOrderException("unknown order"))
  }

  def initialized : Receive = {
    case AddItem(quantity, productName, pricePerItem) if orderPrice + quantity * pricePerItem <= maxOrderPrice ⇒
      persist(ItemAdded(quantity, productName, pricePerItem)) { evt ⇒
        log.debug(s"Item Added {} to {}", persistenceId, evt)
        sender() ! Status.Success(())
        updateState(evt)
      }
    case AddItem(quantity, productName, pricePerItem) ⇒
      log.error("Attempt to add more items to the order than allowed")
      sender() ! Status.Failure(MaxOrderPriceReached(orderPrice, maxOrderPrice))
    case SubmitOrder if orderPrice > 0 ⇒
      log.info(s"Order submitted {}", orderId)
      persist(OrderSubmitted) { event =>
        sender() ! Status.Success(())
        updateState (event)
      }
  }

  def submitted: Receive = {
    case msg ⇒
      log.error("Order is completed. Will not process: {}", msg)
      sender() ! Status.Failure(OrderIsSubmittedException(orderId))
  }

  override def receiveRecover: Receive = {
    case event: Event ⇒
      log.debug("Receiving recover message: {}", event)
      updateState(event)
  }

  override def receiveCommand: Receive = initialized
}
