package sample.race

import akka.actor._
import scala.collection._

object Backend {
  sealed trait BackendReq
  case class Get(key: String) extends BackendReq
  case class Put(obj: DataObject) extends BackendReq
  case object GetLastSeq extends BackendReq

  sealed trait BackendResp
  case class GetRes(obj: Option[DataObject]) extends BackendResp
  case class PutAck(seq: DataObject.SeqID) extends BackendResp
  case class Seq(seq: DataObject.SeqID) extends BackendResp
  case class Error(reason: String) extends BackendResp

  def props = Props[Backend]
}

class Backend extends Actor with ActorLogging {
  import Backend._
  // TODO http://doc.akka.io/docs/akka/snapshot/scala/persistence.html
  var seq: DataObject.SeqID = DataObject.SeqID()
  var storage: mutable.HashMap[DataObject.Key, DataObject] = mutable.HashMap()
  def receive = {
    case GetLastSeq => sender ! Seq(seq)
    case Get(key) =>
      sender ! GetRes(storage.get(key))
    case Put(obj) =>
      storage.put(obj.key, obj)
      seq = obj.version max seq
      sender ! PutAck(seq)
  }
}