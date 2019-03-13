(* ::Package:: *)

(* Strategies for continuous invariant generation.

  Copyright 2019, Carnegie Mellon University *)


Needs["Classifier`",FileNameJoin[{Directory[],"Classifier.m"}]] (* Load classifier package from current directory *)
Needs["QualitativeMethods`",FileNameJoin[{Directory[],"QualitativeMethods.m"}]] (* Load qualitative analysis-based invariant generation methods package from current directory *)
Needs["AbstractionPolynomials`",FileNameJoin[{Directory[],"AbstractionPolynomials.m"}]] (* Polynomial sources for qualitative abstraction *)
Needs["PlanarLinear`",FileNameJoin[{Directory[],"PlanarLinear.m"}]]  (* Planar linear system analysis package *)
Needs["Linear`",FileNameJoin[{Directory[],"Linear.m"}]] (* Linear system analysis package *)
Needs["OneDimensional`",FileNameJoin[{Directory[],"OneDimensional.m"}]] (* One-dimensional system analysis package *)
Needs["FirstIntegralMethod`",FileNameJoin[{Directory[],"FirstIntegralMethod.m"}]] (* First integral generation and qualitative abstraction package *)
Needs["MultiLinear`",FileNameJoin[{Directory[],"MultiLinear.m"}]] (* Linear system analysis package *)


BeginPackage["Strategies`"];


RunMethod::usage="Run designated method on a problem"
Pegasus::usafe="Run Pegasus"


Begin["`Private`"]


CheckSemiAlgInclusion[subset_,set_,vars_List]:=Module[{},
TrueQ[Reduce[ForAll[vars, Implies[subset,set]],Reals]]
]


(* STRATEGIES *)


(* Strategy for one-dimensional systems *)
OneDimStrat[problem_List]:=Catch[Module[{pre,f,vars,evoConst,post,invPotential,reachSet},
(* Pattern match fields in the problem *)
{ pre, { f, vars, evoConst }, post } = problem;
(* Attempt to find an invariant by computing the potential function *)
invPotential=RunMethod["OneDimPotential", problem, {}]; 
(* If resulting invariant is sufficient, return it *)
If[CheckSemiAlgInclusion[invPotential,post,vars], Throw[invPotential],
(* Otherwise, construct the reachable set and return *)
reachSet=RunMethod["OneDimReach", problem, {}];
Throw[{reachSet}]]
]]


ProjectAlongVec[S_,vf_List,vars_List]:=Module[{subst,proj},
subst=Map[Apply[Rule,#]&,{vars,vars-vf*PROJECTIONLAMBDA}//Transpose];
proj=S/.subst;
Resolve[Exists[{PROJECTIONLAMBDA},proj&&PROJECTIONLAMBDA>=0],Reals]
]


ConstantStrat[problem_List]:=Catch[Module[{inv,invs,pre,f,vars,evoConst,post},
(* Pattern match fields in the problem *)
{ pre, { f, vars, evoConst }, post } = problem;
Print["CONSTANT STRATEGY"];
(* Project initial set along the constant flow and return the result *)
inv=ProjectAlongVec[pre,f,vars];
Print[inv];
Throw[{inv}]
]]


PlanarLinearStrat[problem_List]:=Catch[Module[{inv,invs,pre,f,vars,evoConst,post,initConnectedComponents,problems},
(* Pattern match fields in the problem *)
{ pre, { f, vars, evoConst }, post } = problem;
Print["PLANAR LINEAR STRATEGY"];
(* Compute the connected components of the initial set  *)
initConnectedComponents=CylindricalDecomposition[pre,vars,"Components"];
(* Treat each initial connected component as a new initial set - separate the problems *)
problems = Map[ {#, {f,vars,evoConst}, post}&, initConnectedComponents];
(* Run the PlanarLinear method on these problems separately *)
invs=Map[RunMethod["Linear", #, {}]&, problems];
(* Combine the results into a disjunction and return *)
inv=If[Length[invs]>1, Throw[{Apply[Or, Map[First[#]&,invs]]}], Throw[invs]]
]]


GeneralLinearStrat[problem_List]:=Catch[Module[{inv,invs,pre,f,vars,evoConst,post,FIs,fiInv,cuts,problems,initConnectedComponents},
(* Pattern match fields in the problem *)
{ pre, { f, vars, evoConst }, post } = problem;
Print["GENERAL LINEAR STRATEGY"];
Print["Trying first integrals first"];
FIs=FirstIntegralMethod`FirstIntegralMethod[problem, RationalsOnly->True, RationalPrecision->3];
If[Length[FIs]>0,
{fiInv, cuts}= QualitativeMethods`DWC[pre, post, { f, vars, evoConst }, FIs, {}];
If[CheckSemiAlgInclusion[fiInv,post,vars], 
Throw[cuts],
Print["First integrals didn't do it. Proceeding to other qualitative methods."]
]];
(* Apply methods for linear systems  *)
initConnectedComponents=CylindricalDecomposition[pre,vars,"Components"];
problems = Map[ {#, {f,vars,evoConst}, post}&, initConnectedComponents];
invs=Map[RunMethod["Linear", #, {}]&, problems];
inv=If[Length[invs]>1, Throw[{Apply[Or, Map[First[#]&,invs]]}], Throw[invs]]
]]


MultiLinearStrat[problem_List]:=Catch[Module[{inv,invs,pre,f,vars,evoConst,post,FIs,fiInv,cuts,},
(* Pattern match fields in the problem *)
{ pre, { f, vars, evoConst }, post } = problem;
Print["MULTI-LINEAR STRATEGY"];
Print["Trying first integrals first"];
FIs=FirstIntegralMethod[problem, RationalsOnly->True, RationalPrecision->3];
If[Length[FIs]>0,
{fiInv, cuts}= QualitativeMethods`DWC[pre, post, { f, vars, evoConst }, FIs, {}];
If[CheckSemiAlgInclusion[fiInv,post,vars], 
Throw[cuts],
Print["First integrals didn't do it. Proceeding to other qualitative methods."]
]];
(* Apply methods for mutilinear systems  *)
inv=RunMethod["Multi-Linear", problem, {}];
Throw[inv]
]]


QualitativeBasic[problem_List]:=Catch[Module[{pre,f,vars,evoConst,post,fiInv,cuts,FIs,aggregate,cutsAggregate,inv},
(* Pattern match fields in the problem *)
{ pre, { f, vars, evoConst }, post } = problem;
Print["BASIC QUALITATIVE STRATEGY (DWC)"];
FIs={};
(*Print["Trying first integrals first"];
FIs=TimeConstrained[ (* Using a 5 second timeout *)
FirstIntegralMethod[problem, RationalsOnly->True, RationalPrecision->3],
5, {}];
If[Length[FIs]>0,
{fiInv,cuts}= QualitativeMethods`DWC[pre, post, { f, vars, evoConst }, FIs, {}];
If[CheckSemiAlgInclusion[fiInv,post,vars], 
Throw[cuts],
Print["First integrals didn't do it. Proceeding to other qualitative methods."]
]];*)

aggregate=evoConst;
cutsAggregate={};
inv=True;
Do[
{inv,cuts}=RunMethod[method,problem,FIs];
If[ TrueQ[Reduce[Implies[inv, post], vars, Reals]], Throw[cuts]];
aggregate=FullSimplify[inv && aggregate];
cutsAggregate=Join[cutsAggregate, cuts];
If[TrueQ[Reduce[Implies[aggregate, post], vars, Reals]], Throw[cutsAggregate]],
{method,{
"DWC-Darboux",
"DWC-Factors-Summands",
"DWC-Factors-RHS", 
"DWC-Factors-RHS-Lie", 
"DWC-Factors-RHS-Product", 
"DWC-Factors-RHS-Lie-Product"}}
];
Throw[QualitativeExtended[{pre, {f, vars, aggregate} ,post}]]
]]


QualitativeExtended[problem_List]:=Catch[Module[{pre,f,vars,evoConst,post,fiInv,FIs,cuts,abstraction,inv},
(* Pattern match fields in the problem *)
{ pre, { f, vars, evoConst }, post } = problem;
Print["EXTENDED QUALITATIVE STRATEGY (DWCL i.e. full abstraction)"];

fiInv=True;
Print["Trying first integrals first"];
FIs=FirstIntegralMethod[problem, RationalsOnly->True, RationalPrecision->3];
If[Length[FIs]>0,
{fiInv,cuts}= QualitativeMethods`DWC[pre, post, { f, vars, evoConst }, FIs, {}];
If[CheckSemiAlgInclusion[fiInv,post,vars], 
Throw[cuts],
Print["First integrals didn't do it. Proceeding to other qualitative methods."]
]];

(* Postcondition and right-hand side factors *)
abstraction=AbstractionPolynomials`PostRHSFactors[problem];
inv=QualitativeMethods`DWCLZR[pre, post, {f,vars,evoConst}, abstraction];
Throw[{inv}]
]]


(* Set righ-hand side of terms to zero *)
ZeroRHS[formula_] := Module[{},formula/.{
Equal[a_,b_]        :>  Equal[a-b,0],
Unequal[a_,b_]      :>  Unequal[a-b,0],
Greater[a_,b_]      :>  Greater[a-b,0],
GreaterEqual[a_,b_] :>  GreaterEqual[a-b,0],
Less[a_,b_]         :>  Less[a-b,0], 
LessEqual[a_,b_]    :>  LessEqual[a-b,0]
}]

GeqToLeq[formula_]:=Module[{}, formula/.{         GreaterEqual[lhs_,rhs_] :>  LessEqual[rhs,lhs]} ] 
GtToLt[formula_]:=Module[{}, formula/.{           Greater[lhs_,rhs_]      :>  Less[rhs,lhs]} ] 
UnequalToLtOrGt[formula_]:=Module[{}, formula/.{  Unequal[lhs_,rhs_]      :>  Or[Less[lhs,rhs] ,Less[rhs,lhs]]} ] 
EqualToLeqAndGeq[formula_]:=Module[{}, formula/.{ Equal[lhs_,rhs_]        :>  And[LessEqual[lhs,rhs] ,LessEqual[rhs,lhs]]} ] 
LeqToLtOrEqual[formula_]:=Module[{}, formula/.{   LessEqual[lhs_,rhs_]    :>  Or[Less[lhs,rhs] ,Equal[rhs,lhs]]} ] 

PreProcess[expression_]:=Module[{},
ZeroRHS[
GeqToLeq[
GtToLt[
LogicalExpand[BooleanMinimize[UnequalToLtOrGt[expression], "DNF"]]
]
]
]
] 


AugmentWithParameters[problem_List]:=Module[{pre,post,f,vars,evoConst,symbols,parameters,newvars,newf},
{ pre, { f, vars, evoConst }, post } = problem;
symbols=Complement[DeleteDuplicates@Cases[{pre, post, f, evoConst},_Symbol,Infinity], {True, False}];
parameters=Complement[symbols, vars];
newvars=Join[vars,parameters];
newf=Join[f,Table[0,{i,Length[parameters]}]];
{ pre, { newf, newvars, evoConst }, post }
]


Pegasus[parametricProb_List]:=Catch[Module[
{problem,pre,f,vars,evoConst,post,preImpliesPost,postInvariant,preInvariant,class,strat,inv,andinv,relaxedInv,invImpliesPost}, 
(* Bring symbolic parameters into the dynamics *)
problem = AugmentWithParameters[parametricProb];
{ pre, { f, vars, evoConst }, post }=problem;

(* Sanity checks *)

preImpliesPost=CheckSemiAlgInclusion[pre, post, vars];
If[ Not[TrueQ[preImpliesPost]], 
Print["Precondition does not imply postcondition! Nothing to do."]; Throw[{{False}, False}], 
Print["Precondition implies postcondition. Proceeding."]];

postInvariant=LZZ`InvS[post, f, vars, evoConst];
If[ TrueQ[postInvariant], 
Print["Postcondition is an invariant! Nothing to do."]; Throw[{{PreProcess[post]},True}], 
Print["Postcondition is not an invariant. Proceeding."]];

preInvariant=LZZ`InvS[pre, f, vars, evoConst];
If[ TrueQ[preInvariant], 
Print["Precondition is an invariant! Nothing to do."]; Throw[{{PreProcess[pre]}, True}], 
Print["Precondition is not an invariant. Proceeding."]];

(* Determine strategies depending on problem classification by pattern matching on {dimension, classes} *)
class=Classifier`ClassifyProblem[problem];
strat = class/.{
{1,CLASSES_List}-> OneDimStrat, 
{dim_,{"Constant"}}-> ConstantStrat, 
(* {2,{"Linear"}}-> GeneralLinearStrat, *)
{dim_,{"Linear"}}-> GeneralLinearStrat, 
(* {dim_,{"Multi-affine"}}-> MultiLinearStrat, *)
{dim_, CLASSES_List}-> QualitativeBasic
};
(* Apply strategy to the problem and return the result *)
inv=strat[problem];

(* Simplify invariant w.r.t. the domain constraint *)
inv=Map[Assuming[evoConst, FullSimplify[#, Reals]]&, inv];

(* Return the invariant without strict inequalities - KeYmaera has trouble with mixed formulas *)
inv=inv/.{Unequal[a_,b_]-> True};
andinv=Apply[And,inv];
relaxedInv=LZZ`InvS[andinv, f, vars, evoConst];
If[ TrueQ[relaxedInv], 
Print["Relaxed invariant is still ok. Proceeding"], 
Print["Relaxed invariant is no longer invariant. Sorry."];Throw[{{True},False}]];

invImpliesPost=CheckSemiAlgInclusion[Apply[And,inv], post, vars];
If[TrueQ[invImpliesPost], Print["Generated invariant implies postcondition. Returning."]; Throw[{inv, True}],
Print["Generated invariant does not imply postcondition. Bad luck; returning what I could find."]; Throw[{inv, False}]]

]]


RunMethod[methodID_String, problem_List, hintPolynomials_List]:=Module[{
 precond=problem[[1]], system=problem[[2]], postcond=problem[[3]]
},
Switch[methodID,
(* QualitativeMethods for one-dimensional systems *)
"OneDimPotential", OneDimensional`OneDimPotential[problem],
"OneDimReach", OneDimensional`OneDimReach[problem],

(* Planar constant systems *)
"PlanarConstant", QualitativeMethods`DWC[precond, postcond, system, Linear`PlanarConstantMethod[precond, postcond, system, RationalsOnly->False, RationalPrecision->3], {}],

(*"PlanarLinear", QualitativeMethods`DWC[precond, postcond, system, PlanarLinear`PlanarLinearMethod[precond, postcond, system]],*)
"Linear", QualitativeMethods`DWC[precond, postcond, system, Linear`LinearMethod[precond, postcond, system, RationalsOnly->False, RationalPrecision->3], {}],
"Multi-Linear", QualitativeMethods`DWC[precond, postcond, system, Union[hintPolynomials,AbstractionPolynomials`PostRHSFactors[problem]], {}],

(*"PlanarLinearSmallest", QualitativeMethods`DWC[precond, postcond, system, PlanarLinear`PlanarLinearMethod[precond, postcond, system], Smallest->False],*)
"LinearSmallest", QualitativeMethods`DWC[precond, postcond, system, Linear`LinearMethod[precond, postcond, system], {}, Smallest->True],

(* QualitativeMethods for non-linear systems based on qualitative analysis and discrete abstraction *)
"DWC-Factors-Summands", QualitativeMethods`DWC[precond, postcond, system, Union[hintPolynomials,AbstractionPolynomials`SummandFactors[problem]], {}],
"DWC-Darboux", QualitativeMethods`DWC[precond, postcond, system, Union[hintPolynomials,AbstractionPolynomials`DarbouxPolynomials[problem]], {}],
"DWC-Factors-RHS", QualitativeMethods`DWC[precond, postcond, system, Union[hintPolynomials,AbstractionPolynomials`PostRHSFactors[problem]], {}],
"DWC-Factors-RHS-Lie", QualitativeMethods`DWC[precond, postcond, system, Union[hintPolynomials,AbstractionPolynomials`PostRHSLieDFactors[problem]], {}],
"DWC-Factors-RHS-Product", QualitativeMethods`DWC[precond, postcond, system, Union[hintPolynomials,AbstractionPolynomials`PostRHSProductFactors[problem]], {}],
"DWC-Factors-RHS-Lie-Product", QualitativeMethods`DWC[precond, postcond, system, Union[hintPolynomials,AbstractionPolynomials`PostRHSLieDProductFactors[problem]], {}],
"DWCL-Factors-RHS-Product", QualitativeMethods`DWCLZR[precond, postcond,system,  AbstractionPolynomials`PostRHSFactors[problem]],
"DWCL-Factors-RHS-Lie-Product", QualitativeMethods`DWCLZR[precond, postcond, system, AbstractionPolynomials`PostRHSLieDFactors[problem]]
]
]


End[]
EndPackage[]
