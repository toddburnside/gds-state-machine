package edu.gemini.aspen.gds.seqexec

import cats.effect._
import cats.effect.std.QueueSink
import cats.syntax.all._
import edu.gemini.aspen.gds.keywords.CollectedKeyword
import edu.gemini.aspen.gds.obsstates.ObservationStateEvent
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import edu.gemini.aspen.gds.SubSystem

object Service {
  import Models.Decoders._
  import Models.SeqexecRequest._

  implicit val ooEntityDecoder = jsonOf[IO, OpenObservation]
  implicit val coEntityDecoder = jsonOf[IO, CloseObservation]
  implicit val aoEntityDecoder = jsonOf[IO, AbortObservation]
  implicit val kwEntityDecoder = jsonOf[IO, Keywords]

  def apply(obsStateQ: QueueSink[IO, ObservationStateEvent]) = HttpRoutes.of[IO] {
    case req @ POST -> Root / "open-observation"  =>
      for {
        oo <- req.as[OpenObservation]
        _  <- obsStateQ.offer(ObservationStateEvent.Start(oo.dataLabel, oo.programId))
        _  <- oo.keywords.traverse(kwv =>
                obsStateQ.offer(ObservationStateEvent.AddKeyword(oo.dataLabel, kwv2Collected(kwv)))
              )
        ok <- Ok("Success")
      } yield ok
    case req @ POST -> Root / "close-observation" =>
      for {
        co <- req.as[CloseObservation]
        _  <- obsStateQ.offer(ObservationStateEvent.Stop(co.dataLabel))
        ok <- Ok("Success")
      } yield ok
    case req @ POST -> Root / "abort-observation" =>
      for {
        ao <- req.as[AbortObservation]
        _  <- obsStateQ.offer(ObservationStateEvent.Abort(ao.dataLabel))
        ok <- Ok("Success")
      } yield ok
    case req @ POST -> Root / "keywords"          =>
      for {
        kw <- req.as[Keywords]
        _  <- kw.keywords.traverse(kwv =>
                obsStateQ.offer(ObservationStateEvent.AddKeyword(kw.dataLabel, kwv2Collected(kwv)))
              )
        ok <- Ok("Success")
      } yield ok
  }

  def kwv2Collected(kwv: Models.KeywordValue) =
    CollectedKeyword.Value(kwv.keyword, SubSystem.Seqexec, none, kwv.value)
}
