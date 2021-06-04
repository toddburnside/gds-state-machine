package edu.gemini.aspen.gds.config

import edu.gemini.aspen.gds.SubSystem
import edu.gemini.aspen.gds.obsevents.ObservationEvent

// highly simplified
final case class ConfigurationItem(event: ObservationEvent, keyword: String, subSystem: SubSystem)

final case class Configuration(items: List[ConfigurationItem]) {
  def forSubSystem(subSystem: SubSystem): Configuration        =
    copy(items = items.filter(_.subSystem == subSystem))
  def forEvent(event:         ObservationEvent): Configuration =
    copy(items = items.filter(_.event == event))
}
