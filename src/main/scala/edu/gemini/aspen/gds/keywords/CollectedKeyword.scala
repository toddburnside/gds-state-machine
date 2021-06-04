package edu.gemini.aspen.gds.keywords

import edu.gemini.aspen.gds.SubSystem
import edu.gemini.aspen.gds.obsevents.ObservationEvent

sealed trait CollectedKeyword {
  val keyword: String
  val subSystem: SubSystem
  val event: Option[ObservationEvent] // is this needed? Should we have event None?
}

object CollectedKeyword {

  final case class Value(
    keyword:   String,
    subSystem: SubSystem,
    event:     Option[ObservationEvent],
    value:     String
  ) extends CollectedKeyword

  final case class Error(
    keyword:   String,
    subSystem: SubSystem,
    event:     Option[ObservationEvent],
    message:   String
  ) extends CollectedKeyword

}
