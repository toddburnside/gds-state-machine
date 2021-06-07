package edu.gemini.aspen.gds.obsstates

import edu.gemini.aspen.gds.keywords.CollectedKeyword
import edu.gemini.aspen.gds.obsevents.ObservationEvent

sealed trait ObservationStateEvent

object ObservationStateEvent {
  final case class Start(dataLabel: String, programId: String) extends ObservationStateEvent
  final case class Stop(dataLabel: String) extends ObservationStateEvent
  final case class Abort(dataLabel: String) extends ObservationStateEvent
  final case class Delete(dataLabel: String) extends ObservationStateEvent
  final case class AddObservationEvent(dataLabel: String, obsEvent: ObservationEvent)
      extends ObservationStateEvent
  final case class AddKeyword(dataLabel: String, keyword: CollectedKeyword)
      extends ObservationStateEvent
  final case class CollectKeywords(dataLabel: String, obsEvent: ObservationEvent)
      extends ObservationStateEvent
  // used by ObservationFSM to step through the observation completion.
  final case class Step(dataLabel: String) extends ObservationStateEvent
  final case class Complete(dataLabel: String) extends ObservationStateEvent
  final case object PurgeStale extends ObservationStateEvent
}
