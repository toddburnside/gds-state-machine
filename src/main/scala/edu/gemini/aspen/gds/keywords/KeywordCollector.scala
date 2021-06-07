package edu.gemini.aspen.gds.keywords

import cats._
import cats.effect._
import cats.effect.syntax.all._
import cats.syntax.all._
import edu.gemini.aspen.gds.obsevents.ObservationEvent
import edu.gemini.aspen.gds.config.{ Configuration, ConfigurationItem }
import org.typelevel.log4cats.slf4j.Slf4jLogger
import edu.gemini.aspen.gds.SubSystem

trait KeywordCollector[F[_]] {
  def collect(event: ObservationEvent, count: Count[F], adder: CollectedKeyword => F[Unit]): F[Unit]
}

// In real life, retriever will probably return an F[FitsValue]. It can return a failed
// IO, but should handle it's own timeouts and return within a few seconds at most. If needed,
// it should also do it's own shift to a blocking pool.
// retriever might also need the data label and program id.
object KeywordCollector {
  def apply[F[_]: Async: Parallel](
    subSystem: SubSystem,
    config:    Configuration,
    retriever: ConfigurationItem => F[String] // will return F[FitsValue] in real life
  ): F[KeywordCollector[F]] = Slf4jLogger.create[F].map { logger =>
    new KeywordCollector[F] {
      val configForSubsys = config.forSubSystem(subSystem)

      def collect(
        event: ObservationEvent,
        count: Count[F],
        adder: CollectedKeyword => F[Unit]
      ): F[Unit] =
        // I probably should worry about cancellation wrt decrementing the counter
        configForSubsys
          .forEvent(event)
          .items
          .parTraverse(ci => count.incr >> retrieveOne(event, ci, count, adder).start)
          .void

      private def retrieveOne(
        event:      ObservationEvent,
        configItem: ConfigurationItem,
        count:      Count[F],
        adder:      CollectedKeyword => F[Unit]
      ): F[Unit] = for {
        _  <- logger.info(
                s"Collecting keyword ${configItem.keyword} for subsystem $subSystem, event $event"
              )
        kw <- safeRetrieve(event, configItem)
        _  <- logger.info(s"Collected $kw")
        _  <- adder(kw)
        _  <- count.decr
      } yield ()

      private def safeRetrieve(
        event:      ObservationEvent,
        configItem: ConfigurationItem
      ): F[CollectedKeyword] =
        retriever(configItem).attempt.map {
          case Right(value) =>
            CollectedKeyword.Value(configItem.keyword, subSystem, event.some, value)
          case Left(e)      =>
            CollectedKeyword.Error(configItem.keyword, subSystem, event.some, e.getMessage())
        }
    }
  }
}
