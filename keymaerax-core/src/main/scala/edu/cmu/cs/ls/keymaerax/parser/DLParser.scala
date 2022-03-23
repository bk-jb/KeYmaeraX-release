/**
  * Copyright (c) Carnegie Mellon University.
  * See LICENSE.txt for the conditions of this license.
  */
/**
  * Differential Dynamic Logic parser for concrete KeYmaera X notation.
  * @author Andre Platzer
  * @see Andre Platzer. [[https://doi.org/10.1007/s10817-016-9385-1 A complete uniform substitution calculus for differential dynamic logic]]. Journal of Automated Reasoning, 59(2), pp. 219-266, 2017.
  */
package edu.cmu.cs.ls.keymaerax.parser

import edu.cmu.cs.ls.keymaerax.core._
import fastparse._
import MultiLineWhitespace._

import scala.collection.immutable._

/**
  * Differential Dynamic Logic parser reads input strings in the concrete syntax of differential dynamic logic of KeYmaera X.
  * @example
  * Parsing formulas from strings is straightforward using [[edu.cmu.cs.ls.keymaerax.parser.KeYmaeraXParser.apply]]:
  * {{{
  * val parser = DLParser
  * val fml0 = parser("x!=5")
  * val fml1 = parser("x>0 -> [x:=x+1;]x>1")
  * val fml2 = parser("x>=0 -> [{x'=2}]x>=0")
  * // parse only formulas
  * val fml3 = parser.formulaParser("x>=0 -> [{x'=2}]x>=0")
  * // parse only programs/games
  * val prog1 = parser.programParser("x:=x+1;{x'=2}")
  * // parse only terms
  * val term1 = parser.termParser("x^2+2*x+1")
  * }}}
  * @author Andre Platzer
  * @see [[KeYmaeraXParser]]
  */
object DLParser extends DLParser {
  assert(OpSpec.statementSemicolon, "This parser is built for formulas whose atomic statements end with a ;")
  assert(OpSpec.negativeNumber, "This parser accepts negative number literals although it does not give precedence to them")

  /** Converts Parsed.Failure to corresponding ParseException to throw. */
  private[keymaerax] def parseException(f: Parsed.Failure): ParseException = {
    val tr: Parsed.TracedFailure = f.trace()
    val inputString = f.extra.input match {
      case IndexedParserInput(input) => input
      case _ => tr.input + ""
    }
    /*@note tr.msg is redundant compared to the following and could be safely elided for higher-level messages */
    /*@note tr.longMsg can be useful for debugging the parser */
    /*@note tr.longAggregateMsg */
    ParseException(tr.longTerminalsMsg /*tr.msg*/,
      location(f),
      found = Parsed.Failure.formatTrailing(f.extra.input, f.index),
      expect = Parsed.Failure.formatStack(tr.input, List(tr.label -> f.index)),
      after = "" + tr.stack.headOption.getOrElse(""),
      state = tr.longMsg,
      //state = Parsed.Failure.formatMsg(tr.input, tr.stack ++ List(tr.label -> tr.index), tr.index),
      hint = "Try " + tr.groupAggregateString).inInput(inputString, None)
  }

  /** The location of a parse failure. */
  private[keymaerax] def location(f: Parsed.Failure): Location = try {
    f.extra.input.prettyIndex(f.index).split(':').toList match {
      case line::col::Nil => Region(line.toInt, col.toInt)
      case line::col::unexpected => Region(line.toInt, col.toInt)
      case unexpected => UnknownLocation
    }
  } catch {
    case _: NumberFormatException => UnknownLocation
  }

  /** parse from a parser with more friendly error reporting */
  private[keymaerax] def parseValue[T](input: String, parser: P[_] => P[T]): T = fastparse.parse(input, parser(_)) match {
    case Parsed.Success(value, index) => value
    case f: Parsed.Failure => throw parseException(f)
  }
}

/**
  * Differential Dynamic Logic parser reads input strings in the concrete syntax of differential dynamic logic of KeYmaera X.
  *
  * @example
  * Parsing formulas from strings is straightforward using [[edu.cmu.cs.ls.keymaerax.parser.KeYmaeraXParser.apply]]:
  * {{{
  * val parser = DLParser
  * val fml0 = parser("x!=5")
  * val fml1 = parser("x>0 -> [x:=x+1;]x>1")
  * val fml2 = parser("x>=0 -> [{x'=2}]x>=0")
  * // parse only formulas
  * val fml3 = parser.formulaParser("x>=0 -> [{x'=2}]x>=0")
  * // parse only programs/games
  * val prog1 = parser.programParser("x:=x+1;{x'=2}")
  * // parse only terms
  * val term1 = parser.termParser("x^2+2*x+1")
  * }}}
  * @author Andre Platzer
  * @see [[KeYmaeraXParser]]
  * @see [[edu.cmu.cs.ls.keymaerax.parser]]
  * @see [[http://keymaeraX.org/doc/dL-grammar.md Grammar]]
  * @see [[https://github.com/LS-Lab/KeYmaeraX-release/wiki/KeYmaera-X-Syntax-and-Informal-Semantics Wiki]]
  */
class DLParser extends Parser {
  import DLParser.parseException
  /** Parse the input string in the concrete syntax as a differential dynamic logic expression
    *
    * @param input the string to parse as a dL formula, dL term, or dL program.
    * @ensures apply(printer(\result)) == \result
    * @throws ParseException if `input` is not a well-formed expression of differential dynamic logic or differential game logic.
    */
  override def apply(input: String): Expression = exprParser(input)


  /** Parse the input string in the concrete syntax as a differential dynamic logic expression */
    //@todo store the parser for speed
  val exprParser: String => Expression = (s => {
      val newres = fastparse.parse(s, fullExpression(_)) match {
        case Parsed.Success(value, index) => value
        case f: Parsed.Failure => throw parseException(f)
      }
      val oldres = KeYmaeraXParser.apply(s)
      if (oldres != newres) {
        println("Oops expr: `" + s + "`")
        println(oldres)
        println(newres)
      }
      newres
    })

  /** Parse the input string in the concrete syntax as a differential dynamic logic term */
  override val termParser: String => Term = (s => {
    val newres = fastparse.parse(s, fullTerm(_)) match {
      case Parsed.Success(value, index) => value
      case f: Parsed.Failure => throw parseException(f)
    }
    val oldres = KeYmaeraXParser.termParser(s)
    if (oldres != newres) {
      println("Oops term: `" + s + "`")
      println(oldres)
      println(newres)
    }
    newres
  })

  /** Parse the input string in the concrete syntax as a differential dynamic logic formula */
  override val formulaParser: String => Formula = (s => {
    val newres = fastparse.parse(s, fullFormula(_)) match {
      case Parsed.Success(value, index) => value
      case f: Parsed.Failure => throw parseException(f)
    }
    val oldres = KeYmaeraXParser.formulaParser(s)
    if (oldres != newres) {
      println("Oops formula: `" + s + "`")
      println(oldres)
      println(newres)
    }
    newres
  })

  /** Parse the input string in the concrete syntax as a differential dynamic logic program */
  override val programParser: String => Program = (s => {
    val newres = fastparse.parse(s, fullProgram(_)) match {
      case Parsed.Success(value, index) => value
      case f: Parsed.Failure => throw parseException(f)
    }
    val oldres = KeYmaeraXParser.programParser(s)
    if (oldres != newres) {
      println("Oops program: `" + s + "`")
      println(oldres)
      println(newres)
    }
    newres
  })

  /** Parse the input string in the concrete syntax as a differential dynamic logic differential program */
  override val differentialProgramParser: String => DifferentialProgram = (s => {
    val newres = fastparse.parse(s, fullDifferentialProgram(_)) match {
      case Parsed.Success(value, index) => value
      case f: Parsed.Failure => throw parseException(f)
    }
    val oldres = KeYmaeraXParser.differentialProgramParser(s)
    if (oldres != newres) {
      println("Oops diff prog: `" + s + "`")
      println(oldres)
      println(newres)
    }
    newres
  })

  /** Parse the input string in the concrete syntax as a differential dynamic logic sequent. */
  override val sequentParser: String => Sequent = (s => {
    val newres = fastparse.parse(s, fullSequent(_)) match {
      case Parsed.Success(value, index) => value
      case f: Parsed.Failure => throw parseException(f)
    }
    val oldres = KeYmaeraXParser.sequentParser(s)
    if (oldres != newres) {
      println("Oops sequent: `" + s + "`")
      println(oldres)
      println(newres)
    }
    newres
  })

  /** Parse the input string in the concrete syntax as a ;; separated list fof differential dynamic logic sequents . */
  val sequentListParser: String => List[Sequent] = (s => {
    fastparse.parse(s, fullSequentList(_)) match {
      case Parsed.Success(value, index) => value
      case f: Parsed.Failure => throw parseException(f)
    }
  })

  /** A pretty-printer that can write the output that this parser reads
    *
    * @ensures \forall e: apply(printer(e)) == e
    */
  override lazy val printer: KeYmaeraXPrettyPrinter.type = KeYmaeraXPrettyPrinter


  /**
    * Register a listener for @annotations during the parse.
    *
    * @todo this design is suboptimal.
    */
  override def setAnnotationListener(listener: (Program,Formula) => Unit): Unit =
    this.annotationListener = listener

  // internals

  private[parser] var annotationListener: ((Program,Formula) => Unit) = {(p,inv) => }

  /** Report an @invariant @annotation to interested parties */
  private def reportAnnotation(p: Program, invariant: Formula): Unit = annotationListener(p,invariant)

  /** `true` has unary negation `-` bind weakly like binary subtraction.
    * `false` has unary negation `-` bind strong just shy of power `^`. */
  private val weakNeg: Boolean = true


  //*****************
  // implementation
  //*****************

  def fullTerm[_: P]: P[Term]   = P( term ~ End )
  def fullFormula[_: P]: P[Formula]   = P( formula ~ End )
  def fullProgram[_: P]: P[Program]   = P( program ~ End )
  def fullDifferentialProgram[_: P]: P[DifferentialProgram]   = {
    /* Hack because old parser allowed with or without {}s */
    P( "{" ~ diffProgram ~ "}" | diffProgram ~ End )
  }

  def fullExpression[_: P]: P[Expression] = P( NoCut(formula ~ End) | NoCut(term ~ End) | (program ~ End) )

  def expression[_: P]: P[Expression] = P( NoCut(formula) | NoCut(term) | program )

  def fullSequent[_: P]: P[Sequent]   = P( sequent ~ End )

  def fullSequentList[_: P]: P[List[Sequent]]   = P( sequentList ~ End )

  //*****************
  // terminals
  //*****************

  //@todo how to ensure longest-possible match in all terminals everywhere? Avoid reading truenot as "true" "not"

  /** Explicit nonempty whitespace terminal. */
  def blank[_:P]: P[Unit] = P( CharsWhileIn(" \t\r\n", 1) )

  /** parse a number literal */
  def number[_: P]: P[Number] = {
    import NoWhitespace._
    P(("-".? ~~ CharIn("0-9").rep(1) ~~ ("." ~~/ CharIn("0-9").rep(1)).?).!).
      map(s => Number(BigDecimal(s)))
  }
  /** parse an identifier.
    * @return the name and its index (if any).
    * @note Index is normalized so that x_00 cannot be mentioned and confused with x_0.*/
  def ident[_: P]: P[(String,Option[Int])] = {
    import NoWhitespace._
    P( (CharIn("a-zA-Z") ~~ CharIn("a-zA-Z0-9").repX).! ~~
      (("_" ~~ ("_".? ~~ ("0" | CharIn("1-9") ~~ CharIn("0-9").repX)).!) |
        "_".!).?
    ).map({
      case (s,None) => (s,None)
      case (s,Some("_")) => (s+"_",None)
      case (s,Some(n))=>
        if (n.startsWith("_")) (s+"_",Some(n.drop(1).toInt))
        else (s,Some(n.toInt))
    })
  }

  /** `.` or `._2`: dot parsing */
  def dot[_:P]: P[DotTerm] = {
    import NoWhitespace._
    P( "." ~~ ("_" ~~ ("0" | CharIn("1-9") ~~ CharIn("0-9").repX).!.map(_.toInt)).? ).map(idx => DotTerm(Real, idx))
  }

  // terminals not used here but provided for other DL parsers
  private def stringChars(c: Char): Boolean = c != '\"' && c != '\\'

  /** "whatevs": Parse a string literal. */
  def string[_: P]: P[String] = P("\"" ~~/ CharsWhile(stringChars).! ~~ "\"")

  /** "-532": Parse an integer literal, unnormalized. */
  def integer[_: P]: P[Int] = {
    import NoWhitespace._
    ("-".? ~~ CharIn("0-9").rep(1)).!.map(s => s.toInt)
  }
  /** "532": Parse a (nonnegative) natural number literal, unnormalized. */
  def natural[_: P]: P[Int] = {
    import NoWhitespace._
    CharIn("0-9").rep(1).!.map(s => s.toInt)
  }



  //*****************
  // base parsers
  //*****************


  def baseVariable[_: P]: P[BaseVariable] = ident.map(s => Variable(s._1,s._2,Real))
  def diffVariable[_: P]: P[DifferentialSymbol] = P(baseVariable ~ "'").map(DifferentialSymbol(_))
  def variable[_: P]: P[Variable] = P(baseVariable ~ "'".!.?).map{
    case (v,None) => v
    case (v,Some("'")) => DifferentialSymbol(v)
  }

  //*****************
  // term parser
  //*****************

  def term[_: P]: P[Term] = P(signed(summand).flatMap(termRight))

  def termRight[_: P](left: Term): P[Term] =
    (("+" | "-" ~ !">").!./ ~ summand).rep.map(sums =>
      sums.foldLeft(left) {
        case (m1, ("+", m2)) => Plus(m1, m2)
        case (m1, ("-", m2)) => Minus(m1, m2)
        case _ => throw new IllegalStateException()
      }
    )

  def summand[_: P]: P[Term] = P(multiplicand.flatMap(summRight))

  def summRight[_: P](left: Term): P[Term] =
    (("*" | "/").!./ ~ signed(multiplicand)).rep.map(mults =>
      mults.foldLeft(left) {
        case (m1, ("*", m2)) => Times(m1, m2)
        case (m1, ("/", m2)) => Divide(m1, m2)
        case _ => throw new IllegalStateException()
      }
    )

  def multiplicand[_: P]: P[Term] = P(baseTerm.flatMap(multRight))

  def multRight[_: P](left: Term): P[Term] =
    ("^"./ ~ signed(baseTerm)).rep.map(pows => (left +: pows).reduceRight(Power))

  def baseTerm[_: P]: P[Term] = P(
    number./ | dot./ | func | unitFunctional | variable
      // These cuts are safe, because when *formula* enters term we guarantee
      // the first available character is NOT (
      // So the leftmost baseTerm occurrence in a formula always skips this branch :)
      | ("(" ~/ term.flatMap(parenRight))
  )

  // IMPORTANT: Cannot place a cut after ), because (f(||))' may be
  // either a predicational or a functional. See `comparison`
  def parenRight[_: P](left: Term): P[Term] = P(")" ~ "'".!.?).
    map {case None => left case Some("'") => Differential(left) }

  def func[_: P]: P[FuncOf] = P(ident ~~ ("<<" ~/ formula ~ ">>").? ~~ termList).map({case (s,idx,interp,ts) =>
    FuncOf(
      interp match {
        case Some(i) => Function(s,idx,ts.sort,Real,Some(i))
        case None => OpSpec.func(s, idx, ts.sort, Real)
      },
      ts)
  })

  def unitFunctional[_: P]: P[UnitFunctional] = P(ident ~~ space).map({case (s,None,sp) => UnitFunctional(s,sp,Real)})

  /** `-p | p`: possibly signed occurrences of what is parsed by parser `p`, so to `p` or `-p`. */
  def signed[_: P](p: => P[Term]): P[Term] = P(("-".! ~~ !">").? ~ p).map({case (Some("-"),t) => Neg(t) case (None,t) =>t})

  /** (t1,t2,t3,...,tn) parenthesized list of terms */
  def termList[_: P]: P[Term] = P("(" ~~ !"|" ~/ term.rep(sep=","./) ~ ")").
    map(ts => ts.reduceRightOption(Pair).getOrElse(Nothing))

  /** (|x1,x2,x3|) parses a space declaration */
  def space[_: P]: P[Space] = P( "(|" ~ variable.rep(sep=",") ~ "|)" ).
    map(ts => if (ts.isEmpty) AnyArg else Except(ts.to))

  //*****************
  // formula parser
  //*****************

  def formula[_: P] = P(biimplication)

  /* <-> (lowest prec, non-assoc) */
  def biimplication[_: P]: P[Formula] = P(backImplication).flatMap(biimpRight)
  def biimpRight[_: P](left: Formula): P[Formula] =
    P("<->" ~/ backImplication).?.
      map{ case None => left case Some(r) => Equiv(left,r) }

  /* <- (left-assoc) */
  def backImplication[_: P]: P[Formula] = P(implication).flatMap(backImpRight)
  def backImpRight[_: P](left: Formula): P[Formula] =
    P("<-" ~ !">" ~/ implication).rep.map(hyps =>
      hyps.foldLeft(left){case (acc,hyp) => Imply(hyp,acc)}
    )

  /* -> (right-assoc) */
  def implication[_: P]: P[Formula] = P(disjunct).flatMap(impRight)
  def impRight[_: P](left: Formula): P[Formula] =
    P("->" ~/ disjunct).rep.map(concls =>
      (left +: concls).reduceRight(Imply)
    )

  /* | (right-assoc) */
  def disjunct[_: P]: P[Formula] = P(conjunct).flatMap(disjRight)
  def disjRight[_: P](left: Formula): P[Formula] =
    P("|" ~/ conjunct).rep.map(conjs =>
      (left +: conjs).reduceRight(Or)
    )

  /* & (right-assoc) */
  def conjunct[_: P]: P[Formula] = P(baseF).flatMap(conjRight)
  def conjRight[_: P](left: Formula): P[Formula] =
    P("&" ~/ baseF).rep.map(forms =>
      (left +: forms).reduceRight(And)
    )

  /* atomic formulas, constant T/F, quantifiers, modalities */
  def baseF[_: P]: P[Formula] = P(
    "true"./.map(_ => True) | "false"./.map(_ => False)
      | ( ("\\forall"|"\\exists").! ~~/ blank ~/ variable ~/ baseF ).
        map{case ("\\forall",x, f) => Forall(x::Nil, f)
            case ("\\exists",x, f) => Exists(x::Nil, f)}
      | ( (("[".! ~/ program ~ "]".!) | ("<".! ~/ program ~ ">".!)) ~/ baseF ).
        map{case ("[",p,"]", f) => Box(p, f)
            case ("<",p,">", f) => Diamond(p, f)}
      | ("!" ~/ baseF ).map(f => Not(f))
      | "(".!.rep(1)./.map(_.length).flatMap(lPars =>
        NoCut(term.flatMap(comparison(lPars)).flatMap{case(lPars,cmp) => parenFormula(lPars)(cmp)})
          | formula.flatMap(parenFormula(lPars))
        )
      | NoCut(term.flatMap(comparison(0)).map(_._2))
      | unitPredicational | predicational | pred
  )

  /* Given number of left parens unmatched (potentially after building a comparison)
  * build up a formula matching the rest.
   */
  def parenFormula[_: P](lPars: Int)(left: Formula): P[Formula] =
    if (lPars < 0) throw new IllegalArgumentException()
    else if (lPars == 0) Pass(left)
    else
      P( Pass(left).flatMap(conjRight).flatMap(disjRight).flatMap(impRight).flatMap(backImpRight).flatMap(biimpRight) ).
        flatMap(left =>
          // We've tried consuming the rparen as a term, so now we can guarantee
          // it is a formula paren and not a term paren.
          (")" ~/ "'".!.?).
          map{ case None => left case Some("'") => DifferentialFormula(left)}.
          flatMap(parenFormula(lPars-1))
        )

  /* Given number of left parens at the start of the comparison,
  * on success returns remaining left paren count and the comparison*/
  def comparison[_: P](lPars: Int)(left: Term): P[(Int,Formula)] = P(
    (if (lPars > 0)
      parenRight(left).flatMap(multRight).flatMap(summRight).flatMap(termRight).
        flatMap(comparison(lPars - 1))
    else Fail)
    | ((("=" | "!=" | ">=" | ">" | "<=" | "<" ~ !"-").! ~/ term).map {
      case ("=", right) => Equal(left, right)
      case ("!=", right) => NotEqual(left, right)
      case (">=", right) => GreaterEqual(left, right)
      case (">", right) => Greater(left, right)
      case ("<=", right) => LessEqual(left, right)
      case ("<", right) => Less(left, right)
    }.map((lPars, _)))
  )

  def pred[_: P]: P[PredOf] = P(ident ~~ ("<<" ~/ formula ~ ">>").? ~~ termList ~ (!CharIn("+\\-*/^!=><") | &("->" | "<-"))).
    map({case (s,idx,interp,ts) => PredOf(Function(s,idx,ts.sort,Bool,interp), ts)})
  def unitPredicational[_: P]: P[UnitPredicational] = P(ident ~~ space).map({case (s,None,sp) => UnitPredicational(s,sp)})
  def predicational[_: P]: P[PredicationalOf] = P(ident ~~ "{" ~/ formula ~ "}").map({case (s,idx,f) => PredicationalOf(Function(s,idx,Bool,Bool),f)})

  //*****************
  // program parser
  //*****************

  //@todo add .opaque in some places to improve higher-level error quality

  def programSymbol[_: P]: P[AtomicProgram] = P( ident  ~ ";").
    map({case (s,None) => ProgramConst(s) case (s,Some(i)) => throw ParseException("Program symbols cannot have an index: " + s + "_" + i)})
  //@todo could we call Fail(_) instead of throwing a ParseException to retain more context info?
  //@todo combine system symbol and space taboo
  def systemSymbol[_: P]: P[AtomicProgram] = P( ident  ~~ "{|^@" ~/ variable.rep(sep=","./) ~ "|}" ~ ";").map({
    case (s,None,taboo) => SystemConst(s, if (taboo.isEmpty) AnyArg else Except(taboo.to))
    case (s,Some(i),_) => throw ParseException("System symbols cannot have an index: " + s + "_" + i)
  })

  def assign[_: P]: P[Assign] = P( variable ~ ":=" ~/ term ~ ";").map({case (x,t) => Assign(x,t)})
  def assignany[_: P]: P[AssignAny] = P( variable ~ ":=" ~ "*" ~/ ";").map({case x => AssignAny(x)})
  def test[_: P]: P[Test] = P( "?" ~/ formula ~ ";").map(f => Test(f))
  def braceP[_: P]: P[Program] = P( "{" ~ program ~ "}" )
  def odeprogram[_: P]: P[ODESystem] = P( diffProgram ~ ("&" ~/ formula).?).
    map({case (p,f) => ODESystem(p,f.getOrElse(True))})
  def odesystem[_: P]: P[ODESystem] = P( "{" ~ odeprogram ~ "}" ~/ annotation.?).
    map({case (p,None) => p case (p,Some(inv)) => reportAnnotation(p,inv); p})
  def baseP[_: P]: P[Program] = P(
    ( systemSymbol | programSymbol | assignany | assign | test | ifthen
      | "{" ~/ ( (program ~ "}" ~/ ( "*".! ~ annotation.? ).?).map {
            case (p, None) => p
            case (p, Some(("*", None))) => Loop(p)
            case (p, Some(("*", Some(inv)))) => reportAnnotation(p, inv); Loop(p)
          }
        | (odeprogram ~ "}" ~/ annotation.?).map({case (p,None) => p case (p,Some(inv)) => reportAnnotation(p,inv); p})
        )
      ) ~ "^@".!.?).map({case (p,None) => p case (p,Some("^@")) => Dual(p)})

  def repeat[_: P]: P[Program] = P( braceP ~ "*".! ~ annotation.?).
    map({case (p,"*",None) => Loop(p) case (p,"*",Some(inv)) => reportAnnotation(p,inv); Loop(p)})

  /** Parses an annotation */
  def annotation[_: P]: P[Formula] = P("@invariant" ~/ "(" ~/ formula ~ ")")

  def sequence[_: P]: P[Program] = P( (baseP ~/ ";".?).rep(1) ).
    map(ps => ps.reduceRight(Compose))

  def choice[_: P]: P[Program] = P( sequence ~/ ("++" ~/ sequence).rep ).
    map({case (p, ps) => (ps.+:(p)).reduceRight(Choice)})

  //@note macro-expands
  def ifthen[_: P]: P[Program] = P( "if" ~/ "(" ~/ formula ~ ")" ~ braceP ~ ("else" ~/ braceP).? ).
    map({case (f, p, None) => Choice(Compose(Test(f),p), Test(Not(f)))
         case (f, p, Some(q)) => Choice(Compose(Test(f),p), Compose(Test(Not(f)),q))})

  /** program: Parses a dL program. */
  def program[_: P]: P[Program] = P( choice )


  //*****************
  // differential program parser
  //*****************

  def ode[_: P]: P[AtomicODE] = P( diffVariable ~ "=" ~/ term).map({case (x,t) => AtomicODE(x,t)})
  def diffProgramSymbol[_: P]: P[DifferentialProgramConst] = P( ident ~~ odeSpace.?).
    map({case (s, None, None)     => DifferentialProgramConst(s)
         case (s, None, Some(sp)) => DifferentialProgramConst(s,sp)
         case (s, Some(i), _)     => throw ParseException("Differential program symbols cannot have an index: " + s + "_" + i)})
  def atomicDP[_: P]: P[AtomicDifferentialProgram] = P( ode | diffProgramSymbol )

  /** {|x1,x2,x3|} parses a space declaration */
  def odeSpace[_: P]: P[Space] = P("{|" ~ (variable ~ ("," ~/ variable).rep).? ~ "|}").
    map({case Some((t,ts)) => Except((ts.+:(t)).to) case None => AnyArg})


  def diffProduct[_: P]: P[DifferentialProgram] = P( atomicDP ~ ("," ~/ atomicDP).rep ).
    map({case (p, ps) => (ps.+:(p)).reduceRight(DifferentialProduct.apply)})

  /** diffProgram: Parses a dL differential program. */
  def diffProgram[_: P]: P[DifferentialProgram] = P( diffProduct )

  //*****************
  // sequent parser
  //*****************

  /** sequent ::= `aformula1 , aformula2 , ... , aformulan ==>  sformula1 , sformula2 , ... , sformulam`. */
  def sequent[_: P]: P[Sequent] = P( formula.rep(sep=","./) ~ "==>" ~ formula.rep(sep=","./)).
    map({case (ante, succ) => Sequent(ante.to, succ.to)})

  /** sequentList ::= sequent `;;` sequent `;;` ... `;;` sequent. */
  def sequentList[_: P]: P[List[Sequent]] = P( sequent.rep(sep=";;"./ )).map(_.toList)
}

