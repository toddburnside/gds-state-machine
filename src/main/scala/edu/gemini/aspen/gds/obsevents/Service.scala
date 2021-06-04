package edu.gemini.aspen.gds.obsevents

import cats.effect._
import cats.effect.std.QueueSink
import edu.gemini.aspen.gds.obsstates.ObservationStateEvent
import org.http4s._
import org.http4s.dsl.io._

object Service {
  import ObservationEvent._

  def apply(obsStateQ: QueueSink[IO, ObservationStateEvent]) = HttpRoutes.of[IO] {
    case POST -> Root / "obs-prep" / dataLabel           =>
      for {
        _  <- obsStateQ.offer(ObservationStateEvent.AddObservationEvent(dataLabel, OBS_PREP))
        ok <- Ok("Success")
      } yield ok
    case POST -> Root / "obs-end-acq" / dataLabel        =>
      for {
        _  <- obsStateQ.offer(ObservationStateEvent.AddObservationEvent(dataLabel, OBS_END_ACQ))
        ok <- Ok("Success")
      } yield ok
    case POST -> Root / "obs-end-dset-write" / dataLabel =>
      for {
        _  <-
          obsStateQ.offer(ObservationStateEvent.AddObservationEvent(dataLabel, OBS_END_DSET_WRITE))
        ok <- Ok("Success")
      } yield ok
  }
}
