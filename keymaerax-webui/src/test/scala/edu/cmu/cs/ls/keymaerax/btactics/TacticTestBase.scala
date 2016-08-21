package edu.cmu.cs.ls.keymaerax.btactics

import java.io.File

import edu.cmu.cs.ls.keymaerax.bellerophon.{BelleExpr, BelleProvable, Interpreter, SequentialInterpreter}
import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.hydra.SQLite.SQLiteDB
import edu.cmu.cs.ls.keymaerax.hydra.{BellerophonTacticExecutor, DBAbstractionObj, ExtractTacticFromTrace}
import edu.cmu.cs.ls.keymaerax.launcher.DefaultConfiguration
import edu.cmu.cs.ls.keymaerax.parser.{KeYmaeraXParser, KeYmaeraXPrettyPrinter, KeYmaeraXProblemParser}
import edu.cmu.cs.ls.keymaerax.tacticsinterface.TraceRecordingListener
import edu.cmu.cs.ls.keymaerax.tools._
import org.scalactic.{AbstractStringUniformity, Uniformity}
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}

import scala.collection.immutable._

/**
 * Base class for tactic tests.
 */
class TacticTestBase extends FlatSpec with Matchers with BeforeAndAfterEach {
  val theInterpreter = SequentialInterpreter()

  /** Tests that want to record proofs in a database. */
  class DbTacticTester {
    private val db = {
      val testLocation = File.createTempFile("testdb", ".sqlite")
      val db = new SQLiteDB(testLocation.getAbsolutePath)
      db.cleanup(testLocation.getAbsolutePath)
      db
    }

    /** Prove sequent `s` using tactic  `t`. Record the proof in the database under the return value `proofId`. */
    def proveBy(modelContent: String, t: BelleExpr): Provable = {
      val modelName = ""
      val s: Sequent = KeYmaeraXProblemParser(modelContent) match {
        case fml: Formula => Sequent(IndexedSeq(), IndexedSeq(fml))
        case _ => fail("Model content " + modelContent + " cannot be parsed")
      }
      db.createModel("guest", modelName, modelContent, "", None, None, None, None) match {
        case Some(modelId) =>
          val proofId = db.createProofForModel(modelId, "", "", "")
          val trace = db.getExecutionTrace(proofId)
          val globalProvable = trace.lastProvable
          val listener = new TraceRecordingListener(db, proofId, trace.executionId.toInt, trace.lastStepId,
            globalProvable, trace.alternativeOrder, 0 /* start from single provable */, recursive = false, "custom")
          SequentialInterpreter(listener :: Nil)(t, BelleProvable(Provable.startProof(s))) match {
            case BelleProvable(provable, _) =>
              extractTactic(proofId) shouldBe t
              provable
            case r => fail("Unexpected tactic result " + r)
          }
        case None => fail("Unable to create temporary model in DB")
      }
    }

    /** Returns the tactic recorded for the proof `proofId`. */
    def extractTactic(proofId: Int): BelleExpr = new ExtractTacticFromTrace(db).apply(proofId)
  }

  /** For tests that want to record proofs in the database. */
  def withDatabase(testcode: DbTacticTester => Any): Unit = testcode(new DbTacticTester())

  /**
   * Creates and initializes Mathematica for tests that want to use QE. Also necessary for tests that use derived
   * axioms that are proved by QE.
   * @example{{{
   *    "My test" should "prove something with Mathematica" in withMathematica { implicit qeTool =>
   *      // ... your test code here
   *    }
   * }}}
   * */
  def withMathematica(testcode: Mathematica => Any) {
    val provider = new MathematicaToolProvider(DefaultConfiguration.defaultMathematicaConfig)
    ToolProvider.setProvider(provider)
    testcode(provider.tool)
  }

  /**
    * Creates and initializes Z3 for tests that want to use QE. Also necessary for tests that use derived
    * axioms that are proved by QE.
    * Note that Mathematica should also ne initialized in order to perform DiffSolution and CounterExample
    * @example{{{
    *    "My test" should "prove something with Mathematica" in withZ3 { implicit qeTool =>
    *      // ... your test code here
    *    }
    * }}}
    * */
  def withZ3(testcode: Z3 => Any) {
    val provider = new Z3ToolProvider
    ToolProvider.setProvider(provider)
    testcode(provider.tool)
  }

  /**
    * Creates and initializes Polya for tests that want to use QE. Also necessary for tests that use derived
    * axioms that are proved by QE.
    * Note that Mathematica should also ne initialized in order to perform DiffSolution and CounterExample
    * @example{{{
    *    "My test" should "prove something with Mathematica" in withPolya { implicit qeTool =>
    *      // ... your test code here
    *    }
    * }}}
    * */
  def withPolya(testcode: Polya => Any) {
    val provider = new PolyaToolProvider
    ToolProvider.setProvider(provider)
    testcode(provider.tool)
  }

  /** Test setup */
  override def beforeEach() = {
    PrettyPrinter.setPrinter(KeYmaeraXPrettyPrinter.pp)
    val generator = new ConfigurableGenerate[Formula]()
    KeYmaeraXParser.setAnnotationListener((p: Program, inv: Formula) => generator.products += (p->inv))
    TactixLibrary.invGenerator = generator
  }

  /* Test teardown */
  override def afterEach() = {
    PrettyPrinter.setPrinter(e => e.getClass.getName)
    ToolProvider.shutdown()
    TactixLibrary.invGenerator = new NoneGenerate()
  }

  /** Proves a formula using the specified tactic. Fails the test when tactic fails.
    * @todo remove proveBy in favor of [[TactixLibrary.proveBy]] to avoid incompatibilities or meaingless tests if they do something else
    */
  //@deprecated("TactixLibrary.proveBy should probably be used instead of TacticTestBase")
  def proveBy(fml: Formula, tactic: BelleExpr): Provable = {
    val v = BelleProvable(Provable.startProof(fml))
    theInterpreter(tactic, v) match {
      case BelleProvable(provable, _) => provable
      case r => fail("Unexpected tactic result " + r)
    }
  }

  /** Proves a sequent using the specified tactic. Fails the test when tactic fails. */
  //@deprecated("TactixLibrary.proveBy should probably be used instead of TacticTestBase")
  def proveBy(s: Sequent, tactic: BelleExpr): Provable = {
    val v = BelleProvable(Provable.startProof(s))
    theInterpreter(tactic, v) match {
      case BelleProvable(provable, _) => provable
      case r => fail("Unexpected tactic result " + r)
    }
  }

  //@deprecated("TactixLibrary.proveBy should probably be used instead of TacticTestBase")
  def proveBy(p: Provable, tactic: BelleExpr): Provable = {
    val v = BelleProvable(p)
    theInterpreter(tactic, v) match {
      case BelleProvable(provable, _) => provable
      case r => fail("Unexpected tactic result " + r)
    }
  }

  /** Removes all whitespace for string comparisons in tests.
    * @example{{{
    *     "My string with     whitespace" should equal ("Mystring   with whitespace") (after being whiteSpaceRemoved)
    * }}}
    */
  val whiteSpaceRemoved: Uniformity[String] =
    new AbstractStringUniformity {
      def normalized(s: String): String = s.replaceAll("\\s+", "")
      override def toString: String = "whiteSpaceRemoved"
    }

  def loneSucc(p: Provable) = {
    assert(p.subgoals.length==1)
    assert(p.subgoals.last.succ.length==1)
    p.subgoals.last.succ.last
  }
}