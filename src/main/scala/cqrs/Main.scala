package cqrs

import java.util.UUID

import akka.actor._
import akka.io.IO
import akka.persistence.journal.leveldb.{ SharedLeveldbStore, SharedLeveldbJournal }
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import cqrs.domain.orders.{ Order, OrderCommandHandler, Orders }
import cqrs.read.OrdersView
import spray.can.Http
import akka.pattern.ask
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object Main extends App with DomainActors with ReadActors with TestData {
  val clusterPort = args.lift(0).getOrElse("2551")
  val httpPort = args.lift(1).getOrElse("8080")

  println("PORT: " + clusterPort)
  val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + clusterPort).
    withFallback(ConfigFactory.load())

  implicit lazy val system = ActorSystem("cqrs-activator", config)
  implicit val executionContext = system.dispatcher
  implicit val timeout: Timeout = 2 seconds

  startupSharedJournal(system, startStore = (clusterPort == "2551"), path =
    ActorPath.fromString("akka.tcp://cqrs-activator@127.0.0.1:2551/user/store"))

  def startupSharedJournal(system: ActorSystem, startStore: Boolean, path: ActorPath): Unit = {
    // Start the shared journal one one node (don't crash this SPOF)
    // This will not be needed with a distributed journal
    if (startStore)
      system.actorOf(Props[SharedLeveldbStore], "store")

    // register the shared journal
    implicit val timeout = Timeout(15.seconds)
    val f = system.actorSelection(path) ? Identify(None)
    f.onSuccess {
      case ActorIdentity(_, Some(ref)) ⇒ SharedLeveldbJournal.setStore(ref, system)
      case x ⇒
        system.log.error("Shared journal not started at {}", path)
        system.shutdown()
    }
    f.onFailure {
      case _ ⇒
        system.log.error("Lookup of shared journal at {} timed out", path)
        system.shutdown()
    }
  }

  val api = system.actorOf(CqrsApi.props(ordersView, orderCommandHandler), "cqrs-api-actor")

  IO(Http)(system) ? Http.Bind(listener = api, interface = "0.0.0.0", port = httpPort.toInt)

  loadData()

}

trait TestData extends DomainActors {
  implicit def system: ActorSystem
  implicit def executionContext: ExecutionContext
  implicit def timeout: Timeout

  def loadData() = {
    val orderId = UUID.randomUUID().toString

    (orderCommandHandler ? Orders.CreateOrderForUser(orderId, "trivento")).onSuccess { case m ⇒ println(m) }
    (orderCommandHandler ? OrderCommandHandler.Command(orderId, Order.AddItem(1, "T-Shirt", 12.50))).onSuccess { case m ⇒ println(m) }
    (orderCommandHandler ? OrderCommandHandler.Command(orderId, Order.AddItem(2, "Sweater", 27.95))).onSuccess { case m ⇒ println(m) }
    (orderCommandHandler ? OrderCommandHandler.Command(orderId, Order.SubmitOrder)).onSuccess { case m ⇒ println(m) }

    val orderId2 = UUID.randomUUID().toString
    (orderCommandHandler ? Orders.CreateOrderForUser(orderId2, "trivento")).onSuccess { case m ⇒ println(m) }
    (orderCommandHandler ? OrderCommandHandler.Command(orderId2, Order.AddItem(1, "Shoe", 72.50))).onSuccess { case m ⇒ println(m) }
    (orderCommandHandler ? OrderCommandHandler.Command(orderId2, Order.AddItem(1, "T-Shirt", 12.50))).onSuccess { case m ⇒ println(m) }
    (orderCommandHandler ? OrderCommandHandler.Command(orderId2, Order.SubmitOrder)).onSuccess { case m ⇒ println(m) }

    val orderId3 = UUID.randomUUID().toString
    (orderCommandHandler ? Orders.CreateOrderForUser(orderId3, "typesafe")).onSuccess { case m ⇒ println(m) }
    (orderCommandHandler ? OrderCommandHandler.Command(orderId3, Order.AddItem(1, "Sweater", 27.95))).onSuccess { case m ⇒ println(m) }
    (orderCommandHandler ? OrderCommandHandler.Command(orderId3, Order.SubmitOrder)).onSuccess { case m ⇒ println(m) }
  }

}