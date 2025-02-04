/*
 * *
 *   * Copyright (c) 2021 Carnegie Mellon University. CONFIDENTIAL
 *   * See LICENSE.txt for the conditions of this license.
 *
 *
 */

package edu.cmu.cs.ls.keymaerax.utils

import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.btactics.TacticHelper

/**
  * Compile a hybrid program to a discrete program performing some form of numerical integration.
  *
  * @author André Platzer
  */
trait NumericalCompiler extends (Program => Program) {
  /**
    * Compile `hp` to a discrete program whose runs perform some numerical integration of `hp` (usually without clever error control).
    * @param hp the hybrid program
    * @return a discrete program, which, if run, computes a numerical integration of `hp`.
    */
  override def apply(hp: Program): Program
}


/**
  * Compile a hybrid program to a discrete program by naive explicit forward Euler integration.
  *
  * @author André Platzer
  */
class EulerIntegrationCompiler extends NumericalCompiler {
  /**
    * Compile `hp` to a discrete program whose runs perform some numerical integration of `hp` (usually without clever error control).
    *
    * @param hp the hybrid program
    * @return a discrete program, which, if run, computes a numerical integration of `hp`.
    */
  override def apply(hp: Program): Program = {
    // fresh step size variable for numerical integration
    val step: Variable = TacticHelper.freshNamedSymbol(BaseVariable("step", None, Real), hp)

    /** turn {x'=f(x,y),y':=g(x,y)} ODE System into sequential composition of differential assignments x':=f(x,y); y':=g(x,y) */
    def toDifferentialAssignments(ode: DifferentialProgram): Program = ode match {
      case AtomicODE(xp, e) => Assign(xp, e)
      case DifferentialProduct(l, r) => Compose(toDifferentialAssignments(l), toDifferentialAssignments(r))
      case a: DifferentialProgramConst => throw new IllegalArgumentException("Differential program constant symbols are not supported " + a)
    }

    /** recursively decompose */
    def trafo(prog: Program): Program = prog match {
      case a: AtomicProgram => a
      case Choice (a, b) => Choice (trafo(a), trafo(b))
      case Compose(a, b) => Compose(trafo(a), trafo(b))
      case Loop(a) => Loop(trafo(a))
      case ODESystem(ode, True) =>
        // explicit forward Euler integration loop
        // (non-differential) state variables changing during hp
        val vars: Set[Variable] = StaticSemantics(ode).bv.toSet.filter(_.isInstanceOf[BaseVariable])
        // x':=f(x); for all x
        val toDiffs = toDifferentialAssignments(ode)
        Loop(
          // x:=x+step*x'; for all x
          vars.foldLeft(toDiffs)((acc,x) => Compose(acc, Assign(x, Plus(x, Times(step, DifferentialSymbol(x))))))
        )   // *

      case ODESystem(ode, constraint) =>
        // (non-differential) state variables changing during hp
        val vars: Set[Variable] = StaticSemantics(ode).bv.toSet.filter(_.isInstanceOf[BaseVariable])
         // explicit forward Euler integration loop
         // x':=f(x); for all x
        val toDiffs = toDifferentialAssignments(ode)
        Compose(
          Loop(
            Compose(
              Test(constraint), // ?Q
                // x:=x+step*x'; for all x
                vars.foldLeft(toDiffs)((acc,x) => Compose(acc, Assign(x, Plus(x, Times(step, DifferentialSymbol(x))))))
            )
          )   // *
          ,   // ;
          Test(constraint)
        )
    }


    trafo(hp)
  }
}
