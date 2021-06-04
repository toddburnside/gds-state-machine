package edu.gemini.aspen.gds.obsstates

import cats.effect.{ Async, Clock }
import cats.effect.std.QueueSink
import cats.syntax.all._
import edu.gemini.aspen.gds.keywords.KeywordManager
import edu.gemini.aspen.gds.obsstates.ObservationStateEvent._
import io.chrisdavenport.mapref.MapRef
import io.chrisdavenport.mapref.implicits._
import org.typelevel.log4cats.slf4j.Slf4jLogger
import scala.concurrent.duration._

trait ObservationManager[F[_]] {
  def process(stateEvent: ObservationStateEvent): F[Unit]
}

object ObservationManager {
  // make this configurable?
  val lifetime = 20.minutes

  def apply[F[_]: Async](
    keywordManager: KeywordManager[F],
    obsStateQ:      QueueSink[F, ObservationStateEvent]
  ): F[ObservationManager[F]] =
    (MapRef.ofConcurrentHashMap[F, String, ObservationItem[F]](), Slf4jLogger.create[F]).mapN {
      (mapref, logger) =>
        new ObservationManager[F] {
          def process(stateEvent: ObservationStateEvent): F[Unit] = stateEvent match {
            case Start(dataLabel, programId) =>
              for {
                _   <- logger.info(s"Starting observation $dataLabel")
                now <- Clock[F].realTime
                fsm <-
                  ObservationFSM(dataLabel, obsStateQ)
                // check to be sure it doesn't already exist? Or, should we just start over, anyway. Maybe a warning, at least.
                _   <- mapref.setKeyValue(dataLabel, ObservationItem(programId, fsm, now + lifetime))
                _   <- keywordManager.initialize(dataLabel)
              } yield ()

            case e @ Stop(dataLabel) => withObsItem(dataLabel, e)(_.fsm.stopObservation)

            case Abort(dataLabel) =>
              logger.warn(s"Received Abort event for observation $dataLabel") >>
                obsStateQ.offer(Delete(dataLabel))

            case Delete(dataLabel) =>
              logger.info(s"Removing observation $dataLabel") >> keywordManager.delete(
                dataLabel
              ) >> mapref.unsetKey(dataLabel)

            case e @ AddObservationEvent(dataLabel, obsEvent) =>
              withObsItem(dataLabel, e)(_.fsm.addObservationEvent(obsEvent))

            case e @ AddKeyword(dataLabel, keyword) =>
              withObsItem(dataLabel, e)(_ => keywordManager.add(dataLabel, keyword))

            case e @ CollectKeywords(dataLabel, obsEvent) =>
              withObsItem(dataLabel, e)(_ => keywordManager.collect(dataLabel, obsEvent))

            case e @ Step(dataLabel) => withObsItem(dataLabel, e)(_.fsm.step)

            case e @ Complete(dataLabel) =>
              withObsItem(dataLabel, e) { _ =>
                for {
                  kws <- keywordManager.get(dataLabel)
                  _   <- logger.info(s"Got these keywords for $dataLabel: ${kws.mkString(", ")}")
                  _   <- logger.info(
                           "Here is where we would deal with the keywords and transfer the fits file."
                         )
                  _   <- obsStateQ.offer(Delete(dataLabel))
                } yield ()
              }

            case PurgeStale =>
              for {
                _    <- logger.info("Checking for expired observations.")
                keys <- mapref.keys
                now  <- Clock[F].realTime
                _    <- keys.traverse(purgeIfNeeded(_, now))
              } yield ()
          }

          def withObsItem(dataLabel: String, event: ObservationStateEvent)(
            action:                  ObservationItem[F] => F[Unit]
          ): F[Unit] =
            mapref(dataLabel).get.flatMap {
              case None       => logger.error(s"Observation not found for event: $event")
              case Some(item) => action(item)
            }

          def purgeIfNeeded(dataLabel: String, now: FiniteDuration): F[Unit] =
            mapref(dataLabel).get.flatMap {
              case Some(item) if item.expiration < now =>
                logger.info(s"Purging observation $dataLabel due to expiration") >> obsStateQ.offer(
                  Delete(dataLabel)
                )
              case _                                   => Async[F].unit
            }
        }
    }

  // need to add an expiration
  final case class ObservationItem[F[_]](
    programId:  String,
    fsm:        ObservationFSM[F],
    expiration: FiniteDuration
  )
}
