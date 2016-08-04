package org.allenai.iqclid.z3

import com.microsoft.z3
import com.microsoft.z3.Symbol
import com.microsoft.z3._
import org.allenai.iqclid.z3.ThreadSafeDependencies.Z3Module

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/** various relevant status values after the SMT program is solved */
sealed trait SmtStatus
case object SmtSatisfiable extends SmtStatus { override def toString = "Satisfiable" }
case object SmtUnsatisfiable extends SmtStatus { override def toString = "Unsatisfiable" }
case class SmtUnknown(reason: String) extends SmtStatus {
  override def toString = s"Unknown, reason: $reason"
}

/** A generic SMT interface to the Z3 SMT engine. Creating a new instance of this class for each
  * Eulogy program will ensure a clear internal Z3 state and thread safety.
  *
  * @param isIntegerProgram hack to enable the par solver only for integer programs. We don't know
  *                         how to wrap the default solver in a tactic sequence.
  */
class Z3Interface(z3Module: Z3Module, isIntegerProgram: Boolean) {

  /** z3 context. expressions are created here as pure data. */
  private val ctx: Context = new Context

  /** Bounded integers solver.
    * From http://rise4fun.com/Z3/tutorialcontent/strategies.
    * Does not appear to work for a simple primality check problem.
    */
  val boundedSolver = {
    val params = ctx.mkParams()
    params.add(":arith_lhs", true)
    params.add(":som", true)
    ctx.`then`(
      ctx.usingParams(ctx.mkTactic("simplify"), params),
      ctx.mkTactic("normalize-bounds"),
      ctx.mkTactic("lia2pb"),
      ctx.mkTactic("pb2bv"),
      ctx.mkTactic("bit-blast"),
      ctx.mkTactic("sat")
    ).getSolver
  }

  /** Default z3 solver */
  private val defaultSolver = ctx.mkSolver()

  /** Z3 solver with a few different random seeds and explicit simplification.
    * See
    *   http://rise4fun.com/Z3/tutorialcontent/strategies
    *   http://stackoverflow.com/questions/14220826/optimising-z3-input
    *
    * Runs several different random seeds in parallel:
    * a. Increases the chances a solution will be found.
    * b. Increases the chances a solution will be found fast.
    */
  val integerSolver = ctx.parOr(
    (1 to 8).map {
      i =>
        val params = ctx.mkParams()
        params.add(":random_seed", i)
        params.add(":timeout", 4000)
        ctx.`then`(
          ctx.mkTactic("simplify"),
          ctx.mkTactic("elim-term-ite"),
          ctx.mkTactic("solve-eqs"),
          ctx.usingParams(ctx.mkTactic("smt"), params)
        )
    }: _*
  ).getSolver

  /** Z3 solver. assertions are added here one by one, until the full problem is formulated.
    * Picking the right solver is a bit of a dark art.
    * See http://stackoverflow.com/questions/35706358/what-is-z3s-default-solver/35706710#35706710
    */
  private val solver = if (isIntegerProgram) {
    integerSolver
  } else {
    // Consider using "qfnra-nlsat", which is the tactic for nonlinear reals.
    defaultSolver
  }

  /** an internal container to collect all auxiliary "denominator must be non-zero" constraints */
  private val nonZeroDenominatorConstraints = ArrayBuffer.empty[BoolExpr]

  /** Standard sorts. */
  val boolSort = ctx.mkBoolSort().asInstanceOf[Sort]
  val intSort = ctx.mkIntSort().asInstanceOf[Sort]

  /** floating point sort method with 16-bit precision */
  val fpSort = ctx.mkFPSort16()

  /** floating point rounding method */
  private val fpRoundingMethod = ctx.mkFPRoundNearestTiesToAway()

  /** internal map from Boolean constant to the corresponding Z3 entity */
  private val boolConstMap = mutable.HashMap.empty[Boolean, BoolExpr]

  /** internal map from integer constant to the corresponding Z3 entity */
  private val intConstMap = mutable.HashMap.empty[Int, IntNum]

  /** internal map from real constant's string representation to the corresponding Z3 entity */
  private val realConstMap = mutable.HashMap.empty[String, RatNum]

  /** internal map from floating point constant to the corresponding Z3 entity */
  private val fpConstMap = mutable.HashMap.empty[Double, FPNum]

  /** internal map from Boolean variable name to the corresponding Z3 variable */
  private val boolVarMap = mutable.HashMap.empty[String, BoolExpr]

  /** internal map from integer variable name to the corresponding Z3 variable */
  private val intVarMap = mutable.HashMap.empty[String, IntExpr]

  /** internal map from real valued variable name to the corresponding Z3 variable */
  private val realVarMap = mutable.HashMap.empty[String, RealExpr]

  /** internal map from floating point variable name to the corresponding Z3 variable */
  private val fpVarMap = mutable.HashMap.empty[String, FPExpr]

  /** create a Boolean constant */
  def mkBoolConst(b: Boolean): BoolExpr = boolConstMap.getOrElseUpdate(b, ctx.mkBool(b))

  /** create an integer constant */
  def mkIntConst(i: Int): IntNum = intConstMap.getOrElseUpdate(i, ctx.mkInt(i))

  /** create a real valued constant from an integer */
  def mkRealConst(i: Int): RatNum = realConstMap.getOrElseUpdate(i.toString, ctx.mkReal(i))

  /** create a real valued constant from a fraction of two integers */
  def mkRealConst(n: Int, d: Int): RatNum = realConstMap.getOrElseUpdate(s"$n/$d", ctx.mkReal(n, d))

  /** create a real valued constant from a double; Z3 uses string representation of the double */
  def mkRealConst(r: Double): RatNum = {
    val rAsString = r.toString
    realConstMap.getOrElseUpdate(rAsString, ctx.mkReal(rAsString))
  }

  /** create a floating point constant from a double */
  def mkFPConst(r: Double): FPNum = fpConstMap.getOrElseUpdate(r, ctx.mkFP(r, fpSort))

  /** create a boolean variable from a string; strangely, the Z3 method is called mkBoolConst */
  def mkBoolVar(s: String): BoolExpr = boolVarMap.getOrElseUpdate(s, ctx.mkBoolConst(s))

  /** create an integer variable from a string; strangely, the Z3 method is called mkIntConst */
  def mkIntVar(s: String): IntExpr = {
    val x = ctx.mkIntConst(s)
    intVarMap.getOrElseUpdate(s, x)
    x
  }

  /** create an bounded integer variable from a string. */
  def mkIntVar(s: String, intMin: Int, intMax: Int): IntExpr = {
    val x = ctx.mkIntConst(s)
    intVarMap.getOrElseUpdate(s, x)
    solver.add(ctx.mkAnd(ctx.mkLe(x, ctx.mkInt(intMax)), ctx.mkGe(x, ctx.mkInt(intMin))))
    x
  }

  /** create a real valued variable from a string; strangely, the Z3 method is called mkRealConst */
  def mkRealVar(s: String): RealExpr = realVarMap.getOrElseUpdate(s, ctx.mkRealConst(s))

  /** create a floating point variable from a string; strangely, the Z3 method is called mkConst */
  def mkFPVar(s: String): FPExpr = {
    fpVarMap.getOrElseUpdate(s, ctx.mkConst(s, fpSort).asInstanceOf[FPExpr])
  }

  def mkFuncDecl(name: String, sorts: Array[Sort], result: Sort) = {
    ctx.mkFuncDecl(name, sorts, result)

  }

  /** apply a function to a set of arguments. */
  def mkApply(f: FuncDecl, args: Expr*): Expr = {
    ctx.mkApp(f, args: _*)
  }

  class DefineFun(args: Seq[String], sorts: Seq[String], resultSort: String, body: String) {
    val decls = args
      .zip(sorts)
      .map {
        case (a, s) =>
          s"(declare-fun $a () $s)"
      }
      .mkString("\n")
    val text =
      s"""$decls
         |(assert $body)
       """.stripMargin
    val args2 = args
      .zip(sorts)
      .map {
        case (a, "Int") =>
          ctx.mkConst("x", intSort)
        case (a, "Bool") =>
          ctx.mkConst("x", intSort)
        case (a, s) =>
          throw new Exception(s"Bad arg/sort $a/$s")
      }
    val expr: Expr = parseBool(text)

    def substitute(exprs: Seq[Expr]): Expr = {
      args2.zip(exprs).foldLeft(expr) {
        case (expr, (a, e)) =>
          expr.substitute(a, e)
      }
    }
  }

  private def applyToPairs(xs: Seq[Expr], f: (Expr, Expr) => BoolExpr): BoolExpr = {
    require(xs.size >= 2)
    val pairs = xs.sliding(2).toSeq
    val cons: Seq[BoolExpr] = pairs map { case Seq(x, y) => f(x, y) }
    ctx.mkAnd(cons: _*)
  }

  private def applyToArithPairs(
    xs: Seq[ArithExpr], f: (ArithExpr, ArithExpr) => BoolExpr
  ): BoolExpr = {
    require(xs.size >= 2)
    val pairs = xs.sliding(2).toSeq
    val cons: Seq[BoolExpr] = pairs map { case Seq(x, y) => f(x, y) }
    ctx.mkAnd(cons: _*)
  }

  private def applyToFPPairs(xs: Seq[FPExpr], f: (FPExpr, FPExpr) => BoolExpr): BoolExpr = {
    require(xs.size >= 2)
    val pairs = xs.sliding(2).toSeq
    val cons: Seq[BoolExpr] = pairs map { case Seq(x, y) => f(x, y) }
    ctx.mkAnd(cons: _*)
  }

  /** x = y = ... */
  def mkEq(xs: Seq[Expr]): BoolExpr = applyToPairs(xs, (x: Expr, y: Expr) => ctx.mkEq(x, y))

  /** the zero constant */
  private val zeroConst = mkRealConst(0)

  /** x == 0 */
  def mkIsZero(x: ArithExpr): BoolExpr = mkEq(Seq(x, zeroConst))

  /** x != 0 */
  def mkIsNonZero(x: ArithExpr): BoolExpr = ctx.mkNot(mkIsZero(x))

  /** x1 < x2 < ... */
  def mkLt(xs: Seq[ArithExpr]): BoolExpr = {
    applyToArithPairs(xs, (x: ArithExpr, y: ArithExpr) => ctx.mkLt(x, y))
  }

  /** x1 < x2 < ... */
  def mkFPLt(xs: Seq[FPExpr]): BoolExpr = {
    applyToFPPairs(xs, (x: FPExpr, y: FPExpr) => ctx.mkFPLt(x, y))
  }

  /** x <= y <= ... */
  def mkLe(xs: Seq[ArithExpr]): BoolExpr = {
    applyToArithPairs(xs, (x: ArithExpr, y: ArithExpr) => ctx.mkLe(x, y))
  }

  /** x <= y <= ... */
  def mkFPLe(xs: Seq[FPExpr]): BoolExpr = {
    applyToFPPairs(xs, (x: FPExpr, y: FPExpr) => ctx.mkFPLEq(x, y))
  }

  /** x > y > ... */
  def mkGt(xs: Seq[ArithExpr]): BoolExpr = {
    applyToArithPairs(xs, (x: ArithExpr, y: ArithExpr) => ctx.mkGt(x, y))
  }

  /** x > y > ... */
  def mkFPGt(xs: Seq[FPExpr]): BoolExpr = {
    applyToFPPairs(xs, (x: FPExpr, y: FPExpr) => ctx.mkFPGt(x, y))
  }

  /** x >= y >= ... */
  def mkGe(xs: Seq[ArithExpr]): BoolExpr = {
    applyToArithPairs(xs, (x: ArithExpr, y: ArithExpr) => ctx.mkGe(x, y))
  }

  /** x >= y >= ... */
  def mkFPGe(xs: Seq[FPExpr]): BoolExpr = {
    applyToFPPairs(xs, (x: FPExpr, y: FPExpr) => ctx.mkFPGEq(x, y))
  }

  /** x AND y AND ... */
  def mkAnd(xs: Seq[BoolExpr]): BoolExpr = ctx.mkAnd(xs: _*)

  /** x OR y OR ... */
  def mkOr(xs: Seq[BoolExpr]): BoolExpr = ctx.mkOr(xs: _*)

  /** NOT x */
  def mkNot(x: BoolExpr): BoolExpr = ctx.mkNot(x)

  /** x IMPLIES y */
  def mkImplies(x: BoolExpr, y: BoolExpr): BoolExpr = ctx.mkImplies(x, y)

  /** x + y + ... */
  def mkAdd(xs: Seq[ArithExpr]): ArithExpr = ctx.mkAdd(xs: _*)

  /** x + y */
  def mkAdd(x: FPExpr, y: FPExpr): FPExpr = ctx.mkFPAdd(fpRoundingMethod, x, y)

  /** x - y */
  def mkSub(x: ArithExpr, y: ArithExpr): ArithExpr = ctx.mkSub(x, y)

  /** x - y */
  def mkSub(x: FPExpr, y: FPExpr): FPExpr = ctx.mkFPSub(fpRoundingMethod, x, y)

  /** x * y * ... */
  def mkMul(xs: Seq[ArithExpr]): ArithExpr = ctx.mkMul(xs: _*)

  /** x * y */
  def mkMul(x: FPExpr, y: FPExpr): FPExpr = ctx.mkFPMul(fpRoundingMethod, x, y)

  /** x / y */
  def mkDiv(x: ArithExpr, y: ArithExpr): ArithExpr = {
    // if y is not a numeric constant, Z3 appears to set y = 0 unless explicitly prohibited
    if (!y.isNumeral) nonZeroDenominatorConstraints += mkIsNonZero(y)
    ctx.mkDiv(x, y)
  }

  /** x / y */
  def mkDiv(x: FPExpr, y: FPExpr): FPExpr = ctx.mkFPDiv(fpRoundingMethod, x, y)

  /** x ^^ y (x to the power of y) */
  def mkPower(x: ArithExpr, y: ArithExpr): ArithExpr = ctx.mkPower(x, y)

  /** x ^^ y (x to the power of y) */
  def mkMod(x: IntExpr, y: IntExpr): ArithExpr = ctx.mkMod(x, y)

  /** - x (negative x) */
  def mkNeg(x: ArithExpr): ArithExpr = ctx.mkUnaryMinus(x)

  /** |x| (absolute value of x) */
  def mkAbs(x: ArithExpr): ArithExpr = {
    ctx.mkITE(mkGe(Seq(x, zeroConst)), x, mkNeg(x)).asInstanceOf[ArithExpr]
  }

  /** |x| (absolute value of x) */
  def mkAbs(x: FPExpr): FPExpr = ctx.mkFPAbs(x)

  /** sqrt(x) (square root of x) */
  def mkSqrt(x: FPExpr): FPExpr = ctx.mkFPSqrt(fpRoundingMethod, x)

  /** add a constraint to the solver; constraint = Boolean expression implicitly asserted as true */
  def add(c: BoolExpr): Unit = solver.add(c)

  /** add constraints to the solver; constraint = Boolean expression implicitly asserted as true */
  def add(cs: Seq[BoolExpr]): Unit = solver.add(cs: _*)

  def parseBool(text: String): BoolExpr = {
    val sortNames = Array[Symbol]()
    val sorts = Array[Sort]()
    val declNames = Array[Symbol]()
    val decls = Array[FuncDecl]()
    ctx.parseSMTLIB2String(text, sortNames, sorts, declNames, decls)
  }

  /** get all auxiliary constraints */
  def getAuxConstraints: Seq[BoolExpr] = nonZeroDenominatorConstraints

  /** check SMT program for satisfiability */
  def check(): SmtStatus = {
    println("checking Z3 program for satisfiability")
    println(s"SMT program:\n${solver.toString}")
    // Consider uncommenting the next line. See:
    // http://stackoverflow.com/questions/15806141/
    //   keep-getting-unknown-result-with-pattern-usage-in-smtlib-v2-input
    // solver.push()
    solver.check() match {
      case Status.SATISFIABLE => SmtSatisfiable
      case Status.UNSATISFIABLE => SmtUnsatisfiable
      case Status.UNKNOWN => SmtUnknown(solver.getReasonUnknown)
      case _ => throw new IllegalStateException("Unknown Z3 solver state")
    }
  }

  /** extract a solution (aka, a model) found by Z3; throws an exception if check() has not been
    * called earlier or no solution exists; variable values are returned as strings where decimal
    * numbers have the given precision
    */
  def extractModel(precision: Int): Map[String, String] = {
    val model = solver.getModel
    println("solution extracted: " + model.toString)
    // extract variable assignment in the solution found
    val solution = boolVarMap.mapValues(model.evaluate(_, false)) ++
      intVarMap.mapValues(model.evaluate(_, false)) ++
      realVarMap.mapValues(model.evaluate(_, false))
    // convert solution values to decimal strings
    solution.mapValues(toDecimalString(_, precision)).toMap
  }

  /** convert Expr value, which could be an IntNum, RatNum, or FPNum, into a decimal value string;
    * the built-in toDecimalString method in RatNum can produce output such as 4.6666666666666666?,
    * so check for '?' at the end and drop if it is present
    */
  private def toDecimalString(expr: Expr, precision: Int): String = {
    expr match {
      case b: BoolExpr => b.toString
      case i: IntNum => i.toString
      case r: RatNum =>
        val rStr = r.toDecimalString(precision)
        if (rStr.last == '?') rStr.dropRight(1) else rStr
      case f: FPNum => f.toString
      case _ => "satisfiable" // TODO: do something better than this: augment engine API to support .isSatisfiable()
    }
  }
}