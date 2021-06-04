package edu.gemini.aspen.gds.obsstates

import cats.effect._
import cats.effect.std.QueueSink
import cats.effect.syntax.all._
import cats.syntax.all._
import edu.gemini.aspen.gds.obsevents.ObservationEvent
import org.typelevel.log4cats.slf4j.Slf4jLogger
import scala.concurrent.duration._

trait ObservationFSM[F[_]] {
  def addObservationEvent(obsEvent: ObservationEvent): F[Unit]
  def stopObservation: F[Unit]
  def step: F[Unit]
}

object ObservationFSM {
  // make these configurable?
  val numAttempts = 3
  val sleepTime   = 10.seconds

  def apply[F[_]: Async](
    dataLabel: String,
    obsStateQ: QueueSink[F, ObservationStateEvent]
  ): F[ObservationFSM[F]] =
    (Ref.of[F, State](Running(Set.empty)), Slf4jLogger.create[F]).mapN { (state, logger) =>
      new ObservationFSM[F] {

        def addObservationEvent(obsEvent: ObservationEvent): F[Unit] = state.modify {
          case st @ Running(events) =>
            if (events.contains(obsEvent))
              st -> logger.warn(
                s"Duplicate event $obsEvent received for running observation $dataLabel"
              )
            else if (obsEvent == ObservationEvent.OBS_END_DSET_WRITE)
              WaitingForEvents(events + obsEvent, numAttempts) -> (logger.info(
                s"Received $obsEvent for observation $dataLabel"
              ) >> qKeywordCollection(obsEvent) >> qStep)
            else
              Running(events + obsEvent)                       -> (logger.info(
                s"Added $obsEvent to running observation $dataLabel"
              ) >> qKeywordCollection(obsEvent))

          case st @ WaitingForEvents(events, _) =>
            if (events.contains(obsEvent))
              st                                               -> logger.warn(
                s"Duplicate event $obsEvent received for observation $dataLabel, which was waiting for other events to complete."
              )
            else
              WaitingForEvents(events + obsEvent, numAttempts) -> (logger.info(
                s"Received $obsEvent for observation $dataLabel, which was waiting for events to complete."
              ) >> qKeywordCollection(obsEvent) >> qStep)

          case st @ _ =>
            st -> logger.info(
              s"Received $obsEvent for observation $dataLabel, which no longer needs events."
            )
        }.flatten

        def stopObservation: F[Unit] = state.modify {
          case Running(events) =>
            WaitingForEvents(events, numAttempts) -> (logger.info(
              s"Observation $dataLabel stopped by Seqexeq"
            ) >> qStep)
          case st @ _          => st -> Sync[F].unit
        }.flatten

        def step: F[Unit] = state.modify {
          case st @ WaitingForEvents(_, _) => st -> waitForEvents
          case st @ _                      => st -> Sync[F].unit
        }.flatten

        def waitForEvents: F[Unit] = state.modify {
          case WaitingForEvents(events, remaining) =>
            val required = ObservationEvent.all.diff(events)
            if (required.isEmpty)
              Completed -> (logger.info(
                s"All events have arrived for observation $dataLabel"
              ) >> qComplete)
            else if (remaining > 0)
              WaitingForEvents(events, remaining - 1) -> (logger.warn(
                s"Observation $dataLabel waiting for keywords: ${required.mkString(",")}. $remaining attempts left"
              ) >> sleepAndRecheck)
            else
              Completed                               ->
                (logger.warn(
                  s"Observation $dataLabel missing these events: ${required.mkString(",")}. Finishing without them."
                ) >> qComplete)
          case st @ _                              => st -> Sync[F].unit
        }.flatten

        def qKeywordCollection(obsEvent: ObservationEvent): F[Unit] =
          obsStateQ.offer(ObservationStateEvent.CollectKeywords(dataLabel, obsEvent))

        def qStep: F[Unit]     = obsStateQ.offer(ObservationStateEvent.Step(dataLabel))
        def qComplete: F[Unit] = obsStateQ.offer(ObservationStateEvent.Complete(dataLabel))

        // If we receive another event in the meantime, this will still fire. But, it doesn't really matter.
        // Receiving an event resets the attempts, so if we're still waiting, we just might have an extra check
        // to see if we've received them all. And, if we have, the state will be Completed and the check will
        // be ignored. The extra checks will litter the logs, but this should only happen if we are missing multiple
        // events - which should be rare.
        def sleepAndRecheck: F[Unit] = (Async[F].sleep(sleepTime) >> waitForEvents).start.void
      }
    }

  sealed trait State
  case class Running(events: Set[ObservationEvent]) extends State
  case class WaitingForEvents(events: Set[ObservationEvent], remaining: Int) extends State
  case object Completed extends State
}
