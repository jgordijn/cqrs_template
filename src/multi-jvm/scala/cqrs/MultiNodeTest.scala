package cqrs

import java.io.File

import akka.actor._
import akka.cluster.Cluster
import akka.contrib.pattern.{ ClusterSharding, ClusterSingletonProxy, ClusterSingletonManager }
import akka.persistence.Persistence
import akka.persistence.journal.leveldb.{ SharedLeveldbJournal, SharedLeveldbStore }
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{ MultiNodeSpecCallbacks, MultiNodeConfig }
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import org.scalatest.concurrent.Eventually._
import scala.concurrent.duration._

import domain.orders.{ Order, OrderCommandHandler, Orders }
import read.OrdersView


object MultiNodeSampleConfig extends MultiNodeConfig {
  val node1 = role("node1")
  val node2 = role("node2")

  commonConfig(ConfigFactory.parseString("""
    akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
    akka.persistence.journal.plugin = "akka.persistence.journal.leveldb-shared"
    akka.persistence.journal.leveldb-shared.store {
      native = off
      dir = "target/test-shared-journal"
    }
    akka.persistence.snapshot-store.local.dir = "target/test-snapshots"
    cqrs.maxOrderPrice = 100.00
"""))

}

class MultiNodeSampleSpecMultiJvmNode1 extends MultiNodeSample
class MultiNodeSampleSpecMultiJvmNode2 extends MultiNodeSample

/**
 * Hooks up MultiNodeSpec with ScalaTest
 */
trait STMultiNodeSpec extends MultiNodeSpecCallbacks
    with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()

}

class MultiNodeSample extends MultiNodeSpec(MultiNodeSampleConfig)
    with STMultiNodeSpec with ImplicitSender {

  val storageLocations = List(
    "akka.persistence.journal.leveldb.dir",
    "akka.persistence.journal.leveldb-shared.store.dir",
    "akka.persistence.snapshot-store.local.dir").map(s ⇒ new File(system.settings.config.getString(s)))

  override def beforeAll() = {
    super.beforeAll()
    storageLocations.foreach(FileUtils.deleteDirectory)
  }

  override def afterAll() = {
    super.afterAll()
    storageLocations.foreach(FileUtils.deleteDirectory)
  }

  def startSharding(): Unit = {

    val orderRegion: ActorRef = ClusterSharding(system).start(
      typeName = Order.shardName,
      entryProps = Some(Order.props(100)),
      idExtractor = OrderCommandHandler.idExtractor,
      shardResolver = OrderCommandHandler.shardResolver)

    system.actorOf(
      ClusterSingletonManager.props(Orders.props(orderRegion), "orders", PoisonPill, None),
      "singleton")

  }
  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      Cluster(system) join node(to).address
      startSharding()
    }
    enterBarrier(from.name + "-joined")
  }

  import MultiNodeSampleConfig._

  def initialParticipants = roles.size

  "A MultiNodeSample" must {

    "setup shared journal" in {
      // start the Persistence extension
      Persistence(system)
      runOn(node1) {
        system.actorOf(Props[SharedLeveldbStore], "store")
      }
      enterBarrier("peristence-started")

      runOn(node1, node2) {
        system.actorSelection(node(node1) / "user" / "store") ! Identify(None)
        val sharedStore = expectMsgType[ActorIdentity].ref.get
        SharedLeveldbJournal.setStore(sharedStore, system)
      }

      enterBarrier("after-1")
    }

    "join cluster" in within(15.seconds) {
      join(node1, node1)
      join(node2, node1)
      enterBarrier("after-2")
    }
    val orderId = "45902e35-4acb-4dc2-9109-73a9126b604f"
    "create, edit, and retrieve blog post" in within(15.seconds) {
      runOn(node1) {
        val orderRegion = ClusterSharding(system).shardRegion(Order.shardName)
        val orders = system.actorOf(
          ClusterSingletonProxy.props(s"/user/singleton/orders", None),
          "ordersProxy")
        val orderCommandHandler = system.actorOf(OrderCommandHandler.props(orders, orderRegion), "OrderCommandHandler")

        orderCommandHandler ! Orders.CreateOrderForUser(orderId, "trivento")
        expectMsg(Status.Success(()))
        orderCommandHandler ! OrderCommandHandler.Command(orderId, Order.AddItem(1, "T-shirt", 12.50))
        expectMsg(Status.Success(()))
      }
      enterBarrier("New product")
      runOn(node2) {
        val view = system.actorOf(OrdersView.props, "OrdersView")

        eventually {
          view ! OrdersView.GetOrdersForUser("trivento")
          expectMsg(List(OrdersView.Order(orderId, List(OrdersView.OrderItem(1, "T-shirt", 12.50)))))
        }
      }
      enterBarrier("Done")
    }
  }
}
