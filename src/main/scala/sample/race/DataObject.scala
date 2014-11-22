package sample.race

import akka.util.ByteString

object DataObject {
  object SeqID {
    def apply() = new SeqID
  }
  class SeqID (val seq: Long = 0) extends AnyVal {
    def increment = new SeqID(seq + 1)
    def <= (that: SeqID) = seq <= that.seq
    def max(that: SeqID) = new SeqID(Math.max(seq, that.seq))
  }


  type Key = String
  type Value = ByteString
  def apply(key: String, value: String) = new DataObject(key, ByteString.fromString(value))
}
case class DataObject(key: DataObject.Key,
                      value: DataObject.Value,
                      version: DataObject.SeqID = DataObject.SeqID()) {
  import DataObject._
  def update(newVal: ByteString) = new DataObject(key, newVal, version)
  def update(newVal: String) = new DataObject(key, ByteString.fromString(newVal), version)
  def update_version(newVer: SeqID) =  new DataObject(key, value, newVer)
  override def toString = s"DataObject{ key: '$key'; value: '${value.utf8String}' }"
}