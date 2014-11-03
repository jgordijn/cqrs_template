package cqrs.domain.orders

import scala.concurrent.duration._

import akka.actor._
import akka.contrib.pattern.ShardRegion
import akka.util.Timeout
import cqrs.settings.SettingsActor

object OrderCommandHandler {

  case class Command(orderId: String, cmd: Order.Command)

  case class UnknownOrderException(msg: String) extends Exception

  val idExtractor: ShardRegion.IdExtractor = {
    case Command(orderId, orderCommand) ⇒ (orderId, orderCommand)
  }

  def shardResolver: ShardRegion.ShardResolver = {
    case Command(orderId, _) ⇒ math.abs(orderId.hashCode) % 30 toString
  }

  def props(orders: ActorRef, orderRegion: ActorRef): Props =
    Props(new OrderCommandHandler(orders, orderRegion))

}

class OrderCommandHandler(orders: ActorRef, orderRegion: ActorRef) extends Actor with ActorLogging with SettingsActor {

  import cqrs.domain.orders.OrderCommandHandler._

  implicit val timeout: Timeout = 5 seconds

  override def receive: Receive = {
    case create: Orders.CreateOrderForUser ⇒
      log.debug("Creating a new Order: {}", create.id)
      orders forward create
    case cmd: Command ⇒
      log.debug("Executing a new Command")
      orderRegion forward cmd
  }
}
