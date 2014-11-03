package cqrs
package domain.orders

import java.util.UUID
import scala.concurrent.duration._
import org.scalatest.{ BeforeAndAfterAll, Matchers, FlatSpecLike }

import akka.actor._
import akka.actor.ActorDSL._
import akka.persistence.PersistentView
import akka.testkit.{ TestProbe, ImplicitSender, TestKit }

import Order.MaxOrderPriceReached
import read.OrdersView
import settings.Settings

class DomainTest extends TestKit(ActorSystem("domain")) with FlatSpecLike with ImplicitSender with Matchers with BeforeAndAfterAll {

  override def afterAll(): Unit = system.shutdown()

  lazy val orders = system.actorOf(Orders.props, "Orders")
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

  // We create a test view here so we can forward all persistent messages to a probe
  class TestOrderView(orderId: String, probe: ActorRef) extends PersistentView with ActorLogging {

    override def persistenceId = Order.persistenceId(orderId)
    override def viewId: String = "test"

    override def receive: Receive = {
      case msg ⇒
        log.debug(s"Test view received: $msg")
        probe ! msg
    }
  }

  def createOrderForUser()(implicit orderId: String) = {
    orderCommandHandler ! Orders.CreateOrderForUser(orderId, "trivento")
    expectMsg(Status.Success())
  }
  def addItemSuccesfully(quantity: Int, productName: String, pricePerItem: Double)(implicit orderId: String, orderViewProbe: TestProbe) = {
    orderCommandHandler ! OrderCommandHandler.Command(orderId, Order.AddItem(quantity, productName, pricePerItem))
    expectMsg(Status.Success())
    orderViewProbe.expectMsg(Order.ItemAdded(quantity, productName, pricePerItem))
  }

  def addItemFailed(quantity: Int, productName: String, pricePerItem: Double, currentOrderPrice: Double, maxPrice: Double)(implicit orderId: String, orderViewProbe: TestProbe) = {
    orderCommandHandler ! OrderCommandHandler.Command(orderId, Order.AddItem(quantity, productName, pricePerItem))
    expectMsg(Status.Failure(MaxOrderPriceReached(currentOrderPrice, maxPrice)))
    orderViewProbe.expectNoMsg(500 millis)
  }

  def submitSuccesfully()(implicit orderId: String, orderViewProbe: TestProbe) = {
    orderCommandHandler ! OrderCommandHandler.Command(orderId, Order.SubmitOrder)
    expectMsg(Status.Success())
    orderViewProbe.expectMsg(Order.OrderSubmitted)
  }

  "The Domain" should "handle the happy flow, adding items and submitting the order with price < 100" in {
    implicit val orderId = UUID.randomUUID().toString
    implicit val orderViewProbe = TestProbe()
    system.actorOf(Props(new TestOrderView(orderId, orderViewProbe.ref)))

    createOrderForUser()
    addItemSuccesfully(1, "T-Shirt", 12.50)
    addItemSuccesfully(2, "Sweater", 27.95)
    submitSuccesfully()
  }

  it should "reply with a failure when adding an item exceeds the max price" in {
    implicit val orderId = UUID.randomUUID().toString
    implicit val orderViewProbe = TestProbe()
    system.actorOf(Props(new TestOrderView(orderId, orderViewProbe.ref)))
    Settings(system).maxOrderPrice shouldBe 100.00

    createOrderForUser()
    addItemSuccesfully(1, "T-Shirt", 99.00)
    addItemFailed(1, "Sweater", 10.00, 99.00, 100.00)
    submitSuccesfully()

  }

  it should "accept an order with a product with the exact price of 100 dollar" in {
    implicit val orderId = UUID.randomUUID().toString
    implicit val orderViewProbe = TestProbe()
    system.actorOf(Props(new TestOrderView(orderId, orderViewProbe.ref)))
    Settings(system).maxOrderPrice shouldBe 100.00

    createOrderForUser()
    addItemSuccesfully(1, "T-Shirt", 100.00)
    submitSuccesfully()
  }

  it should "accept an order with multiple products with an exact total price of 100 dollar" in {
    implicit val orderId = UUID.randomUUID().toString
    implicit val orderViewProbe = TestProbe()
    system.actorOf(Props(new TestOrderView(orderId, orderViewProbe.ref)))

    createOrderForUser()
    addItemSuccesfully(2, "T-Shirt", 12.50)
    addItemSuccesfully(1, "Sweater", 75.00)
    submitSuccesfully()
  }

}
