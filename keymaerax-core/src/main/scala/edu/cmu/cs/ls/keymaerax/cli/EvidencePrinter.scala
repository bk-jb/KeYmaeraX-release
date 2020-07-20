package edu.cmu.cs.ls.keymaerax.cli

import edu.cmu.cs.ls.keymaerax.cli.KeYmaeraX.OptionMap

/** Light-weight evidence header for file artifacts produced by KeYmaera X. */
object EvidencePrinter {

  /** Generate a header stamping the source of a generated file */
  //@todo Of course this has a security attack for non-letter characters like end of comments from command line
  def stampHead(options: OptionMap): String = "/* @evidence: generated by KeYmaeraX " +
    edu.cmu.cs.ls.keymaerax.core.VERSION + " " +
    nocomment(options.getOrElse('commandLine, "<unavailable>").asInstanceOf[String]) + " */\n\n"

  /** Replace C-style line-comments in command line (from wildcard paths) */
  private def nocomment(s: String): String = s.replaceAllLiterally("/*", "/STAR").replaceAllLiterally("*/", "STAR/")

}
