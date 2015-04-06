package cqrs

import java.util.UUID
import org.scalatest.concurrent.Eventually
import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, GivenWhenThen }

import akka.actor.{ Status, ActorSystem }
import akka.actor.ActorDSL._
import akka.testkit.{ ImplicitSender, TestKit }

import domain.orders.{ Order, OrderCommandHandler, Orders }
import read.OrdersView

class SystemTest extends TestKit(ActorSystem("systemtest")) with ImplicitSender with FlatSpecLike
    with GivenWhenThen with BeforeAndAfterAll with Eventually {

  override def afterAll(): Unit = system.shutdown()

  lazy val orders = system.actorOf(Orders.props(orderRegion), "Orders")

  // Stub out the orderRegion with a local implementation
  val orderRegion = actor {
    new Act {
      def getOrCreate(orderId: String) = context.child(orderId).getOrElse(context.actorOf(Order.props(100), orderId))
      become {
        case OrderCommandHandler.Command(orderId, cmd) ⇒
          getOrCreate(orderId) forward cmd
      }
    }
  }

  val orderCommandHandler = system.actorOf(OrderCommandHandler.props(orders, orderRegion), "Handler")
  val ordersView = system.actorOf(OrdersView.props, "OrdersView")

  "CQRS" should "Divide the domain and the read model" in {
    val orderId = UUID.randomUUID().toString
    orderCommandHandler ! Orders.CreateOrderForUser(orderId, "trivento")
    expectMsg(Status.Success())
    orderCommandHandler ! OrderCommandHandler.Command(orderId, Order.AddItem(1, "T-Shirt", 12.50))
    expectMsg(Status.Success())
    orderCommandHandler ! OrderCommandHandler.Command(orderId, Order.AddItem(2, "Sweater", 27.95))
    expectMsg(Status.Success())
    orderCommandHandler ! OrderCommandHandler.Command(orderId, Order.SubmitOrder)
    expectMsg(Status.Success())

    val orderId2 = UUID.randomUUID().toString
    orderCommandHandler ! Orders.CreateOrderForUser(orderId2, "trivento")
    expectMsg(Status.Success())
    orderCommandHandler ! OrderCommandHandler.Command(orderId2, Order.AddItem(1, "Shoe", 72.50))
    expectMsg(Status.Success())
    orderCommandHandler ! OrderCommandHandler.Command(orderId2, Order.AddItem(1, "T-Shirt", 12.50))
    expectMsg(Status.Success())
    orderCommandHandler ! OrderCommandHandler.Command(orderId2, Order.SubmitOrder)
    expectMsg(Status.Success())

    val orderId3 = UUID.randomUUID().toString
    orderCommandHandler ! Orders.CreateOrderForUser(orderId3, "typesafe")
    expectMsg(Status.Success())
    orderCommandHandler ! OrderCommandHandler.Command(orderId3, Order.AddItem(1, "Sweater", 27.95))
    expectMsg(Status.Success())
    orderCommandHandler ! OrderCommandHandler.Command(orderId3, Order.SubmitOrder)
    expectMsg(Status.Success())

    eventually {
      ordersView ! OrdersView.GetTop2Items
      expectMsg(List(("Sweater", 3), ("T-Shirt", 2)))
    }

  }

}
