package edu.cmu.cs.ls.keymaerax.tools.qe

import java.math.{MathContext, RoundingMode}

import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.tools.{Tool, ToolEvidence}

import scala.collection.immutable.Map

/** (Hopefully) trustworthy and fast tool to help speed up IntervalArithmeticV2
  * 'proves' quantifier- and variable-free arithmetic formulas by evaluation with BigDecimals
 *
  * @author Fabian Immler
  */
object BigDecimalQETool extends Tool with QETool {
  /** @inheritdoc */
  override val name: String = "BigDecimalQETool"

  // TODO: taken from DifferentialTactics, should perhaps be in a more central place?
  val maxF = Function("max", None, Tuple(Real, Real), Real, interpreted=true)
  val minF = Function("min", None, Tuple(Real, Real), Real, interpreted=true)

  private def unableToEvaluate(e: Expression) = (name + " unable to evaluate " + e)

  def eval(t: Term) : BigDecimal = t match {
    case Plus(a, b) => eval(a) + eval(b)
    case Minus(a, b) => eval(a) - eval(b)
    case Neg(a) => -eval(a)
    case Times(a, b) => eval(a) * eval(b)
    case Power(a, b) =>
      (eval(a), eval(b)) match {
        case (x, y) if y.isValidInt && y >= 1 => x pow y.toIntExact
        case (x, y) if x != 0 && y == 0 => BigDecimal(1)
        case (x, y) if x == BigDecimal(10) && y.isValidInt => BigDecimal(1).bigDecimal.scaleByPowerOfTen(y.toIntExact)
        case (x, y) => throw new IllegalArgumentException(unableToEvaluate(t))
      }
    case Number(a) => BigDecimal(a.bigDecimal, new MathContext(0, RoundingMode.UNNECESSARY))
    case FuncOf(f, Pair(a, b)) =>
      if (f == minF) eval(a) min eval(b)
      else if (f == maxF) eval(a) max eval(b)
      else throw new IllegalArgumentException(unableToEvaluate(t))
    case Divide(_, _) => throw new IllegalArgumentException(unableToEvaluate(t))
  }
  def eval(fml: Formula) : Boolean = fml match {
    case LessEqual(s, t) => eval(s) <= eval(t)
    case GreaterEqual(s, t) => eval(s) >= eval(t)
    case Less(s, t) => eval(s) < eval(t)
    case Greater(s, t) => eval(s) > eval(t)
    case Equal(s, t) => eval(s) == eval(t)
    case NotEqual(s, t) => eval(s) != eval(t)
    case And(f, g) => eval(f) && eval(g)
    case Or(f, g) => eval(f) || eval(g)
    case True => true
    case False => false
  }

  /** @inheritdoc */
  override def qeEvidence(formula: Formula): (Formula, Evidence) =
    (if (eval(formula)) True else formula, ToolEvidence(("message", "evaluated BigDecimal numerics") :: Nil))

  /** @inheritdoc */
  final override def init(config: Map[String,String]): Unit = {}

  /** @inheritdoc */
  final override def restart(): Unit = {}

  /** @inheritdoc */
  final override def shutdown(): Unit = {}

  /** @inheritdoc */
  override def cancel(): Boolean = true

  /** @inheritdoc */
  override def isInitialized: Boolean = true
}
