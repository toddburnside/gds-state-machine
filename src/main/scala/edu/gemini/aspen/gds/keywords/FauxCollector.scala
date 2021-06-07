package edu.gemini.aspen.gds.keywords

import cats._
import cats.effect._
import cats.effect.std.Random
import cats.syntax.all._
import edu.gemini.aspen.gds._
import edu.gemini.aspen.gds.config._
import scala.concurrent.duration._

object FauxCollector {
  def apply[F[_]: Async: Parallel: Random](
    subSystem: SubSystem,
    config:    Configuration
  ): F[KeywordCollector[F]] = {
    val retriever: ConfigurationItem => F[String] = ci => {
      for {
        sleep <- Random[F].betweenInt(1, 20)
        _     <- Async[F].sleep(sleep.seconds)
        value  = s"$subSystem: ${ci.keyword}: $sleep"
      } yield value
    }
    KeywordCollector(subSystem, config, retriever)
  }
}
