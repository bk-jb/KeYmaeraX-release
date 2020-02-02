/**
* Copyright (c) Carnegie Mellon University.
* See LICENSE.txt for the conditions of this license.
*/
package edu.cmu.cs.ls.keymaerax.btactics

import java.io.File

import edu.cmu.cs.ls.keymaerax.bellerophon.IOListeners.PrintProgressListener
import edu.cmu.cs.ls.keymaerax.bellerophon._
import edu.cmu.cs.ls.keymaerax.bellerophon.parser.BelleParser
import edu.cmu.cs.ls.keymaerax.btactics.Generator.Generator
import edu.cmu.cs.ls.keymaerax.btactics.InvariantGenerator.GenProduct
import edu.cmu.cs.ls.keymaerax.core.{Expression, Formula, Lemma, Program}
import edu.cmu.cs.ls.keymaerax.hydra.{DatabasePopulator, TempDBTools}
import edu.cmu.cs.ls.keymaerax.hydra.DatabasePopulator.TutorialEntry
import edu.cmu.cs.ls.keymaerax.lemma.LemmaDBFactory
import edu.cmu.cs.ls.keymaerax.parser.{KeYmaeraXArchiveParser, KeYmaeraXParser}
import edu.cmu.cs.ls.keymaerax.parser.KeYmaeraXArchiveParser.Declaration
import edu.cmu.cs.ls.keymaerax.tags.{ExtremeTest, SlowTest}
import edu.cmu.cs.ls.keymaerax.tools.ToolEvidence
import org.scalatest.AppendedClues
import org.scalatest.exceptions.TestFailedException

import scala.io.Source
import scala.language.postfixOps
import org.scalatest.prop.TableDrivenPropertyChecks._

import scala.util.matching.Regex

/**
 * Tutorial and case study test cases.
 * @author Stefan Mitsch
 */
abstract class RegressionTesterBase(val tutorialName: String, val url: String) extends TacticTestBase with AppendedClues {

  private def table(entries: List[TutorialEntry]) = {
    Table(("Tutorial name", "Entry name", "Model", "Description", "Title", "Link", "Tactic", "Kind"),
      entries.map(e => (tutorialName, e.name, e.model, e.description, e.title, e.link, e.tactic, e.kind)):_*)
  }

  private def table(tactics: List[(String, String, Boolean)]) = {
    Table(("Tactic name", "Tactic content"), tactics.map(t => (t._1, t._2)):_*)
  }

  private lazy val tutorialEntries = table({
    println("Reading " + url)
    if (url.endsWith(".json")) DatabasePopulator.readTutorialEntries(url)
    else if (url.endsWith(".kya") || url.endsWith(".kyx")) DatabasePopulator.readKyx(url)
    else throw new IllegalArgumentException(s"URL must end in either .json, .kya, or .kyx, but got $url")
  })

  tutorialName should "parse all models" in {
    forEvery (tutorialEntries) { (tutorialName, name, model, _, _, _, _, _) =>
      withClue(tutorialName + "/" + name) { KeYmaeraXArchiveParser.parseProblem(model, parseTactics=false) }
    }
  }

  it should "parse all tactics" in {
    forEvery (tutorialEntries.filter(_._7.nonEmpty)) { (tutorialName, name, _, _, _, _, tactics, _) =>
      forEvery (table(tactics)) { ( tname, ttext) =>
        withClue(tutorialName + "/" + name + "/" + tname) { BelleParser(ttext) }
      }
    }
  }

  it should "prove all entries flagged as being provable with Mathematica" in withMathematica { _ => withDatabase {
    prove("Mathematica" :: "Z3" :: Nil)
  }}
  it should "prove all entries flagged as being provable with Z3" in withZ3 { tool => withDatabase {
    tool.setOperationTimeout(30) // avoid getting stuck
    prove("Z3" :: Nil)
  }}

  /* Try to see if any of the Mathematica entries work with Z3. Test "fails" if Z3 can prove an entry. */
  it should "try all Mathematica entries also with Z3" in withZ3 { tool => withDatabase { db =>
    val mathematicaEntries = filterEntriesByTool(_.exists(_.group("toolName") == "Mathematica"), replaceQE=true)

    tool.setOperationTimeout(30) // avoid getting stuck
    forEvery (mathematicaEntries) { (_, name, model, _, _, _, tactic, kind) =>
      whenever(!Thread.currentThread().isInterrupted) {
        try {
          runEntry(name, model, kind, tactic.head, db)
          fail("Now works with Z3: " + tutorialName + "/" + name + "/" + tactic.head._1)
        } catch {
          case _: BelleThrowable => // test "succeeds" (Z3 still fails), so QE("Mathematica") is still required
          case e: TestFailedException if e.getMessage.contains("was not proved") => // master/ODE etc. stopped before proof was done
        }
      }
    }
  }}

  /** Proves all entries that either use no QE at all, all generic QE, or whose specific QE("tool") (if any) match any of the tools */
  private def prove(tools: List[String])(db: TempDBTools): Unit = {
    forEvery (filterEntriesByTool(_.forall(m => tools.contains(m.group("toolName"))), replaceQE=false)) { (_, name, model, _, _, _, tactic, kind) =>
      whenever(!Thread.currentThread().isInterrupted) { runEntry(name, model, kind, tactic.head, db) }
    }
  }

  private def runEntry(name: String, model: String, kind: String, tactic: (String, String, Boolean), db: TempDBTools) = {
    withClue(tutorialName + ": " + name + "/" + tactic._1) {
      val (decls, invGen) = parseProblem(model)
      println(s"Proving $name with ${tactic._1}")
      // backwards compatibility: start with expandAll if model has expansible definitions and tactic does not expand any
      val expandAll = decls.decls.exists(_._2._3.isDefined) && "(expand(?!All))|(expandAllDefs)".r.findFirstIn(tactic._2).isEmpty
      val t = BelleParser.parseWithInvGen(tactic._2, Some(invGen), decls, expandAll)

      val start = System.currentTimeMillis()
      val proof = db.proveBy(model, t, l => LazySequentialInterpreter(l :+ new PrintProgressListener(t), throwWithDebugInfo = false), name)
      val end = System.currentTimeMillis()

      println(s"Proof Statistics (proved: ${proof.isProved})")
      println(s"$tutorialName, model $name, tactic ${tactic._1}")
      println(s"Duration [ms]: ${end - start}")
      println("Tactic LOC/normalized LOC/steps: " +
        Source.fromString(tactic._2).getLines.size + "/" +
        TacticStatistics.lines(t) + "/" +
        TacticStatistics.size(t))
      println("Proof steps: " + proof.steps)

      if (kind == "lemma") {
        val lemmaName = "user" + File.separator + name
        if (LemmaDBFactory.lemmaDB.contains(lemmaName)) LemmaDBFactory.lemmaDB.remove(lemmaName)
        val evidence = Lemma.requiredEvidence(proof, ToolEvidence(List(
          "tool" -> "KeYmaera X",
          "model" -> model,
          "tactic" -> t.prettyString
        )) :: Nil)
        LemmaDBFactory.lemmaDB.add(new Lemma(proof, evidence, Some(lemmaName)))
      }

      t match {
        case _: PartialTactic => // nothing to do, tactic deliberately allowed to result in a non-proof
        case _ => proof shouldBe 'proved withClue tutorialName + "/" + name + "/" + tactic._1
      }
    }
  }

  /** Parse a problem file to find declarations and invariant annotations */
  private def parseProblem(model: String): (Declaration, Generator[GenProduct]) = {
    TactixLibrary.invGenerator = FixedGenerator(Nil)
    val generator = new ConfigurableGenerator[GenProduct]()
    KeYmaeraXParser.setAnnotationListener((p: Program, inv: Formula) =>
      generator.products += (p -> (generator.products.getOrElse(p, Nil) :+ (inv, None))))
    val entry = KeYmaeraXArchiveParser.parseProblem(model, parseTactics=false)
    TactixLibrary.invGenerator = generator
    TactixLibrary.differentialInvGenerator =
      (sequent,pos) => generator(sequent,pos) #::: InvariantGenerator.differentialInvariantCandidates(sequent,pos)
    KeYmaeraXParser.setAnnotationListener((_: Program, _: Formula) => {}) //@note cleanup for separation between tutorial entries
    (entry.defs, generator)
  }

  /** Restricts the tutorialentries to the ones with tactics matching the filter `toolFilter`. */
  private def filterEntriesByTool(toolFilter: Iterator[Regex.Match] => Boolean, replaceQE: Boolean) = {
    // find all specific QE({`tool`}) and QE("tool") entries, but ignores the generic QE that works with any tool
    val qeOldFinder = """QE\(\{`([^`]+)`\}\)""".r("toolName")
    val qeFinder = """QE\("([^"]+)"\)""".r("toolName")

    val skipEntries = tutorialEntries.filter(e => e._7.nonEmpty && !e._7.exists(t => t._3 &&
      toolFilter(qeFinder.findAllMatchIn(t._2)) && toolFilter(qeOldFinder.findAllMatchIn(t._2)))
    )
    skipEntries.foreach(e => println(s"QE tool mismatch: skipping ${e._2}"))

    val foo = tutorialEntries.flatMap(e => e._7.filter(t => t._3 &&
      toolFilter(qeFinder.findAllMatchIn(t._2)) && toolFilter(qeOldFinder.findAllMatchIn(t._2))).map(t =>
        (e._1, e._2, e._3, e._4, e._5, e._6,
          if (replaceQE) (t._1, qeOldFinder.replaceAllIn(qeFinder.replaceAllIn(t._2, "QE"), "QE"), t._3)::Nil
          else t::Nil, e._8)
      )
    )
    foo
  }
}

@SlowTest
class TutorialRegressionTester(override val tutorialName: String, override val url: String) extends RegressionTesterBase(tutorialName, url)

@ExtremeTest
class CaseStudyRegressionTester(override val tutorialName: String, override val url: String) extends RegressionTesterBase(tutorialName, url)
