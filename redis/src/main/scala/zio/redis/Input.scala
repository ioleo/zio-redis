/*
 * Copyright 2021 John A. De Goes and the ZIO contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.redis

import zio._
import zio.schema.Schema
import zio.schema.codec.BinaryCodec

import java.time.Instant
import java.util.concurrent.TimeUnit

sealed trait Input[-A] {
  self =>

  private[redis] def encode(data: A)(implicit codec: BinaryCodec): RespCommand

  final def contramap[B](f: B => A): Input[B] = new Input[B] {
    def encode(data: B)(implicit codec: BinaryCodec): RespCommand = self.encode(f(data))
  }
}

object Input {

  def apply[A](implicit input: Input[A]): Input[A] = input

  case object AbsTtlInput extends Input[AbsTtl] {
    def encode(data: AbsTtl)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal(data.stringify))
  }

  case object AddressInput extends Input[Address] {
    def encode(data: Address)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Unknown(data.stringify))
  }

  case object AggregateInput extends Input[Aggregate] {
    def encode(data: Aggregate)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal("AGGREGATE"), RespArgument.Literal(data.stringify))
  }

  case object AlphaInput extends Input[Alpha] {
    def encode(data: Alpha)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal(data.stringify))
  }

  case object AuthInput extends Input[Auth] {
    def encode(data: Auth)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal("AUTH"), RespArgument.Value(data.password))
  }

  case object BoolInput extends Input[Boolean] {
    def encode(data: Boolean)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal(if (data) "1" else "0"))
  }

  case object StralgoLcsQueryTypeInput extends Input[StrAlgoLcsQueryType] {
    def encode(data: StrAlgoLcsQueryType)(implicit codec: BinaryCodec): RespCommand = data match {
      case StrAlgoLcsQueryType.Len => RespCommand(RespArgument.Literal("LEN"))
      case StrAlgoLcsQueryType.Idx(minMatchLength, withMatchLength) => {
        val idx = Chunk.single(RespArgument.Literal("IDX"))
        val min =
          if (minMatchLength > 1)
            Chunk(RespArgument.Literal("MINMATCHLEN"), RespArgument.Unknown(minMatchLength.toString))
          else Chunk.empty[RespArgument]
        val length =
          if (withMatchLength) Chunk.single(RespArgument.Literal("WITHMATCHLEN")) else Chunk.empty[RespArgument]
        RespCommand(Chunk(idx, min, length).flatten)
      }
    }
  }

  case object BitFieldCommandInput extends Input[BitFieldCommand] {
    def encode(data: BitFieldCommand)(implicit codec: BinaryCodec): RespCommand = {
      import BitFieldCommand._

      val respArgs = data match {
        case BitFieldGet(t, o) =>
          Chunk(RespArgument.Literal("GET"), RespArgument.Unknown(t.stringify), RespArgument.Unknown(o.toString))
        case BitFieldSet(t, o, v) =>
          Chunk(
            RespArgument.Literal("SET"),
            RespArgument.Unknown(t.stringify),
            RespArgument.Unknown(o.toString),
            RespArgument.Unknown(v.toString)
          )
        case BitFieldIncr(t, o, i) =>
          Chunk(
            RespArgument.Literal("INCRBY"),
            RespArgument.Unknown(t.stringify),
            RespArgument.Unknown(o.toString),
            RespArgument.Unknown(i.toString)
          )
        case bfo: BitFieldOverflow => Chunk(RespArgument.Literal("OVERFLOW"), RespArgument.Literal(bfo.stringify))
      }
      RespCommand(respArgs)
    }
  }

  case object BitOperationInput extends Input[BitOperation] {
    def encode(data: BitOperation)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal(data.stringify))
  }

  case object BitPosRangeInput extends Input[BitPosRange] {
    def encode(data: BitPosRange)(implicit codec: BinaryCodec): RespCommand = {
      val start    = RespArgument.Unknown(data.start.toString)
      val respArgs = data.end.fold(Chunk.single(start))(end => Chunk(start, RespArgument.Unknown(end.toString)))
      RespCommand(respArgs)
    }
  }

  case object ByInput extends Input[String] {
    def encode(data: String)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal("BY"), RespArgument.Unknown(data))
  }

  case object ChangedInput extends Input[Changed] {
    def encode(data: Changed)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal(data.stringify))
  }

  case object ClientKillInput extends Input[ClientKillFilter] {
    def encode(data: ClientKillFilter)(implicit codec: BinaryCodec): RespCommand = data match {
      case addr: ClientKillFilter.Address =>
        RespCommand(RespArgument.Literal("ADDR"), RespArgument.Unknown(addr.stringify))
      case laddr: ClientKillFilter.LocalAddress =>
        RespCommand(RespArgument.Literal("LADDR"), RespArgument.Unknown(laddr.stringify))
      case ClientKillFilter.Id(clientId) =>
        RespCommand(RespArgument.Literal("ID"), RespArgument.Unknown(clientId.toString))
      case ClientKillFilter.Type(clientType) =>
        RespCommand(RespArgument.Literal("TYPE"), RespArgument.Literal(clientType.stringify))
      case ClientKillFilter.User(username) => RespCommand(RespArgument.Literal("USER"), RespArgument.Unknown(username))
      case ClientKillFilter.SkipMe(skip) =>
        RespCommand(RespArgument.Literal("SKIPME"), RespArgument.Literal(if (skip) "YES" else "NO"))
    }
  }

  case object ClientPauseModeInput extends Input[ClientPauseMode] {
    def encode(data: ClientPauseMode)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal(data.stringify))
  }

  case object ClientTrackingInput
      extends Input[Option[(Option[Long], Option[ClientTrackingMode], Boolean, Chunk[String])]] {
    def encode(
      data: Option[(Option[Long], Option[ClientTrackingMode], Boolean, Chunk[String])]
    )(implicit codec: BinaryCodec): RespCommand =
      data match {
        case Some((clientRedir, mode, noLoop, prefixes)) =>
          val modeChunk = mode match {
            case Some(ClientTrackingMode.OptIn)     => RespCommand(RespArgument.Literal("OPTIN"))
            case Some(ClientTrackingMode.OptOut)    => RespCommand(RespArgument.Literal("OPTOUT"))
            case Some(ClientTrackingMode.Broadcast) => RespCommand(RespArgument.Literal("BCAST"))
            case None                               => RespCommand.empty
          }
          val loopChunk = if (noLoop) RespCommand(RespArgument.Literal("NOLOOP")) else RespCommand.empty
          RespCommand(RespArgument.Literal("ON")) ++
            clientRedir.fold(RespCommand.empty)(id =>
              RespCommand(RespArgument.Literal("REDIRECT"), RespArgument.Unknown(id.toString))
            ) ++
            RespCommand(
              prefixes.flatMap(prefix => Chunk(RespArgument.Literal("PREFIX"), RespArgument.Unknown(prefix)))
            ) ++
            modeChunk ++
            loopChunk
        case None =>
          RespCommand(RespArgument.Literal("OFF"))
      }
  }

  case object CopyInput extends Input[Copy] {
    def encode(data: Copy)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal(data.stringify))
  }

  case object CountInput extends Input[Count] {
    def encode(data: Count)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal("COUNT"), RespArgument.Unknown(data.count.toString))
  }

  case object RedisTypeInput extends Input[RedisType] {
    def encode(data: RedisType)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal("TYPE"), RespArgument.Literal(data.stringify))
  }

  case object PatternInput extends Input[Pattern] {
    def encode(data: Pattern)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal("MATCH"), RespArgument.Unknown(data.pattern))
  }

  case object GetInput extends Input[String] {
    def encode(data: String)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal("GET"), RespArgument.Unknown(data))
  }

  case object PositionInput extends Input[Position] {
    def encode(data: Position)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal(data.stringify))
  }

  case object SideInput extends Input[Side] {
    def encode(data: Side)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal(data.stringify))
  }

  case object DoubleInput extends Input[Double] {
    def encode(data: Double)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Unknown(data.toString))
  }

  case object DurationMillisecondsInput extends Input[Duration] {
    def encode(data: Duration)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Unknown(data.toMillis.toString))
  }

  case object DurationSecondsInput extends Input[Duration] {
    def encode(data: Duration)(implicit codec: BinaryCodec): RespCommand = {
      val seconds = TimeUnit.MILLISECONDS.toSeconds(data.toMillis)
      RespCommand(RespArgument.Unknown(seconds.toString))
    }
  }

  case object DurationTtlInput extends Input[Duration] {
    def encode(data: Duration)(implicit codec: BinaryCodec): RespCommand = {
      val milliseconds = data.toMillis
      RespCommand(RespArgument.Literal("PX"), RespArgument.Unknown(milliseconds.toString))
    }
  }

  case object FreqInput extends Input[Freq] {
    def encode(data: Freq)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal("FREQ"), RespArgument.Unknown(data.frequency))
  }

  case object IdleTimeInput extends Input[IdleTime] {
    def encode(data: IdleTime)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal("IDLETIME"), RespArgument.Unknown(data.seconds.toString))
  }

  case object IncrementInput extends Input[Increment] {
    def encode(data: Increment)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal(data.stringify))
  }

  case object KeepTtlInput extends Input[KeepTtl] {
    def encode(data: KeepTtl)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal(data.stringify))
  }

  case object LimitInput extends Input[Limit] {
    def encode(data: Limit)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(
        RespArgument.Literal("LIMIT"),
        RespArgument.Unknown(data.offset.toString),
        RespArgument.Unknown(data.count.toString)
      )
  }

  case object LongInput extends Input[Long] {
    def encode(data: Long)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Unknown(data.toString))
  }

  case object LongLatInput extends Input[LongLat] {
    def encode(data: LongLat)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Unknown(data.longitude.toString), RespArgument.Unknown(data.latitude.toString))
  }

  final case class MemberScoreInput[M: Schema]() extends Input[MemberScore[M]] {
    def encode(data: MemberScore[M])(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Unknown(data.score.toString), RespArgument.Value(data.member))
  }

  case object NoInput extends Input[Unit] {
    def encode(data: Unit)(implicit codec: BinaryCodec): RespCommand = RespCommand.empty
  }

  final case class NonEmptyList[-A](input: Input[A]) extends Input[(A, List[A])] {
    def encode(data: (A, List[A]))(implicit codec: BinaryCodec): RespCommand =
      (data._1 :: data._2).foldLeft(RespCommand.empty)((acc, a) => acc ++ input.encode(a))
  }

  case object OrderInput extends Input[Order] {
    def encode(data: Order)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Unknown(data.stringify))
  }

  case object RadiusUnitInput extends Input[RadiusUnit] {
    def encode(data: RadiusUnit)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Unknown(data.stringify))
  }

  case object RangeInput extends Input[Range] {
    def encode(data: Range)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Unknown(data.start.toString), RespArgument.Unknown(data.end.toString))
  }

  case object ReplaceInput extends Input[Replace] {
    def encode(data: Replace)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal(data.stringify))
  }

  case object StoreDistInput extends Input[StoreDist] {
    def encode(data: StoreDist)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal("STOREDIST"), RespArgument.Unknown(data.key))
  }

  case object StoreInput extends Input[Store] {
    def encode(data: Store)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal("STORE"), RespArgument.Unknown(data.key))
  }

  case object StringInput extends Input[String] {
    def encode(data: String)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Unknown(data))
  }

  case object CommandNameInput extends Input[String] {
    def encode(data: String)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.CommandName(data))
  }

  final case class ArbitraryValueInput[A: Schema]() extends Input[A] {
    def encode(data: A)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Value(data))
  }

  final case class ArbitraryKeyInput[A: Schema]() extends Input[A] {
    def encode(data: A)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Key(data))
  }

  case object ValueInput extends Input[Chunk[Byte]] {
    def encode(data: Chunk[Byte])(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Value(data))
  }

  final case class OptionalInput[-A](a: Input[A]) extends Input[Option[A]] {
    def encode(data: Option[A])(implicit codec: BinaryCodec): RespCommand =
      data.fold(RespCommand.empty)(a.encode)
  }

  case object TimeSecondsInput extends Input[Instant] {
    def encode(data: Instant)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Unknown(data.getEpochSecond.toString))
  }

  case object TimeMillisecondsInput extends Input[Instant] {
    def encode(data: Instant)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Unknown(data.toEpochMilli.toString))
  }

  case object WeightsInput extends Input[::[Double]] {
    def encode(data: ::[Double])(implicit codec: BinaryCodec): RespCommand = {
      val args = data.foldLeft(Chunk.single[RespArgument](RespArgument.Literal("WEIGHTS"))) { (acc, a) =>
        acc ++ Chunk.single(RespArgument.Unknown(a.toString))
      }
      RespCommand(args)
    }
  }

  case object IdleInput extends Input[Duration] {
    def encode(data: Duration)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal("IDLE"), RespArgument.Unknown(data.toMillis.toString))
  }

  case object TimeInput extends Input[Duration] {
    def encode(data: Duration)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal("TIME"), RespArgument.Unknown(data.toMillis.toString))
  }

  case object RetryCountInput extends Input[Long] {
    def encode(data: Long)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal("RETRYCOUNT"), RespArgument.Unknown(data.toString))
  }

  final case class XGroupCreateInput[K: Schema, G: Schema, I: Schema]() extends Input[XGroupCommand.Create[K, G, I]] {
    def encode(data: XGroupCommand.Create[K, G, I])(implicit codec: BinaryCodec): RespCommand = {
      val chunk = Chunk(
        RespArgument.Literal("CREATE"),
        RespArgument.Key(data.key),
        RespArgument.Unknown(data.group),
        RespArgument.Unknown(data.id)
      )

      RespCommand(if (data.mkStream) chunk :+ RespArgument.Literal(MkStream.stringify) else chunk)
    }
  }

  final case class XGroupSetIdInput[K: Schema, G: Schema, I: Schema]() extends Input[XGroupCommand.SetId[K, G, I]] {
    def encode(data: XGroupCommand.SetId[K, G, I])(implicit codec: BinaryCodec): RespCommand =
      RespCommand(
        RespArgument.Literal("SETID"),
        RespArgument.Key(data.key),
        RespArgument.Unknown(data.group),
        RespArgument.Unknown(data.id)
      )
  }

  final case class XGroupDestroyInput[K: Schema, G: Schema]() extends Input[XGroupCommand.Destroy[K, G]] {
    def encode(data: XGroupCommand.Destroy[K, G])(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal("DESTROY"), RespArgument.Key(data.key), RespArgument.Unknown(data.group))
  }

  final case class XGroupCreateConsumerInput[K: Schema, G: Schema, C: Schema]()
      extends Input[XGroupCommand.CreateConsumer[K, G, C]] {
    def encode(data: XGroupCommand.CreateConsumer[K, G, C])(implicit codec: BinaryCodec): RespCommand =
      RespCommand(
        RespArgument.Literal("CREATECONSUMER"),
        RespArgument.Key(data.key),
        RespArgument.Unknown(data.group),
        RespArgument.Unknown(data.consumer)
      )
  }

  final case class XGroupDelConsumerInput[K: Schema, G: Schema, C: Schema]()
      extends Input[XGroupCommand.DelConsumer[K, G, C]] {
    def encode(data: XGroupCommand.DelConsumer[K, G, C])(implicit codec: BinaryCodec): RespCommand =
      RespCommand(
        RespArgument.Literal("DELCONSUMER"),
        RespArgument.Key(data.key),
        RespArgument.Unknown(data.group),
        RespArgument.Unknown(data.consumer)
      )
  }

  case object BlockInput extends Input[Duration] {
    def encode(data: Duration)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal("BLOCK"), RespArgument.Unknown(data.toMillis.toString))
  }

  final case class StreamsInput[K: Schema, V: Schema]() extends Input[((K, V), Chunk[(K, V)])] {
    def encode(data: ((K, V), Chunk[(K, V)]))(implicit codec: BinaryCodec): RespCommand = {
      val (keys, ids) = (data._1 +: data._2).map { case (key, value) =>
        (RespArgument.Key(key), RespArgument.Value(value))
      }.unzip

      RespCommand(Chunk.single(RespArgument.Literal("STREAMS")) ++ keys ++ ids)
    }
  }

  case object NoAckInput extends Input[NoAck] {
    def encode(data: NoAck)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Unknown(data.stringify))
  }

  case object StreamMaxLenInput extends Input[StreamMaxLen] {
    def encode(data: StreamMaxLen)(implicit codec: BinaryCodec): RespCommand = {
      val chunk =
        if (data.approximate) Chunk(RespArgument.Literal("MAXLEN"), RespArgument.Literal("~"))
        else Chunk.single(RespArgument.Literal("MAXLEN"))

      RespCommand(chunk :+ RespArgument.Unknown(data.count.toString))
    }
  }

  case object ListMaxLenInput extends Input[ListMaxLen] {
    def encode(data: ListMaxLen)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal("MAXLEN"), RespArgument.Unknown(data.count.toString))
  }

  case object RankInput extends Input[Rank] {
    def encode(data: Rank)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal("RANK"), RespArgument.Unknown(data.rank.toString))
  }

  final case class Tuple2[-A, -B](_1: Input[A], _2: Input[B]) extends Input[(A, B)] {
    def encode(data: (A, B))(implicit codec: BinaryCodec): RespCommand =
      _1.encode(data._1) ++ _2.encode(data._2)
  }

  final case class Tuple3[-A, -B, -C](_1: Input[A], _2: Input[B], _3: Input[C]) extends Input[(A, B, C)] {
    def encode(data: (A, B, C))(implicit codec: BinaryCodec): RespCommand =
      _1.encode(data._1) ++ _2.encode(data._2) ++ _3.encode(data._3)
  }

  final case class Tuple4[-A, -B, -C, -D](_1: Input[A], _2: Input[B], _3: Input[C], _4: Input[D])
      extends Input[(A, B, C, D)] {
    def encode(data: (A, B, C, D))(implicit codec: BinaryCodec): RespCommand =
      _1.encode(data._1) ++ _2.encode(data._2) ++ _3.encode(data._3) ++ _4.encode(data._4)
  }

  final case class Tuple5[-A, -B, -C, -D, -E](_1: Input[A], _2: Input[B], _3: Input[C], _4: Input[D], _5: Input[E])
      extends Input[(A, B, C, D, E)] {
    def encode(data: (A, B, C, D, E))(implicit codec: BinaryCodec): RespCommand =
      _1.encode(data._1) ++ _2.encode(data._2) ++ _3.encode(data._3) ++ _4.encode(data._4) ++ _5.encode(data._5)
  }

  final case class Tuple6[-A, -B, -C, -D, -E, -F](
    _1: Input[A],
    _2: Input[B],
    _3: Input[C],
    _4: Input[D],
    _5: Input[E],
    _6: Input[F]
  ) extends Input[(A, B, C, D, E, F)] {
    def encode(data: (A, B, C, D, E, F))(implicit codec: BinaryCodec): RespCommand =
      _1.encode(data._1) ++ _2.encode(data._2) ++ _3.encode(data._3) ++ _4.encode(data._4) ++ _5.encode(data._5) ++
        _6.encode(data._6)
  }

  final case class Tuple7[-A, -B, -C, -D, -E, -F, -G](
    _1: Input[A],
    _2: Input[B],
    _3: Input[C],
    _4: Input[D],
    _5: Input[E],
    _6: Input[F],
    _7: Input[G]
  ) extends Input[(A, B, C, D, E, F, G)] {
    def encode(data: (A, B, C, D, E, F, G))(implicit codec: BinaryCodec): RespCommand =
      _1.encode(data._1) ++ _2.encode(data._2) ++ _3.encode(data._3) ++ _4.encode(data._4) ++ _5.encode(data._5) ++
        _6.encode(data._6) ++ _7.encode(data._7)
  }

  final case class Tuple9[-A, -B, -C, -D, -E, -F, -G, -H, -I](
    _1: Input[A],
    _2: Input[B],
    _3: Input[C],
    _4: Input[D],
    _5: Input[E],
    _6: Input[F],
    _7: Input[G],
    _8: Input[H],
    _9: Input[I]
  ) extends Input[(A, B, C, D, E, F, G, H, I)] {
    def encode(data: (A, B, C, D, E, F, G, H, I))(implicit codec: BinaryCodec): RespCommand =
      _1.encode(data._1) ++ _2.encode(data._2) ++ _3.encode(data._3) ++ _4.encode(data._4) ++ _5.encode(data._5) ++
        _6.encode(data._6) ++ _7.encode(data._7) ++ _8.encode(data._8) ++ _9.encode(data._9)
  }

  final case class Tuple10[-A, -B, -C, -D, -E, -F, -G, -H, -I, -J](
    _1: Input[A],
    _2: Input[B],
    _3: Input[C],
    _4: Input[D],
    _5: Input[E],
    _6: Input[F],
    _7: Input[G],
    _8: Input[H],
    _9: Input[I],
    _10: Input[J]
  ) extends Input[(A, B, C, D, E, F, G, H, I, J)] {
    def encode(data: (A, B, C, D, E, F, G, H, I, J))(implicit codec: BinaryCodec): RespCommand =
      _1.encode(data._1) ++ _2.encode(data._2) ++ _3.encode(data._3) ++ _4.encode(data._4) ++
        _5.encode(data._5) ++ _6.encode(data._6) ++ _7.encode(data._7) ++ _8.encode(data._8) ++
        _9.encode(data._9) ++ _10.encode(data._10)
  }

  final case class Tuple11[-A, -B, -C, -D, -E, -F, -G, -H, -I, -J, -K](
    _1: Input[A],
    _2: Input[B],
    _3: Input[C],
    _4: Input[D],
    _5: Input[E],
    _6: Input[F],
    _7: Input[G],
    _8: Input[H],
    _9: Input[I],
    _10: Input[J],
    _11: Input[K]
  ) extends Input[(A, B, C, D, E, F, G, H, I, J, K)] {
    def encode(data: (A, B, C, D, E, F, G, H, I, J, K))(implicit codec: BinaryCodec): RespCommand =
      _1.encode(data._1) ++ _2.encode(data._2) ++ _3.encode(data._3) ++ _4.encode(data._4) ++
        _5.encode(data._5) ++ _6.encode(data._6) ++ _7.encode(data._7) ++ _8.encode(data._8) ++
        _9.encode(data._9) ++ _10.encode(data._10) ++ _11.encode(data._11)
  }

  case object UpdateInput extends Input[Update] {
    def encode(data: Update)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Unknown(data.stringify))
  }

  final case class GetExPersistInput[K: Schema]() extends Input[(K, Boolean)] {
    def encode(data: (K, Boolean))(implicit codec: BinaryCodec): RespCommand =
      RespCommand(
        if (data._2) Chunk(RespArgument.Key(data._1), RespArgument.Literal("PERSIST"))
        else Chunk(RespArgument.Key(data._1))
      )
  }

  final case class GetExInput[K: Schema]() extends Input[(K, Expire, Duration)] {
    def encode(data: (K, Expire, Duration))(implicit codec: BinaryCodec): RespCommand =
      data match {
        case (key, Expire.SetExpireSeconds, duration) =>
          RespCommand(RespArgument.Key(key), RespArgument.Literal("EX")) ++ DurationSecondsInput.encode(duration)
        case (key, Expire.SetExpireMilliseconds, duration) =>
          RespCommand(RespArgument.Key(key), RespArgument.Literal("PX")) ++ DurationMillisecondsInput.encode(duration)
      }
  }

  final case class GetExAtInput[K: Schema]() extends Input[(K, ExpiredAt, Instant)] {
    def encode(data: (K, ExpiredAt, Instant))(implicit codec: BinaryCodec): RespCommand =
      data match {
        case (key, ExpiredAt.SetExpireAtSeconds, instant) =>
          RespCommand(RespArgument.Key(key), RespArgument.Literal("EXAT")) ++ TimeSecondsInput.encode(instant)
        case (key, ExpiredAt.SetExpireAtMilliseconds, instant) =>
          RespCommand(RespArgument.Key(key), RespArgument.Literal("PXAT")) ++ TimeMillisecondsInput.encode(instant)
      }
  }

  case object IdInput extends Input[Long] {
    def encode(data: Long)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal("ID"), RespArgument.Unknown(data.toString))
  }

  case object UnblockBehaviorInput extends Input[UnblockBehavior] {
    def encode(data: UnblockBehavior)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Unknown(data.stringify))
  }

  final case class Varargs[-A](input: Input[A]) extends Input[Iterable[A]] {
    def encode(data: Iterable[A])(implicit codec: BinaryCodec): RespCommand =
      data.foldLeft(RespCommand.empty)((acc, a) => acc ++ input.encode(a))
  }

  final case class EvalInput[-K, -V](inputK: Input[K], inputV: Input[V]) extends Input[(String, Chunk[K], Chunk[V])] {
    def encode(data: (String, Chunk[K], Chunk[V]))(implicit codec: BinaryCodec): RespCommand = {
      val (lua, keys, args) = data
      val encodedScript     = RespCommand(RespArgument.Unknown(lua), RespArgument.Unknown(keys.size.toString))
      val encodedKeys = keys.foldLeft(RespCommand.empty)((acc, a) =>
        acc ++ inputK.encode(a).mapArguments(arg => RespArgument.Key(arg.value.value))
      )
      val encodedArgs = args.foldLeft(RespCommand.empty)((acc, a) =>
        acc ++ inputV.encode(a).mapArguments(arg => RespArgument.Value(arg.value.value))
      )
      encodedScript ++ encodedKeys ++ encodedArgs
    }
  }

  case object ScriptDebugInput extends Input[DebugMode] {
    def encode(data: DebugMode)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal(data.stringify))
  }

  case object ScriptFlushInput extends Input[FlushMode] {
    def encode(data: FlushMode)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal(data.stringify))
  }

  case object WithScoresInput extends Input[WithScores] {
    def encode(data: WithScores)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal(data.stringify))
  }

  case object WithCoordInput extends Input[WithCoord] {
    def encode(data: WithCoord)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal(data.stringify))
  }

  case object WithDistInput extends Input[WithDist] {
    def encode(data: WithDist)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal(data.stringify))
  }

  case object WithHashInput extends Input[WithHash] {
    def encode(data: WithHash)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal(data.stringify))
  }

  case object WithForceInput extends Input[WithForce] {
    def encode(data: WithForce)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal(data.stringify))
  }

  case object WithJustIdInput extends Input[WithJustId] {
    def encode(data: WithJustId)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal(data.stringify))
  }

  case object YesNoInput extends Input[Boolean] {
    def encode(data: Boolean)(implicit codec: BinaryCodec): RespCommand =
      RespCommand(RespArgument.Literal(if (data) "YES" else "NO"))
  }
}
