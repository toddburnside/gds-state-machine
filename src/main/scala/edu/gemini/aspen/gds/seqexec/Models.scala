package edu.gemini.aspen.gds.seqexec

import io.circe.{ Decoder, HCursor }

object Models {
  final case class KeywordValue(keyword: String, value: String)

  sealed trait SeqexecRequest {
    val dataLabel: String
  }

  object SeqexecRequest {
    final case class OpenObservation(
      dataLabel: String,
      programId: String,
      keywords:  List[KeywordValue]
    ) extends SeqexecRequest

    final case class Keywords(
      dataLabel: String,
      keywords:  List[KeywordValue]
    ) extends SeqexecRequest

    final case class CloseObservation(dataLabel: String) extends SeqexecRequest
    final case class AbortObservation(dataLabel: String) extends SeqexecRequest
  }

  object Decoders {
    import SeqexecRequest._

    implicit val kvDecoder: Decoder[KeywordValue] = new Decoder[KeywordValue] {
      final def apply(c: HCursor): Decoder.Result[KeywordValue] =
        for {
          k <- c.downField("keyword").as[String]
          v <- c.downField("value").as[String]
        } yield KeywordValue(k, v)
    }

    implicit val ooDecoder: Decoder[OpenObservation] = new Decoder[OpenObservation] {
      final def apply(c: HCursor): Decoder.Result[OpenObservation] =
        for {
          d  <- c.downField("data_label").as[String]
          p  <- c.downField("program_id").as[String]
          ks <- c.downField("keywords").as[List[KeywordValue]]
        } yield OpenObservation(d, p, ks)
    }

    implicit val kwDecoder: Decoder[Keywords] = new Decoder[Keywords] {
      final def apply(c: HCursor): Decoder.Result[Keywords] =
        for {
          d  <- c.downField("data_label").as[String]
          ks <- c.downField("keywords").as[List[KeywordValue]]
        } yield Keywords(d, ks)
    }

    implicit val coDecoder: Decoder[CloseObservation] = new Decoder[CloseObservation] {
      final def apply(c: HCursor): Decoder.Result[CloseObservation] =
        for {
          d <- c.downField("data_label").as[String]
        } yield CloseObservation(d)
    }

    implicit val aoDecoder: Decoder[AbortObservation] = new Decoder[AbortObservation] {
      final def apply(c: HCursor): Decoder.Result[AbortObservation] =
        for {
          d <- c.downField("data_label").as[String]
        } yield AbortObservation(d)
    }
  }
}
