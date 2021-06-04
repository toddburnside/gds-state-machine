package edu.gemini.aspen.gds

import cats.effect._
import cats.effect.std.QueueSink
import cats.syntax.all._
import edu.gemini.aspen.gds.obsstates.ObservationStateEvent
import edu.gemini.aspen.gds.seqexec
import fs2.Stream
import org.http4s.implicits._
import org.http4s.server.blaze._
import org.http4s.server.middleware.RequestLogger
import org.http4s.server.Router

object Server {

  def apply(
    obsStateQ: QueueSink[IO, ObservationStateEvent]
  ): Stream[IO, Unit] = {
    val httpApp = RequestLogger
      .httpApp(false, false)(
        Router(
          "gds-seqexec" -> seqexec.Service(obsStateQ),
          "obs-events"  -> obsevents.Service(obsStateQ)
        ).orNotFound
      )

    BlazeServerBuilder[IO](scala.concurrent.ExecutionContext.Implicits.global)
      .bindHttp(8088, "localhost")
      .withHttpApp(httpApp)
      .serve
      .void
  }
}
