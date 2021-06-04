package edu.gemini.aspen.gds

sealed trait SubSystem

object SubSystem {
  final case object Seqexec extends SubSystem
  final case object Epics   extends SubSystem
  final case object Odb     extends SubSystem
}
