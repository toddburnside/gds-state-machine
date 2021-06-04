package edu.gemini.aspen.gds

import cats.syntax.all._
import cats.effect._
import cats.effect.std._
import edu.gemini.aspen.gds.config._
import edu.gemini.aspen.gds.keywords._
import edu.gemini.aspen.gds.obsstates._
import fs2.Stream
import scala.concurrent.duration._
import edu.gemini.aspen.gds.obsevents.ObservationEvent

object Main extends IOApp.Simple {
  val purgeRate = 10.minutes
  val config    = Configuration(
    List(
      ConfigurationItem(ObservationEvent.OBS_PREP, "Prep1", SubSystem.Epics),
      ConfigurationItem(ObservationEvent.OBS_PREP, "Prep2", SubSystem.Epics),
      ConfigurationItem(ObservationEvent.OBS_END_ACQ, "Acq1", SubSystem.Epics),
      ConfigurationItem(ObservationEvent.OBS_END_DSET_WRITE, "Write1", SubSystem.Epics),
      ConfigurationItem(ObservationEvent.OBS_PREP, "OdbPrep", SubSystem.Odb)
    )
  )

  def run: IO[Unit] = Random.scalaUtilRandom[IO].flatMap { implicit random =>
    for {
      obsStateQ <- Queue.unbounded[IO, obsstates.ObservationStateEvent]
      server     = Server(obsStateQ).compile.drain
      col1      <- FauxCollector[IO](SubSystem.Epics, config)
      col2      <- FauxCollector[IO](SubSystem.Odb, config)
      kwMgr     <- KeywordManager[IO](col1, col2)
      obsMgr    <- ObservationManager(kwMgr, obsStateQ)
      obsPurge   = Stream
                     .fixedDelay[IO](purgeRate)
                     .foreach(_ => obsStateQ.offer(ObservationStateEvent.PurgeStale))
                     .compile
                     .drain
      obsProcess = Stream
                     .fromQueueUnterminated(obsStateQ)
                     .foreach(obsMgr.process)
                     .compile
                     .drain
      app       <- (server, obsPurge, obsProcess).parMapN((_, _, _) => ())
    } yield app
  }
}
