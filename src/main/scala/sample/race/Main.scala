package sample.race

import akka.actor._
import akka.dispatch._
import akka.pattern.{ask, pipe}
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Success, Failure}

object KV {
  sealed trait KVReq
  case class Get(key: String) extends KVReq
  case class Put(obj: DataObject, naive: Boolean = false) extends KVReq

  sealed trait KVResp
  case object OK extends KVResp
  case class Error(reason: String) extends KVResp
  case class Data(obj: Option[DataObject]) extends KVResp

  def props = Props(classOf[KV])
}

class KV extends Actor with Stash with ActorLogging {
  import sample.race.KV._

  implicit val timeout: Timeout = 5 seconds
  implicit val ec = context dispatcher

  var maybe_seq: Option[DataObject.SeqID] = None
  val backend = context actorOf Backend.props

  backend ask Backend.GetLastSeq pipeTo self
  def receive = init

  def init: Receive = {
    case Backend.Seq(s) =>
      maybe_seq = Some(s)
      unstashAll()
      context become normal
    case _ => stash()
  }

  case object PutVersionMismatch extends Throwable("Version mismatch on put")

  def preparePut(obj: DataObject) = backend ask Backend.Get(obj.key) flatMap {
    case Backend.GetRes(Some(old_obj: DataObject)) if old_obj.version <= obj.version =>
      backend ask Backend.Put(obj update_version maybe_seq.get.increment)
    case Backend.GetRes(None) =>
      backend ask Backend.Put(obj update_version maybe_seq.get.increment)
    case Backend.GetRes(Some(_: DataObject)) =>
      Futures.failed(PutVersionMismatch)
  }

  case class PutRes(sender: ActorRef, x: Any)

  def normal: Receive = {
    case g: Get => handleGet(g)

    // Naïve and wrong
    case Put(obj, true) =>
      val put_sender = sender()
      preparePut(obj) onComplete {
        case Success(o) => self ! PutRes(put_sender, o)
        case Failure(f) => self ! PutRes(put_sender, f)
      }
    case PutRes(put_sender, Backend.PutAck(seq)) =>
      maybe_seq = Some(seq)
      put_sender ! OK
    case PutRes(put_sender, f) =>
      put_sender ! Error(f.toString)

    // The right way
    case Put(obj, false) =>
      val put_sender = sender()

      preparePut(obj) pipeTo self

      context become {
        case Backend.PutAck(seq) =>
          maybe_seq = Some(seq)
          put_sender ! OK
          unstashAll()
          context become normal
        case f: Status.Failure =>
          put_sender ! Error(f.toString)
          unstashAll()
          context become normal

        case g@Get(k) if k != obj.key => handleGet(g)
        case _ => stash()
      }
  }

  private def handleGet(g: Get) = g match {
    case Get(key) =>
      (backend ? Backend.Get(key))
        .mapTo[Backend.GetRes]
        .map(r => Data(r.obj))
        .pipeTo(sender())
  }
}

object Main extends App {

  implicit val timeout: Timeout = 5 seconds

  def api_test(): Unit = {
    val system = ActorSystem("KV_API")
    val replica = system actorOf KV.props
    implicit val ec = system.dispatcher

    val key = "foo"
    val f0 = for {
      _ <- replica ? KV.Put(DataObject(key, "banana"))
      KV.Data(get_res1) <- (replica ? KV.Get(key)).mapTo[KV.Data]
      _ <- replica ? KV.Put(get_res1.get.update("bar")) if get_res1.isDefined
      KV.Data(get_res2) <- (replica ? KV.Get(key)).mapTo[KV.Data]
    } yield println(s"Result1: $get_res1\nResult2: $get_res2")
    Await.result(f0, 1 second)
    system.shutdown()
  }

  def race_test(naive: Boolean) {
    val system = ActorSystem("KVRace")
    val replica = system actorOf KV.props
    implicit val ec = system.dispatcher

    val key2 = "bar"
    val f1 = replica ask KV.Put(DataObject(key2, "1"), naive)
    val f2 = replica ask KV.Put(DataObject(key2, "2"), naive)
    for (fi <- Seq(f1, f2)) println(s"Res: ${Await.result(fi, 5 seconds)}")
    val f3 = for { gr <- replica.ask(KV.Get(key2)).mapTo[KV.Data] }
             yield println(s"Get: $gr")
    Await.result(f3, 1 second)

    system.shutdown()
  }

  api_test()
  println("========================================")
  race_test(naive = false); println("--------------")
  race_test(naive = false); println("--------------")
  race_test(naive = false)
  println("=======================================")
  race_test(naive = true); println("--------------")
  race_test(naive = true); println("--------------")
  race_test(naive = true)
}