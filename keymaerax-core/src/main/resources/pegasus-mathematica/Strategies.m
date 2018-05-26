(* ::Package:: *)

Needs["Methods`",NotebookDirectory[]<>"Methods.m"] (* Load invariant generation methods package from current directory *)
Needs["Classifier`",NotebookDirectory[]<>"Classifier.m"] (* Load classifier package from current directory *)
Needs["AbstractionPolynomials`",NotebookDirectory[]<>"AbstractionPolynomials.m"] (* Polynomial sources for qualitative abstraction *)
Needs["PlanarLinear`",NotebookDirectory[]<>"PlanarLinear.m"]  (* Planar linear system analysis package *)
Needs["Linear`",NotebookDirectory[]<>"Linear.m"] (* Linear system analysis package *)
Needs["MultiLinear`",NotebookDirectory[]<>"MultiLinear.m"] (* Linear system analysis package *)


BeginPackage["Strategies`"];


RunMethod::usage="Run designated method on a problem"
Pegasus::usafe="Run Pegasus"


Begin["`Private`"]


CheckSemiAlgInclusion[subset_,set_,vars_List]:=Module[{},
TrueQ[Reduce[ForAll[vars, Implies[subset,set]],Reals]]
]


(* STRATEGIES *)


OneDimStrat[problem_List]:=Catch[Module[{},
(* Pattern match fields in the problem *)
{ pre, { f, vars, evoConst }, post } = problem;
(* Apply one-dimensional potential method *)
invPotential=RunMethod["OneDimPotential", problem];
(* If resulting invariant is sufficient, return it *)
If[CheckSemiAlgInclusion[invPotential,post,vars], Throw[invPotential],
(* Otherwise, construct the reachable set and return *)
reachSet=RunMethod["OneDimReach", problem];
Throw[reachSet]]
]]


PlanarConstantStrat[problem_List]:=Catch[Module[{inv,invs},
(* Pattern match fields in the problem *)
{ pre, { f, vars, evoConst }, post } = problem;
Print["PLANAR CONSTANT STRATEGY"];
(* Compute the connected components of the initial set  *)
initConnectedComponents=CylindricalDecomposition[pre,vars,"Components"];
(* Treat each initial connected component as a new initial set - separate the problems *)
problems = Map[ {#, {f,vars,evoConst}, post}&, initConnectedComponents];
(* Run the method on these problems separately *)
invs=Map[RunMethod["PlanarConstant", #]&, problems];
(* Combine the results into a disjunction and return *)
inv=If[Length[invs]>1, Apply[Or, invs], invs[[1]]];
(* Fall back to projection if result insufficient *)
If[CheckSemiAlgInclusion[inv,post,vars], Throw[inv],
invproj=ProjectAlongVec[pre,f,vars];
Throw[invproj]]
]]


ProjectAlongVec[S_,vf_List,vars_List]:=Module[{},
subst=Map[Apply[Rule,#]&,{vars,vars-vf*PROJECTIONLAMBDA}//Transpose];
proj=S/.subst;
Resolve[Exists[{PROJECTIONLAMBDA},proj&&PROJECTIONLAMBDA>=0],Reals]
]


ConstantStrat[problem_List]:=Catch[Module[{inv,invs},
(* Pattern match fields in the problem *)
{ pre, { f, vars, evoConst }, post } = problem;
Print["CONSTANT STRATEGY"];
(* Project initial set along the constant flow and return the result *)
inv=ProjectAlongVec[pre,f,vars];
]]


PlanarLinearStrat[problem_List]:=Catch[Module[{inv,invs},
(* Pattern match fields in the problem *)
{ pre, { f, vars, evoConst }, post } = problem;
Print["PLANAR LINEAR STRATEGY"];
(* Compute the connected components of the initial set  *)
initConnectedComponents=CylindricalDecomposition[pre,vars,"Components"];
(* Treat each initial connected component as a new initial set - separate the problems *)
problems = Map[ {#, {f,vars,evoConst}, post}&, initConnectedComponents];
(* Run the PlanarLinear method on these problems separately *)
invs=Map[RunMethod["Linear", #]&, problems];
(* Combine the results into a disjunction and return *)
inv=If[Length[invs]>1, Throw[Apply[Or, invs]], Throw[invs[[1]]]]
]]


GeneralLinearStrat[problem_List]:=Catch[Module[{inv,invs},
(* Pattern match fields in the problem *)
{ pre, { f, vars, evoConst }, post } = problem;
Print["GENERAL LINEAR STRATEGY"];
(* Apply methods for linear systems  *)
initConnectedComponents=CylindricalDecomposition[pre,vars,"Components"];
problems = Map[ {#, {f,vars,evoConst}, post}&, initConnectedComponents];
invs=Map[RunMethod["Linear", #]&, problems];
inv=If[Length[invs]>1, Throw[Apply[Or, invs]], Throw[invs[[1]]]]
]]


MultiLinearStrat[problem_List]:=Catch[Module[{inv,invs},
(* Pattern match fields in the problem *)
{ pre, { f, vars, evoConst }, post } = problem;
Print["MULTI-LINEAR STRATEGY"];
(* Apply methods for mutilinear systems  *)
inv=RunMethod["Multi-Linear", problem];
Throw[inv]
]]


QualitativeBasic[problem_List]:=Catch[Module[{},
(* Pattern match fields in the problem *)
{ pre, { f, vars, evoConst }, post } = problem;
Print["BASIC QUALITATIVE STRATEGY (DWC)"];
aggregate=evoConst;
inv=True;
Do[
inv=RunMethod[method,problem];
If[ TrueQ[Reduce[Implies[inv, post], vars, Reals]], Throw[inv]];
aggregate=Simplify[inv && aggregate];
If[TrueQ[Reduce[Implies[aggregate, post], vars, Reals]], Throw[aggregate]],
{method,{"DWC-Factors-RHS", "DWC-Factors-RHS-Lie", "DWC-Factors-RHS-Product", "DWC-Factors-RHS-Lie-Product"}}
];
Throw[QualitativeExtended[{pre, {f, vars, aggregate} ,post}]]
]]


QualitativeExtended[problem_List]:=Catch[Module[{},
(* Pattern match fields in the problem *)
{ pre, { f, vars, evoConst }, post } = problem;
Print["EXTENDED QUALITATIVE STRATEGY (DWCL i.e. full abstraction)"];
aggregate=evoConst;
inv=True;
Do[
inv=RunMethod[method,problem];
If[ TrueQ[Reduce[Implies[inv, post], vars, Reals]], Throw[inv]];
aggregate=Simplify[inv && aggregate];
If[TrueQ[Reduce[Implies[aggregate, post], vars, Reals]], Throw[aggregate]],
{method,{"DWCL-Factors-RHS-Product"}}
];
Throw[aggregate]
]]


Pegasus[problem_List]:=Catch[Module[{}, { pre, { f, vars, evoConst }, post } = problem;

(* Sanity checks *)

preIsPost=SameQ[Resolve[pre,vars], Resolve[post,vars]];
If[ TrueQ[preIsPost], 
Print["Precondition is the same as the postcondition! Just check postcondition for invariance."]; Throw[post], 
Print["Precondition is not equal to the postcondition. Proceeding."]];

zeroVF=AllTrue[f, Expand[#]==0&];
If[ TrueQ[ZeroVF], 
Print["Zero vector field."]; Throw[pre], 
Print["Non-zero vector field. Proceeding."]];

preImpliesPost=CheckSemiAlgInclusion[pre, post, vars];
If[ Not[TrueQ[preImpliesPost]], 
Print["Precondition does not imply postcondition! Nothing to do."]; Throw[False], 
Print["Precondition implies postcondition. Proceeding."]];

postInvariant=Methods`InvS[post, f, vars, evoConst];
If[ TrueQ[postInvariant], 
Print["Postcondition is an invariant! Nothing to do."]; Throw[post], 
Print["Postcondition is not an invariant. Proceeding."]];

preInvariant=Methods`InvS[pre, f, vars, evoConst];
If[ TrueQ[preInvariant], 
Print["Precondition is an invariant! Nothing to do."]; Throw[pre], 
Print["Pretcondition is not an invariant. Proceeding."]];

(* Determine strategies depending on problem classification by pattern matching on {dimension, classes} *)
class=Classifier`ClassifyProblem[problem];
strat = class/.{
{1,CLASSES_List}-> OneDimStrat, 
{2,{"Constant"}}-> PlanarConstantStrat, 
{dim_,{"Constant"}}-> ConstantStrat, 
{2,{"Linear"}}-> PlanarLinearStrat, 
{dim_,{"Linear"}}-> GeneralLinearStrat, 
{dim_,{"Multi-affine"}}-> MultiLinearStrat, 
{dim_, CLASSES_List}-> QualitativeBasic
};
(* Apply strategy to the problem and return the result *)
inv=strat[problem];
(* Return the invariant without strict inequalities - KeYmaera has trouble with mixed formulas *)
inv=inv/.{Greater[a_,b_]->GreaterEqual[a,b],Less[a_,b_]->LessEqual[a,b],Unequal[a_,b_]-> True};

invImpliesPost=CheckSemiAlgInclusion[inv, post, vars];
If[TrueQ[invImpliesPost], Print["Generated invariant implies postcondition. Returning."]; Throw[{inv, True}],
Print["Generated invariant does not imply postcondition. Bad luck; returning what I could find."]; Throw[{inv, False}]]

]]


RunMethod[methodID_String, problem_List]:=Module[{
 precond=problem[[1]], system=problem[[2]], postcond=problem[[3]]
},
Switch[methodID,
(* Methods for one-dimensional systems *)
"OneDimPotential", Methods`OneDimPotential[problem],
"OneDimReach", Methods`OneDimReach[problem],

(* Planar constant systems *)
"PlanarConstant", Methods`DWC[precond, postcond, system, Linear`PlanarConstantMethod[precond, postcond, system, RationalsOnly->True, RationalPrecision->3]],

(*"PlanarLinear", Methods`DWC[precond, postcond, system, PlanarLinear`PlanarLinearMethod[precond, postcond, system]],*)
"Linear", Methods`DWC[precond, postcond, system, Linear`LinearMethod[precond, postcond, system, RationalsOnly->True, RationalPrecision->3]],
"Multi-Linear", Methods`DWC[precond, postcond, system, MultiLinear`MultiLinearMethod[precond, postcond, system]],

(*"PlanarLinearSmallest", Methods`DWC[precond, postcond, system, PlanarLinear`PlanarLinearMethod[precond, postcond, system], Smallest->False],*)
"LinearSmallest", Methods`DWC[precond, postcond, system, Linear`LinearMethod[precond, postcond, system], Smallest->True],

(* Methods for non-linear systems based on qualitative analysis and discrete abstraction *)
"DWC-Factors-RHS", Methods`DWC[precond, postcond, system, AbstractionPolynomials`PostRHSFactors[problem]],
"DWC-Factors-RHS-Lie", Methods`DWC[precond, postcond, system, AbstractionPolynomials`PostRHSLieDFactors[problem]],
"DWC-Factors-RHS-Product", Methods`DWC[precond, postcond, system, AbstractionPolynomials`PostRHSProductFactors[problem]],
"DWC-Factors-RHS-Lie-Product", Methods`DWC[precond, postcond, system, AbstractionPolynomials`PostRHSLieDProductFactors[problem]],
"DWCL-Factors-RHS-Product", Methods`DWCLZR[precond, postcond,system,  AbstractionPolynomials`PostRHSFactors[problem]],
"DWCL-Factors-RHS-Lie-Product", Methods`DWCLZR[precond, postcond, system, AbstractionPolynomials`PostRHSLieDFactors[problem]]
]
]


End[]
EndPackage[]