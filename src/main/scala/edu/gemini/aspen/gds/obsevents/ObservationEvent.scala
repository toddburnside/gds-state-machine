package edu.gemini.aspen.gds.obsevents

sealed trait ObservationEvent

object ObservationEvent {
  case object OBS_PREP           extends ObservationEvent
  case object OBS_END_ACQ        extends ObservationEvent
  case object OBS_END_DSET_WRITE extends ObservationEvent

  val all: Set[ObservationEvent] = Set(OBS_PREP, OBS_END_ACQ, OBS_END_DSET_WRITE)
}
