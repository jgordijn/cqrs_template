package cqrs

import akka.actor.{ PoisonPill, ActorSystem }
import cqrs.domain.orders.{ Order, OrderCommandHandler, Orders }
import akka.contrib.pattern.{ ClusterSharding, ClusterSingletonManager, ClusterSingletonProxy }

trait DomainActors {
  implicit def system: ActorSystem

  // register the Order entry type
  val orderRegion = ClusterSharding(system).start(
    typeName = Order.shardName,
    entryProps = Some(Order.props(100)),
    idExtractor = OrderCommandHandler.idExtractor,
    shardResolver = OrderCommandHandler.shardResolver)

  system.actorOf(
    ClusterSingletonManager.props(Orders.props(orderRegion), "orders", PoisonPill, None),
    "singleton"
  )

  lazy val orders = system.actorOf(
    ClusterSingletonProxy.props(s"/user/singleton/orders", None),
    "ordersProxy"
  )

  lazy val orderCommandHandler = system.actorOf(OrderCommandHandler.props(orders, orderRegion), "OrderCommandHandler")
}
