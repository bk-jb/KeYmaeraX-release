/*
 * Copyright (c) Carnegie Mellon University.
 * See LICENSE.txt for the conditions of this license.
 */

package btactics

import edu.cmu.cs.ls.keymaerax.bellerophon._
import edu.cmu.cs.ls.keymaerax.btactics._
import edu.cmu.cs.ls.keymaerax.parser.StringConverter._
import edu.cmu.cs.ls.keymaerax.btactics.AxiomaticODESolver._
import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.lemma.LemmaDBFactory
import edu.cmu.cs.ls.keymaerax.parser.KeYmaeraXProblemParser
import edu.cmu.cs.ls.keymaerax.tags.AdvocatusTest

/**
  * @author Nathan Fulton
  */
class AxiomaticODESolverTests extends TacticTestBase {
  import DifferentialHelper._
  import Augmentors._
  import TacticFactory._

  //region integration tests

  "axiomatic ode solver" should "work!" in withMathematica(implicit qeTool => {
    val f = "x=1&v=2&a=0 -> [{x'=v,v'=a}]x^3>=1".asFormula
    val t = TactixLibrary.implyR(1) & apply(qeTool)(1)
    val result = proveBy(f, t)
    loneSucc(result) shouldBe "\\forall t_ (t_>=0->\\forall s_ (0<=s_&s_<=t_->true)->[kyxtime:=kyxtime+1*t_;](a/2*kyxtime^2+2*kyxtime+1)^3>=1)".asFormula
  })

  it should "work using my own time" in withMathematica(implicit qeTool => {
    val f = "x=1&v=2&a=0&t=0 -> [{x'=v,v'=a,t'=1}]x^3>=1".asFormula
    val t = TactixLibrary.implyR(1) & apply(qeTool)(1)
    val result = proveBy(f, t)
    loneSucc(result) shouldBe "\\forall t_ (t_>=0->\\forall s_ (0<=s_&s_<=t_->true)->[t:=t+1*t_;](a/2*t^2+2*t+1)^3>=1)".asFormula
  })


  "axiomatic ode solver" should "solve double integrator" in {withMathematica(implicit qeTool => {
    val f = "x=1&v=2&a=3&t=0 -> [{x'=v,v'=a, t'=1}]x>=0".asFormula
    val t = TactixLibrary.implyR(1) & AxiomaticODESolver(qeTool)(1)
    loneSucc(proveBy(f,t)) shouldBe "\\forall t_ (t_>=0->\\forall s_ (0<=s_&s_<=t_->true)->[t:=t+1*t_;]a/2*t^2+2*t+1>=0)".asFormula
  })}

  //endregion


  //region unit tests

  //@todo exists monotone
  "setupTimeVar" should "work when time exists" in {
    val system = "[{x'=v}]1=1".asFormula
    val tactic = addTimeVarIfNecessary
    val result = proveBy(system, tactic(SuccPosition(1, 0::Nil)))
    loneSucc(result) shouldBe "[{x'=v,kyxtime'=1&true}]1=1".asFormula
  }

  "cutInSolns" should "solve x'=y,t'=1" in {withMathematica(implicit qeTool => {
    val f = "x=0&y=0&t=0 -> [{x'=y, t'=1}]x>=0".asFormula
    val t = TactixLibrary.implyR(1) &  AxiomaticODESolver.cutInSoln(qeTool)(1)
    loneSucc(proveBy(f,t)) shouldBe "[{x'=y,t'=1&true&x=y*t+0}]x>=0".asFormula
  })}

  //@todo Does this need to be fixed?
  it should "solve single time dependent eqn" ignore {withMathematica(implicit qeTool => {
    val f = "x=0&y=0&t=0 -> [{x'=t, t'=1}]x>=0".asFormula
    val t = TactixLibrary.implyR(1) &  AxiomaticODESolver.cutInSoln(qeTool)(1).*@(TheType())
    println(proveBy(f,t))
    loneSucc(proveBy(f,t)) shouldBe ???
  })}

  //endregion

  //region proveAs construct

  "proveAs" should "prove as" in {
    val f = "P() -> P()".asFormula
    val t = ProveAs("piffp", f, TactixLibrary.implyR(1) & TactixLibrary.close) & HilbertCalculus.lazyUseAt("piffp")(1)
    proveBy(f,t) shouldBe 'proved
  }

  it should "work in simplifyPostCondition" in {withMathematica(implicit qeTool => {
    val f = "[{x'=1}](x=22 -> x>0)".asFormula
    val t = simplifyPostCondition(qeTool)(1)
    loneSucc(proveBy(f,t)) shouldBe "[{x'=1&true}]22>0".asFormula
  })}

  //endregion

  //region stand-alone integrator

  "Integrator.apply" should "world on x'=v, v'=a" in {
    val initialConds = conditionsToValues(extractInitialConditions(None)("x=1&v=2&a=3&t=0".asFormula))
    val system = "{x'=v,v'=a, t'=1}".asProgram.asInstanceOf[ODESystem]
    val result = Integrator.apply(initialConds, system)
    println(result.mkString(","))
    result shouldBe "x=a/2*t^2+2*t+1".asFormula :: "v=a*t+2".asFormula :: Nil
  }
  //endregion

  "ODE Solver" should "not exploit soundness bugs" in {withMathematica(implicit qet => {
    val model = """Functions.
                  |  R b.
                  |  R m.
                    |End.
                    |
                    |ProgramVariables.
                    |  R x.
                    |  R v.
                    |  R a.
                    |End.
                    |
                    |Problem.
                    |  x<=m & b>0 -> [a:=-b; {x'=v,v'=a & v>=0}]x<=m
                    |End.
                    |""".stripMargin
    val problem: Formula = KeYmaeraXProblemParser(model)

    import TactixLibrary.{implyR, composeb, assignb, allR, QE}
    val t: BelleExpr = implyR(1) & composeb(1) & assignb(1) & AxiomaticODESolver.axiomaticSolve(qet)(1) & allR(1) & implyR(1) & implyR(1) & assignb(1) & QE

    val result : Provable = proveBy(problem, t)
    result.isProved shouldBe false
  })}
}
