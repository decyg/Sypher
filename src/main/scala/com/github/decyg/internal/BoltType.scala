package com.github.decyg.internal

import com.github.decyg.internal.codecs._
import scodec.codecs.implicits._
import scodec.bits._
import scodec._
import scodec.codecs._
import shapeless.{HNil, Typeable}


sealed trait BoltType
case class BoltNull() extends BoltType
case class BoltBoolean(b: Boolean) extends BoltType
case class BoltInteger(i: BigInt) extends BoltType
case class BoltFloat(d: Double) extends BoltType
case class BoltString(s: String) extends BoltType
case class BoltList(l: Seq[BoltType]) extends BoltType
case class BoltMap(m: Map[BoltType, BoltType]) extends BoltType

// Bolt nodes etc are actually just structure types, just defined by a different signature from normal
// the below structures also use the reduced versions of each, as the types when the structs are formed will be checked at runtime
// note this is suboptimal and i'm aware, i just couldn't figure out how to get scodec to respect nested ADTs with parameterised types
sealed trait BoltStructure extends BoltType

case class BoltStructureContainer(marker: BitVector, l: Seq[BoltType] = Seq()) extends BoltStructure with BoltMessage
case class BoltNode(nodeIdentity: BigInt, labels: Seq[String], properties: Map[String, BoltType]) extends BoltStructure
case class BoltRelationship(relIdentity: BigInt, startNodeIdentity: BigInt, endNodeIdentity: BigInt, `type`: String, properties: Map[String, BoltType]) extends BoltStructure
case class BoltUnboundRelationship(relIdentity: BigInt, `type`: String, properties: Map[String, BoltType]) extends BoltStructure
case class BoltPath(nodes: Seq[BoltNode], relationships: Seq[BoltUnboundRelationship], sequence: Seq[BigInt]) extends BoltStructure

// a boltmessage is an even further specialisation of a generic
sealed trait BoltMessage extends BoltStructure

case class BoltInit(clientName: String, authToken: Map[String, BoltType]) extends BoltMessage
case class BoltRun(statement: String, parameters: Map[String, BoltType]) extends BoltMessage
case class BoltDiscardAll() extends BoltMessage
case class BoltPullAll() extends BoltMessage
case class BoltAckFailure() extends BoltMessage
case class BoltReset() extends BoltMessage
case class BoltRecord(fields: Seq[BoltType]) extends BoltMessage
case class BoltSuccess(metadata: Map[String, BoltType]) extends BoltMessage
case class BoltFailure(metadata: Map[String, BoltType]) extends BoltMessage
case class BoltIgnored() extends BoltMessage

case class BoltTransferEncoding(message: BoltMessage)

object BoltType {

  def typedListCodec[T <: BoltType](t: Typeable[T]): Codec[BoltList] = {

    val f = (bl: BoltList) => {
      if(bl.l.forall(a => t.cast(a).isDefined)) Attempt.successful(bl) else Attempt.failure(Err("Types in BoltList are not contiguous"))
    }

    Codec[BoltList].exmap[BoltList](f, f)
  }

  def typedMapCodec[L <: BoltType, R <: BoltType](l: Typeable[L], r: Typeable[R]): Codec[BoltMap] = {

    val f = (bm: BoltMap) => {
      if(bm.m.forall{ case (lV, rV) => l.cast(lV).isDefined && r.cast(rV).isDefined}) Attempt.successful(bm) else Attempt.failure(Err("Types in BoltMap are not contiguous"))
    }

    Codec[BoltMap].exmap[BoltMap](f, f)
  }

  implicit lazy val codec: Codec[BoltType] = shapeless.lazily {
    discriminated[BoltType].by(marker)
      .typecase(BoltTypeMarker.NULL, `null`)
      .typecase(BoltTypeMarker.BOOLEAN, boolean)
      .typecase(BoltTypeMarker.INTEGER, integer)
      .typecase(BoltTypeMarker.FLOAT, float)
      .typecase(BoltTypeMarker.STRING, string)
      .typecase(BoltTypeMarker.LIST, list)
      .typecase(BoltTypeMarker.MAP, map)
      .typecase(BoltTypeMarker.STRUCTURE, structure)
  }

  implicit val marker: Codec[BoltTypeMarker.WithRange] = BoltTypeMarkerCodec

  implicit val `null`: Codec[BoltNull] = BoltNullCodec

  implicit val boolean: Codec[BoltBoolean] = BoltBooleanCodec

  implicit val integer: Codec[BoltInteger] = BoltIntegerCodec

  implicit val float: Codec[BoltFloat] = BoltFloatCodec

  implicit val string: Codec[BoltString] = BoltStringCodec

  implicit val list: Codec[BoltList] = BoltListCodec

  implicit val map: Codec[BoltMap] = BoltMapCodec

  implicit val structure: Codec[BoltStructure] = BoltStructureCodec

}
