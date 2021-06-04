package edu.gemini.aspen.gds.keywords

import cats._
import cats.effect.kernel._
import cats.syntax.all._
import edu.gemini.aspen.gds.obsevents.ObservationEvent
import io.chrisdavenport.mapref.MapRef
import io.chrisdavenport.mapref.implicits._
import org.typelevel.log4cats.slf4j.Slf4jLogger
import scala.concurrent.duration._

sealed trait KeywordManager[F[_]] {
  def initialize(dataLabel: String): F[Unit]
  def get(dataLabel:        String): F[List[CollectedKeyword]]
  def add(dataLabel:        String, keyword: CollectedKeyword): F[Unit]
  // may also need the program id for some keywords.
  def collect(dataLabel:    String, event:   ObservationEvent): F[Unit]
  def delete(dataLabel:     String): F[Unit]
}

object KeywordManager {
  def apply[F[_]: Async: Parallel](
    // def apply[F[_]](
    collectors: KeywordCollector[F]*
    // )(implicit F: Async[F], ev: Parallel[F]): F[KeywordManager[F]] =
  ): F[KeywordManager[F]] =
    (MapRef.ofConcurrentHashMap[F, String, KeywordItem](), Slf4jLogger.create[F]).mapN {
      (mapref, logger) =>
        new KeywordManager[F] {

          def initialize(dataLabel: String): F[Unit] =
            // check to make sure it doesn't already exist?
            logger.info(s"Initializing keyword manager for observation $dataLabel") >>
              mapref.setKeyValue(dataLabel, KeywordItem(0, List.empty))

          // needs to wait for count to go to zero
          // Make attempts and sleeps configurable?
          def get(dataLabel: String): F[List[CollectedKeyword]] = tryGet(dataLabel, 3)

          def add(dataLabel: String, keyword: CollectedKeyword): F[Unit] =
            mapref
              .modifyKeyValueIfSet(dataLabel,
                                   item => item.copy(keywords = keyword :: item.keywords) -> true
              )
              .flatMap {
                case None    =>
                  logger.warn(s"Received, too late, keyword $keyword for observation $dataLabel")
                case Some(_) => logger.info(s"Added keyword $keyword to observation $dataLabel")
              }

          def collect(dataLabel: String, event: ObservationEvent): F[Unit] = {
            val count = makeCounter(dataLabel)
            val adder = adderFunc(dataLabel)
            logger.info(s"Collecting keywords for observation $dataLabel, event $event") >>
              collectors.parTraverse(_.collect(event, count, adder)).void
          }

          def delete(dataLabel: String): F[Unit] = logger.info(
            s"Removing observation $dataLabel"
          ) >> mapref.unsetKey(dataLabel)

          private def updateCount(dataLabel: String, incr: Int): F[Unit] =
            mapref.updateKeyValueIfSet(dataLabel,
                                       item => item.copy(waitingCount = item.waitingCount + incr)
            )

          private def makeCounter(dataLabel: String): Count[F] = new Count[F] {
            def incr: F[Unit] = updateCount(dataLabel, 1)
            def decr: F[Unit] = updateCount(dataLabel, -1)
          }

          private def adderFunc(dataLabel: String): CollectedKeyword => F[Unit] =
            ckw => add(dataLabel, ckw)

          private def tryGet(dataLabel: String, remaining: Int): F[List[CollectedKeyword]] =
            mapref(dataLabel).get.flatMap {
              case None                                             =>
                logger.error(
                  s"Observation $dataLabel not found - no collected keywords are available"
                ) >> List.empty.pure[F]
              case Some(ki) if ki.waitingCount > 0 && remaining > 0 =>
                logger.info(
                  s"Waiting for ${ki.waitingCount} keywords for observation $dataLabel, $remaining attempts left"
                ) >> Async[F].sleep(5.seconds) >> tryGet(dataLabel, remaining - 1)
              case Some(ki) if ki.waitingCount > 0                  =>
                logger.warn(
                  s"Waiting for ${ki.waitingCount} keywords for observation $dataLabel, no attempts left. Returning what we have"
                ) >> ki.keywords.pure[F]
              case Some(ki)                                         =>
                logger.info(s"Returning keywords for observation $dataLabel") >> ki.keywords.pure[F]
            }
        }
    }

  final case class KeywordItem(waitingCount: Int, keywords: List[CollectedKeyword])
}
