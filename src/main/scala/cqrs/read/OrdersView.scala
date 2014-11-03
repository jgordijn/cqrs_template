package cqrs.read

import akka.actor.{ ActorLogging, Props }
import akka.persistence.{ PersistentView, Persistent }
import cqrs.domain.orders.{ Order, Orders }
import cqrs.read.OrdersView.OrderItem

object OrdersView {
  case class OrderItem(quantity: Int, productName: String, pricePerItem: Double)
  case class Order(orderId: String, items: List[OrderItem], submitted: Boolean = false)

  case object GetTop2Items
  case object GetPendingOrders
  case class GetOrdersForUser(username: String)

  def props: Props =
    Props(new OrdersView)
}

class OrdersView extends PersistentView with ActorLogging {
  override def persistenceId: String = Orders.persistenceId
  override def viewId: String = s"$persistenceId-view"

  type OrderId = String
  type UserName = String
  type ProductName = String

  var orderIdToOrder: Map[OrderId, OrdersView.Order] = Map.empty
  var ordersPerUser: Map[UserName, List[OrderId]] = Map.empty.withDefaultValue(List.empty)
  var pendingOrders: Set[OrderId] = Set.empty
  var productSold: Map[ProductName, Int] = Map.empty.withDefaultValue(0)

  def buildView: Receive = {
    case Orders.OrderCreated(orderId, username) ⇒
      createOrderViewActor(orderId)
      val newOrder = OrdersView.Order(orderId, List.empty[OrderItem])
      orderIdToOrder = orderIdToOrder.updated(orderId, newOrder)
      ordersPerUser = ordersPerUser.updated(username, orderId :: ordersPerUser(username))
      pendingOrders += orderId
    case OrderView.Envelope(orderId, Order.ItemAdded(quantity, productName, pricePerItem)) ⇒
      val order = orderIdToOrder(orderId)
      val orderWithNewItem = order.copy(items = OrderItem(quantity, productName, pricePerItem) :: order.items)
      orderIdToOrder = orderIdToOrder.updated(orderId, orderWithNewItem)
      productSold += productName -> (productSold(productName) + quantity)
      log.info("=================> name: {}, quantity: {}   ({})", productName, quantity, productSold)
    case OrderView.Envelope(orderId, Order.OrderSubmitted) ⇒
      val order = orderIdToOrder(orderId)
      val orderWithNewItem = order.copy(submitted = true)
      orderIdToOrder = orderIdToOrder.updated(orderId, orderWithNewItem)
      pendingOrders -= orderId
  }

  override def receive: Receive = buildView orElse {
    case OrdersView.GetPendingOrders ⇒
      val pending: Set[OrdersView.Order] = pendingOrders.map(orderIdToOrder(_))
      sender() ! pending
    case OrdersView.GetOrdersForUser(username) ⇒
      val ordersForUser: List[OrdersView.Order] = ordersPerUser(username).map(orderIdToOrder)
      sender() ! ordersForUser
    case OrdersView.GetTop2Items ⇒
      sender() ! productSold.toList.sortBy(_._2).reverse.take(2)

  }

  def createOrderViewActor(orderId: String): Unit = context.actorOf(OrderView.props(orderId), s"orderView-$orderId")
}
