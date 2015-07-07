package edu.cmu.cs.ls.keymaerax.parser

import scala.collection.immutable._

import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.core.Number

import scala.math.Ordered

/** Operator associativity notational specification */
sealed trait OpNotation
trait NullaryNotation extends OpNotation
trait UnaryNotation extends OpNotation
trait BinaryNotation extends OpNotation

/** Atomic operators of arity 0 within syntactic category */
object AtomicFormat extends NullaryNotation

/** Unary prefix operators of arity 1 within syntactic category */
object PrefixFormat extends UnaryNotation
/** Unary postfix operators of arity 1 within syntactic category */
object PostfixFormat extends UnaryNotation

/** Left-associative infix operators of arity 2, i.e. ``x-y-z=(x-y)-z``*/
object LeftAssociative extends BinaryNotation
/** Right-associative infix operators of arity 2, i.e. ``x^y^z=x^(y^z)`` */
object RightAssociative extends BinaryNotation
/** Non-associative infix operators of arity 2, i.e. explicit parentheses */
object NonAssociative extends BinaryNotation
/** Mixed binary operators of arity 2 */
object MixedBinary extends BinaryNotation

/** Atomic operators of arity 0 within syntactic category with 2 arguments from lower category */
object AtomicBinaryFormat extends BinaryNotation

/**
 * Operator notation specification.
 * @todo Could add spacing weight information to determine how much spacing is added around an operator.
 */
trait OpSpec extends Ordered[OpSpec] {
  /** opcode operator code used for string representation */
  final def opcode: String = op.img
  /** Token */
  def op: Terminal
  /** prec unique precedence where smaller numbers indicate stronger binding */
  def prec: Int
  /** notational associativity */
  def assoc: OpNotation

  def compare(other: OpSpec): Int = {
    prec - other.prec
  } /*ensuring(r => r!=0 || this==other, "precedence assumed unique " + this + " compared to " + other)*/
  //@note violates this: two different things can have same precedence.
}

case class UnitOpSpec(op: Terminal, prec: Int,
                               const: String => Expression) extends OpSpec {
  def assoc = AtomicFormat
}

object UnitOpSpec {
  def apply(op: Terminal, prec: Int,
             const: Expression) =
    new UnitOpSpec(op, prec, s => {assert(s == op.img); const})
}

case class UnaryOpSpec[T<:Expression](op: Terminal, prec: Int, assoc: UnaryNotation, kind: Kind,
                                const: (String, T) => T) extends OpSpec

object UnaryOpSpec {
  def apply[T<:Expression](op: Terminal, prec: Int, assoc: UnaryNotation, kind: Kind,
            const: T => T) =
    new UnaryOpSpec[T](op, prec, assoc, kind, (s, e) => {assert(s == op.img); const(e)})

  // mixed converters

  def lUnaryOpSpecT[T<:Expression](op: Terminal, prec: Int, assoc: UnaryNotation, kind: Kind,
                           const: (Term) => T) =
    new UnaryOpSpec(op, prec, assoc, kind, (s, e:T) => {assert(s == op.img); const(e.asInstanceOf[Term])})

  def lUnaryOpSpecF[T<:Expression](op: Terminal, prec: Int, assoc: UnaryNotation, kind: Kind,
                           const: (Formula) => T) =
    new UnaryOpSpec(op, prec, assoc, kind, (s, e:T) => {assert(s == op.img); const(e.asInstanceOf[Formula])})
}

case class BinaryOpSpec[T<:Expression](op: Terminal, prec: Int, assoc: BinaryNotation, kind: (Kind,Kind),
                              const: (String, T, T) => T) extends OpSpec

object BinaryOpSpec {
  def apply[T<:Expression](op: Terminal, prec: Int, assoc: BinaryNotation, kind: (Kind,Kind),
            const: (T, T) => T) =
    new BinaryOpSpec[T](op, prec, assoc, kind, (s, e1, e2) => {assert(s == op.img); const(e1,e2)})

  // mixed converters

  def lBinaryOpSpec[T<:Expression](op: Terminal, prec: Int, assoc: BinaryNotation, kind: (Kind,Kind),
                           const: (Term, Term) => T) =
    new BinaryOpSpec(op, prec, assoc, kind, (s, e1:T, e2:T) => {assert(s == op.img); const(e1.asInstanceOf[Term],e2.asInstanceOf[Term])})
}


/**
 * Differential Dynamic Logic's concrete syntax: operator notation specifications.
 * @author aplatzer
 */
object OpSpec {
  /** Whether to terminate atomic statements with a semicolon instead of separating sequential compositions by a semicolon. */
  /*private[parser]*/ val statementSemicolon = true

  /** no notation */
  private val none = PSEUDO

  import UnaryOpSpec.lUnaryOpSpecF
  import UnaryOpSpec.lUnaryOpSpecT
  import BinaryOpSpec.lBinaryOpSpec

  // operator notation specifications


  // terms
  private val unterm = TermKind
  private val binterm = (TermKind,TermKind)
  val sDotTerm      = UnitOpSpec (DOT,     0, DotTerm)
  val sNothing      = UnitOpSpec (NOTHING,  0, Nothing)
  val sAnything     = UnitOpSpec (ANYTHING, 0, Anything)
  val sVariable     = UnitOpSpec (none,    0, name => Variable(name, None, Real))
  val sNumber       = UnitOpSpec (none,    0, number => Number(BigDecimal(number)))
  val sFuncOf       = UnaryOpSpec[Term](none,    0, PrefixFormat, unterm, (name:String, e:Term) => FuncOf(Function(name, None, Real, Real), e))
  val sDifferentialSymbol = UnaryOpSpec[Term](PRIME, 0, PostfixFormat, unterm, (v:Term) => DifferentialSymbol(v.asInstanceOf[Variable]))
  val sPair         = BinaryOpSpec[Term](COMMA,   888, RightAssociative, binterm, Pair.apply _)
  val sDifferential = UnaryOpSpec[Term] (PRIME,    10, PostfixFormat, unterm, Differential.apply _)
  val sNeg          = UnaryOpSpec[Term] (MINUS,    11, PrefixFormat, unterm, Neg.apply _)
  val sPower        = BinaryOpSpec[Term](POWER,   20, RightAssociative, binterm, Power.apply _)
  val sTimes        = BinaryOpSpec[Term](STAR,    40, LeftAssociative, binterm, Times.apply _)
  val sDivide       = BinaryOpSpec[Term](SLASH,    40, LeftAssociative, binterm, Divide.apply _)
  val sPlus         = BinaryOpSpec[Term](PLUS,    50, LeftAssociative, binterm, Plus.apply _)
  val sMinus        = BinaryOpSpec[Term](MINUS,    50, LeftAssociative, binterm, Minus.apply _)

  // formulas
  private val unfml = (FormulaKind)
  private val binfml = (FormulaKind,FormulaKind)
  private val untermfml = unterm
  private val bintermfml = binterm
  private val quantfml = (TermKind/*Variable*/,FormulaKind)
  private val modalfml = (ProgramKind,FormulaKind)
  val sDotFormula   = UnitOpSpec(PLACE,  0, DotFormula)
  val sTrue         = UnitOpSpec(TRUE, 0, True)
  val sFalse        = UnitOpSpec(FALSE, 0, False)
  val sPredOf       = UnaryOpSpec(none,    0, PrefixFormat, untermfml, (name, e:Expression) => PredOf(Function(name, None, Real, Bool), e.asInstanceOf[Term]))
  val sPredicationalOf = UnaryOpSpec(none,    0, PrefixFormat, unfml, (name, e:Formula) => PredicationalOf(Function(name, None, Bool, Bool), e.asInstanceOf[Formula]))
  val sDifferentialFormula = UnaryOpSpec[Formula](PRIME, 80, PostfixFormat, unfml, DifferentialFormula.apply _)
  val sEqual        = lBinaryOpSpec(EQ,    90, AtomicBinaryFormat, bintermfml, Equal.apply _)
  val sNotEqual     = lBinaryOpSpec(NOTEQ,   90, AtomicBinaryFormat, bintermfml, NotEqual.apply _)
  val sGreaterEqual = lBinaryOpSpec(GREATEREQ,   90, AtomicBinaryFormat, bintermfml, GreaterEqual.apply _)
  val sGreater      = lBinaryOpSpec(RDIA,    90, AtomicBinaryFormat, bintermfml, Greater.apply _)
  val sLessEqual    = lBinaryOpSpec(LESSEQ,   90, AtomicBinaryFormat, bintermfml, LessEqual.apply _)
  val sLess         = lBinaryOpSpec(LDIA,    90, AtomicBinaryFormat, bintermfml, Less.apply _ )
  val sForall       = BinaryOpSpec[Expression](FORALL, 96, MixedBinary, quantfml, (x:Expression, f:Expression) => Forall(Seq(x.asInstanceOf[Variable]), f.asInstanceOf[Formula]))
  val sExists       = BinaryOpSpec[Expression](EXISTS,97, MixedBinary, quantfml, (x:Expression, f:Expression) => Exists(Seq(x.asInstanceOf[Variable]), f.asInstanceOf[Formula]))
  val sBox          = BinaryOpSpec[Expression](PSEUDO, 98, MixedBinary, modalfml, (_:String, a:Expression, f:Expression) => Box(a.asInstanceOf[Program], f.asInstanceOf[Formula]))
  val sDiamond      = BinaryOpSpec[Expression](PSEUDO, 99, MixedBinary, modalfml, (_:String, a:Expression, f:Expression) => Diamond(a.asInstanceOf[Program], f.asInstanceOf[Formula]))
  val sNot          = UnaryOpSpec[Formula] (NOT,   100, PrefixFormat, unfml, Not.apply _)
  val sAnd          = BinaryOpSpec[Formula](AMP,   110, LeftAssociative, binfml, And.apply _)
  val sOr           = BinaryOpSpec[Formula](OR,   120, LeftAssociative, binfml, Or.apply _)
  val sImply        = BinaryOpSpec[Formula](IMPLY,  130, RightAssociative, binfml, Imply.apply _)
  val sRevImply     = BinaryOpSpec[Formula](REVIMPLY, 130, LeftAssociative, binfml, (l:Formula, r:Formula) => Imply(r, l)) /* swaps arguments */
  val sEquiv        = BinaryOpSpec[Formula](EQUIV, 130, NonAssociative, binfml, Equiv.apply _)

  // programs
  private val unprog = ProgramKind
  private val binprog = (ProgramKind,ProgramKind)
  private val bindiffprog = (DifferentialProgramKind,DifferentialProgramKind)
  private val bintermprog = binterm
  private val untermprog = unterm
  private val unfmlprog = (FormulaKind)
  private val diffprogfmlprog = (DifferentialProgramKind,FormulaKind)
  val sProgramConst = UnitOpSpec(none,    0, name => ProgramConst(name))
  val sDifferentialProgramConst = UnitOpSpec(none,  0, name => DifferentialProgramConst(name))
  val sAssign       = lBinaryOpSpec[Program](ASSIGN,  200, AtomicBinaryFormat, bintermprog, (x:Term, e:Term) => Assign(x.asInstanceOf[Variable], e.asInstanceOf[Term]))
  val sDiffAssign   = lBinaryOpSpec[Program](ASSIGN,  200, AtomicBinaryFormat, bintermprog, (xp:Term, e:Term) => DiffAssign(xp.asInstanceOf[DifferentialSymbol], e.asInstanceOf[Term]))
  val sAssignAny    = lUnaryOpSpecT[Program](ASSIGNANY, 200, PostfixFormat, untermprog, (x:Term) => AssignAny(x.asInstanceOf[Variable]))
  val sTest         = lUnaryOpSpecF[Program](TEST,   200, PrefixFormat, unfmlprog, (f:Formula) => Test(f.asInstanceOf[Formula]))
  val sAtomicODE    = BinaryOpSpec[Program](EQ,   90/*200*/, AtomicBinaryFormat, bintermprog, (_:String, xp:Expression, e:Expression) => AtomicODE(xp.asInstanceOf[DifferentialSymbol], e.asInstanceOf[Term]))
  val sDifferentialProduct = BinaryOpSpec(COMMA, 95/*210*/, RightAssociative, bindiffprog, DifferentialProduct.apply _)
  val sODESystem    = BinaryOpSpec[Expression](AMP, 150, NonAssociative, diffprogfmlprog, (_:String, ode:Expression, h:Expression) => ODESystem(ode.asInstanceOf[DifferentialProgram], h.asInstanceOf[Formula]))
  val sLoop         = UnaryOpSpec[Program](STAR,   220, PostfixFormat, unprog, Loop.apply _)
  val sCompose      = BinaryOpSpec[Program](SEMI, 230, RightAssociative, binprog, Compose.apply _) //@todo compatibility mode for parser
  //valp: Compose     => OpNotation("",    230, RightAssociative)
  val sChoice       = BinaryOpSpec[Program](CHOICE,  240, RightAssociative, binprog, Choice.apply _)

  val sEOF          = UnitOpSpec  (EOF, Int.MaxValue, _ => throw new AssertionError("Cannot construct EOF"))
  val sNone         = UnitOpSpec  (PSEUDO, Int.MaxValue, _ => throw new AssertionError("Cannot construct NONE"))


  /** The operator notation of the top-level operator of expr with opcode, precedence and associativity  */
  def op(expr: Expression): OpSpec = expr match {
      //@note could replace by reflection getField("s" + expr.getClass.getSimpleName)
      //@todo could add a contract ensuring that constructor applied to expressions's children indeed produces expr.
    // terms
    case DotTerm         => sDotTerm
    case Nothing         => sNothing
    case Anything        => sAnything
    case t: Variable     => sVariable
    case t: Number       => sNumber
    case t: FuncOf       => sFuncOf
    case t: DifferentialSymbol => sDifferentialSymbol
    case t: Pair         => sPair
    case t: Differential => sDifferential
    case t: Neg          => sNeg
    case t: Power        => sPower
    case t: Times        => sTimes
    case t: Divide       => sDivide
    case t: Plus         => sPlus
    case t: Minus        => sMinus

    // formulas
    case DotFormula      => sDotFormula
    case True            => sTrue
    case False           => sFalse
    case f: PredOf       => sPredOf
    case f: PredicationalOf => sPredicationalOf
    case f: DifferentialFormula => sDifferentialFormula
    case f: Equal        => sEqual
    case f: NotEqual     => sNotEqual
    case f: GreaterEqual => sGreaterEqual
    case f: Greater      => sGreater
    case f: LessEqual    => sLessEqual
    case f: Less         => sLess
    case f: Forall       => sForall
    case f: Exists       => sExists
    case f: Box          => sBox
    case f: Diamond      => sDiamond
    case f: Not          => sNot
    case f: And          => sAnd
    case f: Or           => sOr
    case f: Imply        => sImply
    case f: Equiv        => sEquiv

    // programs
    case p: ProgramConst => sProgramConst
    case p: DifferentialProgramConst => sDifferentialProgramConst
    case p: Assign       => sAssign
    case p: DiffAssign   => sDiffAssign
    case p: AssignAny    => sAssignAny
    case p: Test         => sTest
    case p: ODESystem    => sODESystem
    case p: AtomicODE    => sAtomicODE
    case p: DifferentialProduct => sDifferentialProduct
    case p: Loop         => sLoop
    case p: Compose      => sCompose
    case p: Choice       => sChoice
  }

}

