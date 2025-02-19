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

package zio.redis.api

import zio._
import zio.redis.Input._
import zio.redis.Output._
import zio.redis.ResultBuilder._
import zio.redis._
import zio.schema.Schema

trait SortedSets extends RedisEnvironment {
  import SortedSets._

  /**
   * Remove and return the member with the highest score from one or more sorted sets, or block until one is available.
   *
   * @param timeout
   *   Maximum number of seconds to block. A timeout of zero can be used to block indefinitely
   * @param key
   *   Key of the set
   * @param keys
   *   Keys of the rest sets
   * @return
   *   A three-element Chunk with the first element being the name of the key where a member was popped, the second
   *   element is the popped member itself, and the third element is the score of the popped element. An empty chunk is
   *   returned when no element could be popped and the timeout expired.
   */
  final def bzPopMax[K: Schema](
    timeout: Duration,
    key: K,
    keys: K*
  ): ResultBuilder1[({ type lambda[x] = Option[(K, MemberScore[x])] })#lambda] =
    new ResultBuilder1[({ type lambda[x] = Option[(K, MemberScore[x])] })#lambda] {
      def returning[M: Schema]: IO[RedisError, Option[(K, MemberScore[M])]] = {
        val memberScoreOutput =
          Tuple3Output(ArbitraryOutput[K](), ArbitraryOutput[M](), DoubleOutput).map { case (k, m, s) =>
            (k, MemberScore(s, m))
          }
        val command = RedisCommand(
          BzPopMax,
          Tuple2(NonEmptyList(ArbitraryKeyInput[K]()), DurationSecondsInput),
          OptionalOutput(memberScoreOutput),
          codec,
          executor
        )
        command.run(((key, keys.toList), timeout))
      }
    }

  /**
   * Remove and return the member with the lowest score from one or more sorted sets, or block until one is available.
   *
   * @param timeout
   *   Maximum number of seconds to block. A timeout of zero can be used to block indefinitely
   * @param key
   *   Key of the set
   * @param keys
   *   Keys of the rest sets
   * @return
   *   A three-element Chunk with the first element being the name of the key where a member was popped, the second
   *   element is the popped member itself, and the third element is the score of the popped element. An empty chunk is
   *   returned when no element could be popped and the timeout expired.
   */
  final def bzPopMin[K: Schema](
    timeout: Duration,
    key: K,
    keys: K*
  ): ResultBuilder1[({ type lambda[x] = Option[(K, MemberScore[x])] })#lambda] =
    new ResultBuilder1[({ type lambda[x] = Option[(K, MemberScore[x])] })#lambda] {
      def returning[M: Schema]: IO[RedisError, Option[(K, MemberScore[M])]] = {
        val memberScoreOutput =
          Tuple3Output(ArbitraryOutput[K](), ArbitraryOutput[M](), DoubleOutput).map { case (k, m, s) =>
            (k, MemberScore(s, m))
          }
        val command = RedisCommand(
          BzPopMin,
          Tuple2(NonEmptyList(ArbitraryKeyInput[K]()), DurationSecondsInput),
          OptionalOutput(memberScoreOutput),
          codec,
          executor
        )
        command.run(((key, keys.toList), timeout))
      }
    }

  /**
   * Add one or more members to a sorted set, or update its score if it already exists.
   *
   * @param key
   *   Key of set to add to
   * @param update
   *   Set existing and never add elements or always set new elements and don't update existing elements
   * @param change
   *   Modify the return value from the number of new elements added, to the total number of elements change
   * @param memberScore
   *   Score that should be added to specific element for a given sorted set key
   * @param memberScores
   *   Rest scores that should be added to specific elements fr a given sorted set key
   * @return
   *   The number of elements added to the sorted set, not including elements already existing for which the score was
   *   updated.
   */
  final def zAdd[K: Schema, M: Schema](key: K, update: Option[Update] = None, change: Option[Changed] = None)(
    memberScore: MemberScore[M],
    memberScores: MemberScore[M]*
  ): IO[RedisError, Long] = {
    val command = RedisCommand(
      ZAdd,
      Tuple4(
        ArbitraryKeyInput[K](),
        OptionalInput(UpdateInput),
        OptionalInput(ChangedInput),
        NonEmptyList(MemberScoreInput[M]())
      ),
      LongOutput,
      codec,
      executor
    )
    command.run((key, update, change, (memberScore, memberScores.toList)))
  }

  /**
   * Add one or more members to a sorted set, or update its score if it already exists.
   *
   * @param key
   *   Key of set to add to.
   * @param update
   *   Set existing and never add elements or always set new elements and don't update existing elements
   * @param change
   *   Modify the return value from the number of new elements added, to the total number of elements change
   * @param increment
   *   When this option is specified ZADD acts like ZINCRBY. Only one score-element pair can be specified in this mode
   * @param memberScore
   *   Score that should be added to specific element for a given sorted set key
   * @param memberScores
   *   Rest scores that should be added to specific elements fr a given sorted set key
   * @return
   *   The new score of member (a double precision floating point number), or None if the operation was aborted (when
   *   called with either the XX or the NX option).
   */
  final def zAddWithIncr[K: Schema, M: Schema](key: K, update: Option[Update] = None, change: Option[Changed] = None)(
    increment: Increment,
    memberScore: MemberScore[M],
    memberScores: MemberScore[M]*
  ): IO[RedisError, Option[Double]] = {
    val command = RedisCommand(
      ZAdd,
      Tuple5(
        ArbitraryKeyInput[K](),
        OptionalInput(UpdateInput),
        OptionalInput(ChangedInput),
        IncrementInput,
        NonEmptyList(MemberScoreInput[M]())
      ),
      OptionalOutput(DoubleOutput),
      codec,
      executor
    )
    command.run((key, update, change, increment, (memberScore, memberScores.toList)))
  }

  /**
   * Get the number of members in a sorted set.
   *
   * @param key
   *   Key of a sorted set
   * @return
   *   The cardinality (number of elements) of the sorted set, or 0 if key does not exist.
   */
  final def zCard[K: Schema](key: K): IO[RedisError, Long] = {
    val command = RedisCommand(ZCard, ArbitraryKeyInput[K](), LongOutput, codec, executor)
    command.run(key)
  }

  /**
   * Returns the number of elements in the sorted set at key with a score between min and max.
   *
   * @param key
   *   Key of a sorted set
   * @param range
   *   Min and max score (including elements with score equal to min or max)
   * @return
   *   the number of elements in the specified score range.
   */
  final def zCount[K: Schema](key: K, range: Range): IO[RedisError, Long] = {
    val command = RedisCommand(ZCount, Tuple2(ArbitraryKeyInput[K](), RangeInput), LongOutput, codec, executor)
    command.run((key, range))
  }

  /**
   * Subtract multiple sorted sets and return members.
   *
   * @param inputKeysNum
   *   Number of input keys
   * @param key
   *   Key of a sorted set
   * @param keys
   *   Keys of other sorted sets
   * @return
   *   Chunk of differences between the first and successive input sorted sets.
   */
  final def zDiff[K: Schema](
    inputKeysNum: Long,
    key: K,
    keys: K*
  ): ResultBuilder1[Chunk] =
    new ResultBuilder1[Chunk] {
      def returning[M: Schema]: IO[RedisError, Chunk[M]] = {
        val command =
          RedisCommand(
            ZDiff,
            Tuple2(
              LongInput,
              NonEmptyList(ArbitraryKeyInput[K]())
            ),
            ChunkOutput(ArbitraryOutput[M]()),
            codec,
            executor
          )
        command.run((inputKeysNum, (key, keys.toList)))
      }
    }

  /**
   * Subtract multiple sorted sets and return members and their associated score.
   *
   * @param inputKeysNum
   *   Number of input keys
   * @param key
   *   Key of a sorted set
   * @param keys
   *   Keys of other sorted sets
   * @return
   *   Chunk of differences and scores between the first and successive input sorted sets.
   */
  final def zDiffWithScores[K: Schema](
    inputKeysNum: Long,
    key: K,
    keys: K*
  ): ResultBuilder1[MemberScores] =
    new ResultBuilder1[MemberScores] {
      def returning[M: Schema]: IO[RedisError, Chunk[MemberScore[M]]] = {
        val command =
          RedisCommand(
            ZDiff,
            Tuple3(
              LongInput,
              NonEmptyList(ArbitraryKeyInput[K]()),
              ArbitraryValueInput[String]()
            ),
            ChunkTuple2Output(ArbitraryOutput[M](), DoubleOutput)
              .map(_.map { case (m, s) => MemberScore(s, m) }),
            codec,
            executor
          )
        command.run((inputKeysNum, (key, keys.toList), WithScores.stringify))
      }
    }

  /**
   * Subtract multiple sorted sets and store the resulting sorted set in a destination key.
   *
   * @param destination
   *   Key of the output
   * @param inputKeysNum
   *   Number of input keys
   * @param key
   *   Key of a sorted set
   * @param keys
   *   Keys of other sorted sets
   * @return
   *   Chunk of differences between the first and successive input sorted sets.
   */
  final def zDiffStore[DK: Schema, K: Schema](
    destination: DK,
    inputKeysNum: Long,
    key: K,
    keys: K*
  ): IO[RedisError, Long] = {
    val command =
      RedisCommand(
        ZDiffStore,
        Tuple3(
          ArbitraryValueInput[DK](),
          LongInput,
          NonEmptyList(ArbitraryKeyInput[K]())
        ),
        LongOutput,
        codec,
        executor
      )
    command.run((destination, inputKeysNum, (key, keys.toList)))
  }

  /**
   * Increment the score of a member in a sorted set.
   *
   * @param key
   *   Key of a sorted set
   * @param increment
   *   Increment value
   * @param member
   *   Member of sorted set
   * @return
   *   The new score of member (a double precision floating point number).
   */
  final def zIncrBy[K: Schema, M: Schema](
    key: K,
    increment: Long,
    member: M
  ): IO[RedisError, Double] = {
    val command =
      RedisCommand(
        ZIncrBy,
        Tuple3(ArbitraryKeyInput[K](), LongInput, ArbitraryValueInput[M]()),
        DoubleOutput,
        codec,
        executor
      )
    command.run((key, increment, member))
  }

  /**
   * Intersect multiple sorted sets and return members.
   *
   * @param inputKeysNum
   *   Number of input keys
   * @param key
   *   Key of a sorted set
   * @param keys
   *   Keys of the rest sorted sets
   * @param aggregate
   *   With the AGGREGATE option, it is possible to specify how the results of the union are aggregated
   * @param weights
   *   Represents WEIGHTS option, it is possible to specify a multiplication factor for each input sorted set. This
   *   means that the score of every element in every input sorted set is multiplied by this factor before being passed
   *   to the aggregation function. When WEIGHTS is not given, the multiplication factors default to 1
   * @return
   *   Chunk containing the intersection of members.
   */
  final def zInter[K: Schema](inputKeysNum: Long, key: K, keys: K*)(
    aggregate: Option[Aggregate] = None,
    weights: Option[::[Double]] = None
  ): ResultBuilder1[Chunk] =
    new ResultBuilder1[Chunk] {
      def returning[M: Schema]: IO[RedisError, Chunk[M]] = {
        val command = RedisCommand(
          ZInter,
          Tuple4(
            LongInput,
            NonEmptyList(ArbitraryKeyInput[K]()),
            OptionalInput(AggregateInput),
            OptionalInput(WeightsInput)
          ),
          ChunkOutput(ArbitraryOutput[M]()),
          codec,
          executor
        )
        command.run((inputKeysNum, (key, keys.toList), aggregate, weights))
      }
    }

  /**
   * Intersect multiple sorted sets and return members and their associated score.
   *
   * @param inputKeysNum
   *   Number of input keys
   * @param key
   *   Key of a sorted set
   * @param keys
   *   Keys of the rest sorted sets
   * @param aggregate
   *   With the AGGREGATE option, it is possible to specify how the results of the union are aggregated
   * @param weights
   *   Represents WEIGHTS option, it is possible to specify a multiplication factor for each input sorted set. This
   *   means that the score of every element in every input sorted set is multiplied by this factor before being passed
   *   to the aggregation function. When WEIGHTS is not given, the multiplication factors default to 1
   * @return
   *   Chunk containing the intersection of members with their score.
   */
  final def zInterWithScores[K: Schema](inputKeysNum: Long, key: K, keys: K*)(
    aggregate: Option[Aggregate] = None,
    weights: Option[::[Double]] = None
  ): ResultBuilder1[MemberScores] =
    new ResultBuilder1[MemberScores] {
      def returning[M: Schema]: IO[RedisError, Chunk[MemberScore[M]]] = {
        val command = RedisCommand(
          ZInter,
          Tuple5(
            LongInput,
            NonEmptyList(ArbitraryKeyInput[K]()),
            OptionalInput(AggregateInput),
            OptionalInput(WeightsInput),
            ArbitraryValueInput[String]()
          ),
          ChunkTuple2Output(ArbitraryOutput[M](), DoubleOutput)
            .map(_.map { case (m, s) => MemberScore(s, m) }),
          codec,
          executor
        )
        command.run((inputKeysNum, (key, keys.toList), aggregate, weights, WithScores.stringify))
      }
    }

  /**
   * Intersect multiple sorted sets and store the resulting sorted set in a new key.
   *
   * @param destination
   *   Key of the output
   * @param inputKeysNum
   *   Number of input keys
   * @param key
   *   Key of a sorted set
   * @param keys
   *   Keys of the rest sorted sets
   * @param aggregate
   *   With the AGGREGATE option, it is possible to specify how the results of the union are aggregated
   * @param weights
   *   Represents WEIGHTS option, it is possible to specify a multiplication factor for each input sorted set. This
   *   means that the score of every element in every input sorted set is multiplied by this factor before being passed
   *   to the aggregation function. When WEIGHTS is not given, the multiplication factors default to 1
   * @return
   *   The number of elements in the resulting sorted set at destination.
   */
  final def zInterStore[DK: Schema, K: Schema](destination: DK, inputKeysNum: Long, key: K, keys: K*)(
    aggregate: Option[Aggregate] = None,
    weights: Option[::[Double]] = None
  ): IO[RedisError, Long] = {
    val command = RedisCommand(
      ZInterStore,
      Tuple5(
        ArbitraryValueInput[DK](),
        LongInput,
        NonEmptyList(ArbitraryKeyInput[K]()),
        OptionalInput(AggregateInput),
        OptionalInput(WeightsInput)
      ),
      LongOutput,
      codec,
      executor
    )
    command.run((destination, inputKeysNum, (key, keys.toList), aggregate, weights))
  }

  /**
   * Count the number of members in a sorted set between a given lexicographical range.
   *
   * @param key
   *   Key of a sorted set
   * @param lexRange
   *   LexRange that must be satisfied
   * @return
   *   The number of elements in the specified score range.
   */
  final def zLexCount[K: Schema](key: K, lexRange: LexRange): IO[RedisError, Long] = {
    val command = RedisCommand(
      ZLexCount,
      Tuple3(ArbitraryKeyInput[K](), ArbitraryValueInput[String](), ArbitraryValueInput[String]()),
      LongOutput,
      codec,
      executor
    )
    command.run((key, lexRange.min.stringify, lexRange.max.stringify))
  }

  /**
   * Remove and return members with the highest scores in a sorted set.
   *
   * @param key
   *   Key of a sorted set
   * @param count
   *   When left unspecified, the default value for count is 1. Specifying a count value that is higher than the sorted
   *   set's cardinality will not produce an error. When returning multiple elements, the one with the highest score
   *   will be the first, followed by the elements with lower scores
   * @return
   *   Chunk of popped elements and scores.
   */
  final def zPopMax[K: Schema](
    key: K,
    count: Option[Long] = None
  ): ResultBuilder1[MemberScores] =
    new ResultBuilder1[MemberScores] {
      def returning[M: Schema]: IO[RedisError, Chunk[MemberScore[M]]] = {
        val command = RedisCommand(
          ZPopMax,
          Tuple2(ArbitraryKeyInput[K](), OptionalInput(LongInput)),
          ChunkTuple2Output(ArbitraryOutput[M](), DoubleOutput)
            .map(_.map { case (m, s) => MemberScore(s, m) }),
          codec,
          executor
        )
        command.run((key, count))
      }
    }

  /**
   * Remove and return members with the lowest scores in a sorted set.
   *
   * @param key
   *   Key of a sorted set
   * @param count
   *   When left unspecified, the default value for count is 1. Specifying a count value that is higher than the sorted
   *   set's cardinality will not produce an error. When returning multiple elements, the one with the lowest score will
   *   be the first, followed by the elements with greater scores
   * @return
   *   Chunk of popped elements and scores.
   */
  final def zPopMin[K: Schema](
    key: K,
    count: Option[Long] = None
  ): ResultBuilder1[MemberScores] =
    new ResultBuilder1[MemberScores] {
      def returning[M: Schema]: IO[RedisError, Chunk[MemberScore[M]]] = {
        val command = RedisCommand(
          ZPopMin,
          Tuple2(ArbitraryKeyInput[K](), OptionalInput(LongInput)),
          ChunkTuple2Output(ArbitraryOutput[M](), DoubleOutput)
            .map(_.map { case (m, s) => MemberScore(s, m) }),
          codec,
          executor
        )
        command.run((key, count))
      }
    }

  /**
   * Return a range of members in a sorted set, by index.
   *
   * @param key
   *   Key of a sorted set
   * @param range
   *   Inclusive range
   * @return
   *   Chunk of elements in the specified range.
   */
  final def zRange[K: Schema](key: K, range: Range): ResultBuilder1[Chunk] =
    new ResultBuilder1[Chunk] {
      def returning[M: Schema]: IO[RedisError, Chunk[M]] = {
        val command = RedisCommand(
          ZRange,
          Tuple2(ArbitraryKeyInput[K](), RangeInput),
          ChunkOutput(ArbitraryOutput[M]()),
          codec,
          executor
        )
        command.run((key, range))
      }
    }

  /**
   * Return a range of members in a sorted set, by index.
   *
   * @param key
   *   Key of a sorted set
   * @param range
   *   Inclusive range
   * @return
   *   Chunk of elements with their scores in the specified range.
   */
  final def zRangeWithScores[K: Schema](
    key: K,
    range: Range
  ): ResultBuilder1[MemberScores] =
    new ResultBuilder1[MemberScores] {
      def returning[M: Schema]: IO[RedisError, Chunk[MemberScore[M]]] = {
        val command = RedisCommand(
          ZRange,
          Tuple3(ArbitraryKeyInput[K](), RangeInput, ArbitraryValueInput[String]()),
          ChunkTuple2Output(ArbitraryOutput[M](), DoubleOutput)
            .map(_.map { case (m, s) => MemberScore(s, m) }),
          codec,
          executor
        )
        command.run((key, range, WithScores.stringify))
      }
    }

  /**
   * Return a range of members in a sorted set, by lexicographical range.
   *
   * @param key
   *   Key of a sorted set
   * @param lexRange
   *   LexRange that must be satisfied
   * @param limit
   *   The optional LIMIT argument can be used to only get a range of the matching elements. A negative count returns
   *   all elements from the offset
   * @return
   *   Chunk of elements in the specified score range.
   */
  final def zRangeByLex[K: Schema](
    key: K,
    lexRange: LexRange,
    limit: Option[Limit] = None
  ): ResultBuilder1[Chunk] =
    new ResultBuilder1[Chunk] {
      def returning[M: Schema]: IO[RedisError, Chunk[M]] = {
        val command = RedisCommand(
          ZRangeByLex,
          Tuple4(
            ArbitraryKeyInput[K](),
            ArbitraryValueInput[String](),
            ArbitraryValueInput[String](),
            OptionalInput(LimitInput)
          ),
          ChunkOutput(ArbitraryOutput[M]()),
          codec,
          executor
        )
        command.run((key, lexRange.min.stringify, lexRange.max.stringify, limit))
      }
    }

  /**
   * Return a range of members in a sorted set, by score.
   *
   * @param key
   *   Key of a sorted set
   * @param scoreRange
   *   ScoreRange that must be satisfied
   * @param limit
   *   The optional LIMIT argument can be used to only get a range of the matching elements. A negative count returns
   *   all elements from the offset
   * @return
   *   Chunk of elements in the specified score range.
   */
  final def zRangeByScore[K: Schema](
    key: K,
    scoreRange: ScoreRange,
    limit: Option[Limit] = None
  ): ResultBuilder1[Chunk] =
    new ResultBuilder1[Chunk] {
      def returning[M: Schema]: IO[RedisError, Chunk[M]] = {
        val command = RedisCommand(
          ZRangeByScore,
          Tuple4(
            ArbitraryKeyInput[K](),
            ArbitraryValueInput[String](),
            ArbitraryValueInput[String](),
            OptionalInput(LimitInput)
          ),
          ChunkOutput(ArbitraryOutput[M]()),
          codec,
          executor
        )
        command.run((key, scoreRange.min.stringify, scoreRange.max.stringify, limit))
      }
    }

  /**
   * Return a range of members in a sorted set, by score.
   *
   * @param key
   *   Key of a sorted set
   * @param scoreRange
   *   ScoreRange that must be satisfied
   * @param limit
   *   The optional LIMIT argument can be used to only get a range of the matching elements. A negative count returns
   *   all elements from the offset
   * @return
   *   Chunk of elements with their scores in the specified score range.
   */
  final def zRangeByScoreWithScores[K: Schema](
    key: K,
    scoreRange: ScoreRange,
    limit: Option[Limit] = None
  ): ResultBuilder1[MemberScores] =
    new ResultBuilder1[MemberScores] {
      def returning[M: Schema]: IO[RedisError, Chunk[MemberScore[M]]] = {
        val command = RedisCommand(
          ZRangeByScore,
          Tuple5(
            ArbitraryKeyInput[K](),
            ArbitraryValueInput[String](),
            ArbitraryValueInput[String](),
            ArbitraryValueInput[String](),
            OptionalInput(LimitInput)
          ),
          ChunkTuple2Output(ArbitraryOutput[M](), DoubleOutput)
            .map(_.map { case (m, s) => MemberScore(s, m) }),
          codec,
          executor
        )
        command.run((key, scoreRange.min.stringify, scoreRange.max.stringify, WithScores.stringify, limit))
      }
    }

  /**
   * Determine the index of a member in a sorted set.
   *
   * @param key
   *   Key of a sorted set
   * @param member
   *   Member of sorted set
   * @return
   *   The rank of member in the sorted set stored at key, with the scores ordered from low to high.
   */
  final def zRank[K: Schema, M: Schema](key: K, member: M): IO[RedisError, Option[Long]] = {
    val command =
      RedisCommand(
        ZRank,
        Tuple2(ArbitraryKeyInput[K](), ArbitraryValueInput[M]()),
        OptionalOutput(LongOutput),
        codec,
        executor
      )
    command.run((key, member))
  }

  /**
   * Remove one or more members from a sorted set.
   *
   * @param key
   *   Key of a sorted set
   * @param firstMember
   *   Member to be removed
   * @param restMembers
   *   Rest members to be removed
   * @return
   *   The number of members removed from the sorted set, not including non existing members.
   */
  final def zRem[K: Schema, M: Schema](
    key: K,
    firstMember: M,
    restMembers: M*
  ): IO[RedisError, Long] = {
    val command =
      RedisCommand(
        ZRem,
        Tuple2(ArbitraryKeyInput[K](), NonEmptyList(ArbitraryValueInput[M]())),
        LongOutput,
        codec,
        executor
      )
    command.run((key, (firstMember, restMembers.toList)))
  }

  /**
   * Remove all members in a sorted set between the given lexicographical range.
   *
   * @param key
   *   Key of a sorted set
   * @param lexRange
   *   LexRange that must be satisfied
   * @return
   *   The number of elements removed.
   */
  final def zRemRangeByLex[K: Schema](key: K, lexRange: LexRange): IO[RedisError, Long] = {
    val command = RedisCommand(
      ZRemRangeByLex,
      Tuple3(ArbitraryKeyInput[K](), ArbitraryValueInput[String](), ArbitraryValueInput[String]()),
      LongOutput,
      codec,
      executor
    )
    command.run((key, lexRange.min.stringify, lexRange.max.stringify))
  }

  /**
   * Remove all members in a sorted set within the given indexes.
   *
   * @param key
   *   Key of a sorted set
   * @param range
   *   Range that must be satisfied
   * @return
   *   The number of elements removed.
   */
  final def zRemRangeByRank[K: Schema](key: K, range: Range): IO[RedisError, Long] = {
    val command = RedisCommand(ZRemRangeByRank, Tuple2(ArbitraryKeyInput[K](), RangeInput), LongOutput, codec, executor)
    command.run((key, range))
  }

  /**
   * Remove all members in a sorted set within the given scores.
   *
   * @param key
   *   Key of a sorted set
   * @param scoreRange
   *   ScoreRange that must be satisfied
   * @return
   *   The number of elements removed.
   */
  final def zRemRangeByScore[K: Schema](key: K, scoreRange: ScoreRange): IO[RedisError, Long] = {
    val command = RedisCommand(
      ZRemRangeByScore,
      Tuple3(ArbitraryKeyInput[K](), ArbitraryValueInput[String](), ArbitraryValueInput[String]()),
      LongOutput,
      codec,
      executor
    )
    command.run((key, scoreRange.min.stringify, scoreRange.max.stringify))
  }

  /**
   * Return a range of members in a sorted set, by index, with scores ordered from high to low.
   *
   * @param key
   *   Key of a sorted set
   * @param range
   *   Range that must be satisfied
   * @return
   *   Chunk of elements in the specified range.
   */
  final def zRevRange[K: Schema](key: K, range: Range): ResultBuilder1[Chunk] =
    new ResultBuilder1[Chunk] {
      def returning[M: Schema]: IO[RedisError, Chunk[M]] = {
        val command = RedisCommand(
          ZRevRange,
          Tuple2(ArbitraryKeyInput[K](), RangeInput),
          ChunkOutput(ArbitraryOutput[M]()),
          codec,
          executor
        )
        command.run((key, range))
      }
    }

  /**
   * Return a range of members in a sorted set, by index, with scores ordered from high to low.
   *
   * @param key
   *   Key of a sorted set
   * @param range
   *   Range that must be satisfied
   * @return
   *   Chunk of elements with their scores in the specified range.
   */
  final def zRevRangeWithScores[K: Schema](
    key: K,
    range: Range
  ): ResultBuilder1[MemberScores] =
    new ResultBuilder1[MemberScores] {
      def returning[M: Schema]: IO[RedisError, Chunk[MemberScore[M]]] = {
        val command = RedisCommand(
          ZRevRange,
          Tuple3(ArbitraryKeyInput[K](), RangeInput, ArbitraryValueInput[String]()),
          ChunkTuple2Output(ArbitraryOutput[M](), DoubleOutput)
            .map(_.map { case (m, s) => MemberScore(s, m) }),
          codec,
          executor
        )
        command.run((key, range, WithScores.stringify))
      }
    }

  /**
   * Return a range of members in a sorted set, by lexicographical range, ordered from higher to lower strings.
   *
   * @param key
   *   Key of a sorted set
   * @param lexRange
   *   LexRange that must be satisfied
   * @param limit
   *   The optional LIMIT argument can be used to only get a range of the matching elements. A negative count returns
   *   all elements from the offset
   * @return
   *   Chunk of elements in the specified score range.
   */
  final def zRevRangeByLex[K: Schema](
    key: K,
    lexRange: LexRange,
    limit: Option[Limit] = None
  ): ResultBuilder1[Chunk] =
    new ResultBuilder1[Chunk] {
      def returning[M: Schema]: IO[RedisError, Chunk[M]] = {
        val command = RedisCommand(
          ZRevRangeByLex,
          Tuple4(
            ArbitraryKeyInput[K](),
            ArbitraryValueInput[String](),
            ArbitraryValueInput[String](),
            OptionalInput(LimitInput)
          ),
          ChunkOutput(ArbitraryOutput[M]()),
          codec,
          executor
        )
        command.run((key, lexRange.max.stringify, lexRange.min.stringify, limit))
      }
    }

  /**
   * Return a range of members in a sorted set, by score, with scores ordered from high to low.
   *
   * @param key
   *   Key of a sorted set
   * @param scoreRange
   *   ScoreRange that must be satisfied
   * @param limit
   *   The optional LIMIT argument can be used to only get a range of the matching elements. A negative count returns
   *   all elements from the offset
   * @return
   *   Chunk of elements in the specified range.
   */
  final def zRevRangeByScore[K: Schema](
    key: K,
    scoreRange: ScoreRange,
    limit: Option[Limit] = None
  ): ResultBuilder1[Chunk] =
    new ResultBuilder1[Chunk] {
      def returning[M: Schema]: IO[RedisError, Chunk[M]] = {
        val command = RedisCommand(
          ZRevRangeByScore,
          Tuple4(
            ArbitraryKeyInput[K](),
            ArbitraryValueInput[String](),
            ArbitraryValueInput[String](),
            OptionalInput(LimitInput)
          ),
          ChunkOutput(ArbitraryOutput[M]()),
          codec,
          executor
        )
        command.run((key, scoreRange.max.stringify, scoreRange.min.stringify, limit))
      }
    }

  /**
   * Return a range of members in a sorted set, by score, with scores ordered from high to low.
   *
   * @param key
   *   Key of a sorted set
   * @param scoreRange
   *   ScoreRange that must be satisfied
   * @param limit
   *   The optional LIMIT argument can be used to only get a range of the matching elements. A negative count returns
   *   all elements from the offset
   * @return
   *   Chunk of elements with their scores in the specified range.
   */
  final def zRevRangeByScoreWithScores[K: Schema](
    key: K,
    scoreRange: ScoreRange,
    limit: Option[Limit] = None
  ): ResultBuilder1[MemberScores] =
    new ResultBuilder1[MemberScores] {
      def returning[M: Schema]: IO[RedisError, Chunk[MemberScore[M]]] = {
        val command = RedisCommand(
          ZRevRangeByScore,
          Tuple5(
            ArbitraryKeyInput[K](),
            ArbitraryValueInput[String](),
            ArbitraryValueInput[String](),
            ArbitraryValueInput[String](),
            OptionalInput(LimitInput)
          ),
          ChunkTuple2Output(ArbitraryOutput[M](), DoubleOutput)
            .map(_.map { case (m, s) => MemberScore(s, m) }),
          codec,
          executor
        )
        command.run((key, scoreRange.max.stringify, scoreRange.min.stringify, WithScores.stringify, limit))
      }
    }

  /**
   * Determine the index of a member in a sorted set, with scores ordered from high to low.
   *
   * @param key
   *   Key of a sorted set
   * @param member
   *   Member of sorted set
   * @return
   *   The rank of member.
   */
  final def zRevRank[K: Schema, M: Schema](key: K, member: M): IO[RedisError, Option[Long]] = {
    val command = RedisCommand(
      ZRevRank,
      Tuple2(ArbitraryKeyInput[K](), ArbitraryValueInput[M]()),
      OptionalOutput(LongOutput),
      codec,
      executor
    )
    command.run((key, member))
  }

  /**
   * Incrementally iterate sorted sets elements and associated scores.
   *
   * @param key
   *   Key of the set to scan
   * @param cursor
   *   Cursor to use for this iteration of scan
   * @param pattern
   *   Glob-style pattern that filters which elements are returned
   * @param count
   *   Count of elements. Roughly this number will be returned by Redis if possible
   * @return
   *   Returns the items for this iteration or nothing when you reach the end.
   */
  final def zScan[K: Schema](
    key: K,
    cursor: Long,
    pattern: Option[String] = None,
    count: Option[Count] = None
  ): ResultBuilder1[({ type lambda[x] = (Long, MemberScores[x]) })#lambda] =
    new ResultBuilder1[({ type lambda[x] = (Long, MemberScores[x]) })#lambda] {
      def returning[M: Schema]: IO[RedisError, (Long, Chunk[MemberScore[M]])] = {
        val memberScoresOutput =
          ChunkTuple2Output(ArbitraryOutput[M](), DoubleOutput).map(_.map { case (m, s) => MemberScore(s, m) })
        val command = RedisCommand(
          ZScan,
          Tuple4(ArbitraryKeyInput[K](), LongInput, OptionalInput(PatternInput), OptionalInput(CountInput)),
          Tuple2Output(MultiStringOutput.map(_.toLong), memberScoresOutput),
          codec,
          executor
        )
        command.run((key, cursor, pattern.map(Pattern(_)), count))
      }
    }

  /**
   * Get the score associated with the given member in a sorted set.
   *
   * @param key
   *   Key of a sorted set
   * @param member
   *   Member of sorted set
   * @return
   *   The score of member (a double precision floating point number.
   */
  final def zScore[K: Schema, M: Schema](key: K, member: M): IO[RedisError, Option[Double]] = {
    val command = RedisCommand(
      ZScore,
      Tuple2(ArbitraryKeyInput[K](), ArbitraryValueInput[M]()),
      OptionalOutput(DoubleOutput),
      codec,
      executor
    )
    command.run((key, member))
  }

  /**
   * Add multiple sorted sets and return each member.
   *
   * @param inputKeysNum
   *   Number of input keys
   * @param key
   *   Key of a sorted set
   * @param keys
   *   Keys of other sorted sets
   * @param weights
   *   Represents WEIGHTS option, it is possible to specify a multiplication factor for each input sorted set. This
   *   means that the score of every element in every input sorted set is multiplied by this factor before being passed
   *   to the aggregation function. When WEIGHTS is not given, the multiplication factors default to 1
   * @param aggregate
   *   With the AGGREGATE option, it is possible to specify how the results of the union are aggregated
   * @return
   *   Chunk of all members in each sorted set.
   */
  final def zUnion[K: Schema](inputKeysNum: Long, key: K, keys: K*)(
    weights: Option[::[Double]] = None,
    aggregate: Option[Aggregate] = None
  ): ResultBuilder1[Chunk] =
    new ResultBuilder1[Chunk] {
      def returning[M: Schema]: IO[RedisError, Chunk[M]] = {
        val command =
          RedisCommand(
            ZUnion,
            Tuple4(
              LongInput,
              NonEmptyList(ArbitraryKeyInput[K]()),
              OptionalInput(WeightsInput),
              OptionalInput(AggregateInput)
            ),
            ChunkOutput(ArbitraryOutput[M]()),
            codec,
            executor
          )
        command.run((inputKeysNum, (key, keys.toList), weights, aggregate))
      }
    }

  /**
   * Add multiple sorted sets and return each member and associated score.
   *
   * @param inputKeysNum
   *   Number of input keys
   * @param key
   *   Key of a sorted set
   * @param keys
   *   Keys of other sorted sets
   * @param weights
   *   Represents WEIGHTS option, it is possible to specify a multiplication factor for each input sorted set. This
   *   means that the score of every element in every input sorted set is multiplied by this factor before being passed
   *   to the aggregation function. When WEIGHTS is not given, the multiplication factors default to 1
   * @param aggregate
   *   With the AGGREGATE option, it is possible to specify how the results of the union are aggregated
   * @return
   *   Chunk of all members with their scores in each sorted set.
   */
  final def zUnionWithScores[K: Schema](inputKeysNum: Long, key: K, keys: K*)(
    weights: Option[::[Double]] = None,
    aggregate: Option[Aggregate] = None
  ): ResultBuilder1[MemberScores] =
    new ResultBuilder1[MemberScores] {
      def returning[M: Schema]: IO[RedisError, Chunk[MemberScore[M]]] = {
        val command =
          RedisCommand(
            ZUnion,
            Tuple5(
              LongInput,
              NonEmptyList(ArbitraryKeyInput[K]()),
              OptionalInput(WeightsInput),
              OptionalInput(AggregateInput),
              ArbitraryValueInput[String]()
            ),
            ChunkTuple2Output(ArbitraryOutput[M](), DoubleOutput)
              .map(_.map { case (m, s) => MemberScore(s, m) }),
            codec,
            executor
          )
        command.run((inputKeysNum, (key, keys.toList), weights, aggregate, WithScores.stringify))
      }
    }

  /**
   * Add multiple sorted sets and store the resulting sorted set in a new key.
   *
   * @param destination
   *   Key of the output
   * @param inputKeysNum
   *   Number of input keys
   * @param key
   *   Key of a sorted set
   * @param keys
   *   Keys of other sorted sets
   * @param weights
   *   Represents WEIGHTS option, it is possible to specify a multiplication factor for each input sorted set. This
   *   means that the score of every element in every input sorted set is multiplied by this factor before being passed
   *   to the aggregation function. When WEIGHTS is not given, the multiplication factors default to 1
   * @param aggregate
   *   With the AGGREGATE option, it is possible to specify how the results of the union are aggregated
   * @return
   *   The number of elements in the resulting sorted set at destination.
   */
  final def zUnionStore[DK: Schema, K: Schema](destination: DK, inputKeysNum: Long, key: K, keys: K*)(
    weights: Option[::[Double]] = None,
    aggregate: Option[Aggregate] = None
  ): IO[RedisError, Long] = {
    val command = RedisCommand(
      ZUnionStore,
      Tuple5(
        ArbitraryValueInput[DK](),
        LongInput,
        NonEmptyList(ArbitraryKeyInput[K]()),
        OptionalInput(WeightsInput),
        OptionalInput(AggregateInput)
      ),
      LongOutput,
      codec,
      executor
    )
    command.run((destination, inputKeysNum, (key, keys.toList), weights, aggregate))
  }

  /**
   * Returns the scores associated with the specified members in the sorted set stored at key.
   *
   * @param key
   *   Key of the set
   * @param keys
   *   Keys of the rest sets
   * @return
   *   List of scores or None associated with the specified member values (a double precision floating point number).
   */
  final def zMScore[K: Schema](key: K, keys: K*): IO[RedisError, Chunk[Option[Double]]] = {
    val command = RedisCommand(
      ZMScore,
      NonEmptyList(ArbitraryKeyInput[K]()),
      ChunkOutput(OptionalOutput(DoubleOutput)),
      codec,
      executor
    )
    command.run((key, keys.toList))
  }

  /**
   * Return a random element from the sorted set value stored at key.
   *
   * @param key
   *   Key of a sorted set
   * @return
   *   Return a random element from the sorted set value stored at key.
   */
  final def zRandMember[K: Schema](key: K): ResultBuilder1[Option] =
    new ResultBuilder1[Option] {
      def returning[R: Schema]: IO[RedisError, Option[R]] =
        RedisCommand(ZRandMember, ArbitraryKeyInput[K](), OptionalOutput(ArbitraryOutput[R]()), codec, executor)
          .run(key)
    }

  /**
   * Return random elements from the sorted set value stored at key.
   *
   * @param key
   *   Key of a sorted set
   * @param count
   *   If the provided count argument is positive, return an array of distinct elements. The array's length is either
   *   count or the sorted set's cardinality (ZCARD), whichever is lower
   * @return
   *   Return an array of elements from the sorted set value stored at key.
   */
  final def zRandMember[K: Schema](
    key: K,
    count: Long
  ): ResultBuilder1[Chunk] =
    new ResultBuilder1[Chunk] {
      def returning[M: Schema]: IO[RedisError, Chunk[M]] = {
        val command = RedisCommand(
          ZRandMember,
          Tuple2(ArbitraryKeyInput[K](), LongInput),
          ZRandMemberOutput(ArbitraryOutput[M]()),
          codec,
          executor
        )
        command.run((key, count))
      }
    }

  /**
   * Return random elements from the sorted set value stored at key.
   *
   * @param key
   *   Key of a sorted set
   * @param count
   *   If the provided count argument is positive, return an array of distinct elements. The array's length is either
   *   count or the sorted set's cardinality (ZCARD), whichever is lower
   * @return
   *   When the additional count argument is passed, the command returns an array of elements, or an empty array when
   *   key does not exist. If the WITHSCORES modifier is used, the reply is a list elements and their scores from the
   *   sorted set.
   */
  final def zRandMemberWithScores[K: Schema](
    key: K,
    count: Long
  ): ResultBuilder1[MemberScores] =
    new ResultBuilder1[MemberScores] {
      def returning[M: Schema]: IO[RedisError, Chunk[MemberScore[M]]] = {
        val command = RedisCommand(
          ZRandMember,
          Tuple3(ArbitraryKeyInput[K](), LongInput, ArbitraryValueInput[String]()),
          ZRandMemberTuple2Output(ArbitraryOutput[M](), DoubleOutput)
            .map(_.map { case (m, s) => MemberScore(s, m) }),
          codec,
          executor
        )

        command.run((key, count, WithScores.stringify))
      }
    }
}

private[redis] object SortedSets {
  final val BzPopMax         = "BZPOPMAX"
  final val BzPopMin         = "BZPOPMIN"
  final val ZAdd             = "ZADD"
  final val ZCard            = "ZCARD"
  final val ZCount           = "ZCOUNT"
  final val ZDiff            = "ZDIFF"
  final val ZDiffStore       = "ZDIFFSTORE"
  final val ZIncrBy          = "ZINCRBY"
  final val ZInter           = "ZINTER"
  final val ZInterStore      = "ZINTERSTORE"
  final val ZLexCount        = "ZLEXCOUNT"
  final val ZMScore          = "ZMSCORE"
  final val ZPopMax          = "ZPOPMAX"
  final val ZPopMin          = "ZPOPMIN"
  final val ZRange           = "ZRANGE"
  final val ZRangeByLex      = "ZRANGEBYLEX"
  final val ZRangeByScore    = "ZRANGEBYSCORE"
  final val ZRank            = "ZRANK"
  final val ZRem             = "ZREM"
  final val ZRemRangeByLex   = "ZREMRANGEBYLEX"
  final val ZRemRangeByRank  = "ZREMRANGEBYRANK"
  final val ZRemRangeByScore = "ZREMRANGEBYSCORE"
  final val ZRevRange        = "ZREVRANGE"
  final val ZRevRangeByLex   = "ZREVRANGEBYLEX"
  final val ZRevRangeByScore = "ZREVRANGEBYSCORE"
  final val ZRevRank         = "ZREVRANK"
  final val ZScan            = "ZSCAN"
  final val ZScore           = "ZSCORE"
  final val ZUnion           = "ZUNION"
  final val ZUnionStore      = "ZUNIONSTORE"
  final val ZRandMember      = "ZRANDMEMBER"
}
