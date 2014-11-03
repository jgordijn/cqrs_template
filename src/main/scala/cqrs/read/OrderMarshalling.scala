package cqrs.read

import spray.json._
import DefaultJsonProtocol._

private[read] trait OrderMarshalling {
  implicit val orderItemFormat = jsonFormat3(OrdersView.OrderItem)
  implicit val orderFormat = jsonFormat3(OrdersView.Order)
}
