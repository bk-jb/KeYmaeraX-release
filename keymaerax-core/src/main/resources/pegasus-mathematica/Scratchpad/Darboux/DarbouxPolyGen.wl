(* ::Package:: *)

BeginPackage["DarbouxPolyGen`"];


(* Version 1.1, with minor optimisations *)
DbxNaive::usage=
"DbxNaive[degree_Integer, vf_List, vars_List] 
A naive implementation for generating Darboux polynomials for the polynomial vector field 'vf', up to a given finite poylnomial degree 'deg'.";

ManPS1::usage=
"ManPS[degree_Integer, vf_List, vars_List]
An implementation of Y.K. Man's algorithm for Darboux polynomial generation (up to given degree) for a polynomial vector field 'vf'.
See algorithm ps_1 in Y.K. Man 'Computing Closed Form Solutions of First Order ODEs Using the Prelle-Singer Procedure', J. Symb. Comput., 1993.";

ManPS2::usage=
"ManPS2[degree_Integer, vf_List, vars_List] 
An implementation of Y.K. Man's algorithm for Darboux polynomial generation (up to given degree) for a polynomial vector field 'vf'.
See alorithm new_ps_1 in Y.K. Man 'Computing Closed Form Solutions of First Order ODEs Using the Prelle-Singer Procedure', J. Symb. Comput., 1993.";

MinRank::usage="MinRank[matrix,symbols] Minimizes the rank of a symbolic matrix";

MatringeCompleteDbx::usage="MatringeCompleteDbx[degree_Integer, vf_List, vars_List] Implements a complete version of the algorithm from Matringe, Moura & Rebiha (SAS'10, TCS'15)";


Begin["`Private`"];


(* Computing the maximal total polynomial degree of a given polynomial P *)
PolynomDegree[P_]:=Module[{},
Max[Map[Apply[Plus, #]&,Map[#[[1]]&,CoefficientRules[P]]]]
]


PolynomDegree[P_, vars_List]:=Module[{},
Max[Map[Apply[Plus, #]&,Map[#[[1]]&,CoefficientRules[P, vars]]]]
]


DbxNaive[deg_Integer,vf_List,vars_List]:=Catch[Module[
{monbas, coeffs, template, LieD, MonBas, cofactCoeffs, 
cofactBasis, cofactTemplate, Lftemplate, Lfdeg, lhs ,sol,
 problem, monomialOrder,
(* Maximum total polynomial degree of the vector field *)
m=Max[Map[PolynomDegree[#,vars]&,vf]]},
If[deg<=0, Throw[0]];

(* Compute monomial basis with given monomial order *)
monomialOrder="DegreeLexicographic";
MonBas[maxdeg_]:=Map[#/CoefficientRules[#][[1]][[2]]&,MonomialList[ (Plus @@ Join[vars,{1}])^maxdeg , vars, monomialOrder]];
monbas=MonBas[deg];

(* Compute the symbolic coefficients of the polynomial template *)
coeffs=Table[Symbol["COEFF"<>ToString[i]],{i,1,Length[monbas]}];
template=coeffs.monbas;

(* Lie derivative of template *)
LieD[p_]:=Grad[p,vars].vf;
Lftemplate=LieD[template];
Lfdeg=PolynomDegree[Lftemplate,vars];

(* Set maximum degree of the cofactor template to be at most m-1 *)
cofactBasis=MonBas[m-1];
cofactCoeffs=Table[Symbol["COFACTCOEFF"<>ToString[i]],{i,1,Length[cofactBasis]}];
cofactTemplate=cofactCoeffs.cofactBasis;

(* Equate coefficients of Lf(p)-q*p to zero and solve the nonlinear system over Complexes *)
problem=Map[#==0&,DeleteDuplicates@(#[[2]]&/@CoefficientRules[Lftemplate - cofactTemplate*template,vars])];
sol=Solve[problem, Join[cofactCoeffs,coeffs], Complexes];
Throw[Select[ (* Cleanup: return only non-numeric factors, without any duplicates *)
   Map[FactorList, 
    ((Map[Grad[#,coeffs]&,(template/.sol)])//Flatten//DeleteDuplicates)
    ]//Flatten//DeleteDuplicates,
 Not[NumericQ[#]]&]];
]]


ManPS1Alt[deg_Integer,vf_List,vars_List]:=Catch[Module[
{monbas, moncoeffs, monictemplate, LieD, MonBas, cofactCoeffs, cofactBasis, 
cofactTemplate, Lftemplate, Lfdeg, lhs ,sol, problem, LT,n,eqns,feqns,geqns,
geqnsol,feqnsol,Sfg, monomialOrder,
(* Maximum total polynomial degree of the vector field *)
m=Max[Map[PolynomDegree[#,vars]&,vf]]},
If[deg<=0, Throw[0]];

(* Fix monomial order *)
monomialOrder="DegreeLexicographic";
(* Leading Term computation *)
LT[p_]:=LT[p]=FromCoefficientRules[{CoefficientRules[p,vars, monomialOrder][[1]]}, vars];
(* Lie derivative of template *)
LieD[p_]:=LieD[p]=Grad[p,vars].vf;

(* Compute monomial basis w.r.t. the fixed monomial order *)
MonBas[maxdeg_]:=MonBas[maxdeg]=Map[#/CoefficientRules[#][[1]][[2]]&,MonomialList[ (Plus @@ Join[vars,{1}])^maxdeg , vars, monomialOrder]];
monbas=MonBas[1];
(* Final solution set is initially empty *)
Sfg={};
Do[
monbas=MonBas[k];
(* Compute monic polynomial templates *)
moncoeffs=Join[Table[Symbol["COEFF"<>ToString[i]],{i,1,Length[monbas]-j}], {1}, Table[0,{i,1,j-1}]];
monictemplate=moncoeffs.Reverse[monbas];
(* Compute Lie deriovative of the monic template *)
Lftemplate=LieD[monictemplate];
(* If LT(p) divides LT(D(p)) *)
If[TrueQ[PolynomialReduce[LT[Lftemplate],LT[monictemplate],vars][[2]]==0], 
n=PolynomDegree[Lftemplate,vars]-PolynomDegree[monictemplate,vars];
If[n<0, n=0];
(* Create a parametric cofactor of degree n *)
cofactBasis=MonBas[n];
cofactCoeffs=Table[Symbol["COFACTCOEFF"<>ToString[i]],{i,1,Length[cofactBasis]}];
cofactTemplate=cofactCoeffs.cofactBasis;
(* Bring everything in Lfp = q*p to the right hand side and collect the coefficients *)
eqns=DeleteDuplicates@(#[[2]]&/@CoefficientRules[cofactTemplate*monictemplate -Lftemplate,vars]);
(* Collect coefficients of those monomial terms that are generated by LT(p)*q *)
geqns= (CoefficientRules[(LT[monictemplate]*cofactTemplate),vars]/.
{Rule[exp_,coeff_]:>exp})/.CoefficientRules[Expand[cofactTemplate*monictemplate -Lftemplate],vars];
(* Separate the above coefficients from the rest *)
feqns=Complement[eqns,geqns];
(* Solve in terms of parametric coefficients of p *)
geqnsol=Solve[Map[#==0&,geqns],cofactCoeffs, Complexes];
(* Update the other coeffients with this solution *)
feqns=feqns/.geqnsol;
feqnsol=Solve[Map[#==0&,(feqns//Flatten)],Join[cofactCoeffs, moncoeffs[[1;;Length[monbas]-j]]], Complexes];
If[Length[feqnsol]>0,Sfg=Join[Sfg, Select[monictemplate/.feqnsol, IrreduciblePolynomialQ[#,Extension->All]&]]];
], {k,1,deg}, {j,1,Length[monbas]-1}];

Throw[Sfg//DeleteDuplicates];

]]


ManPS1[deg_Integer,vf_List,vars_List]:=Catch[Module[
{monbas, moncoeffs, monictemplate, LieD, MonBas, cofactCoeffs, cofactBasis, 
cofactTemplate, Lftemplate, Lfdeg, lhs ,sol, problem, LT,n,eqns,feqns,geqns,
geqnsol,feqnsol,Sfg, monomialOrder,
(* Maximum total polynomial degree of the vector field *)
m=Max[Map[PolynomDegree[#,vars]&,vf]]},
If[deg<=0, Throw[0]];

(* Fix monomial order *)
monomialOrder="DegreeLexicographic";
(* Leading Term computation *)
LT[p_]:=LT[p]=FromCoefficientRules[{CoefficientRules[p,vars, monomialOrder][[1]]}, vars];
(* Lie derivative of template *)
LieD[p_]:=LieD[p]=Grad[p,vars].vf;

(* Compute monomial basis w.r.t. the fixed monomial order *)
MonBas[maxdeg_]:=MonBas[maxdeg]=Map[#/CoefficientRules[#][[1]][[2]]&,MonomialList[ (Plus @@ Join[vars,{1}])^maxdeg , vars, monomialOrder]];
monbas=MonBas[1];
(* Final solution set is initially empty *)
Sfg={};
Do[
monbas=MonBas[k];
(* Compute monic polynomial templates *)
moncoeffs=Join[Table[Symbol["COEFF"<>ToString[i]],{i,1,Length[monbas]-j}], {1}, Table[0,{i,1,j-1}]];
monictemplate=moncoeffs.Reverse[monbas];
(* Compute Lie deriovative of the monic template *)
Lftemplate=LieD[monictemplate];
(* If LT(p) divides LT(D(p)) *)
If[TrueQ[PolynomialReduce[LT[Lftemplate],LT[monictemplate],vars][[2]]==0], 
n=PolynomDegree[Lftemplate,vars]-PolynomDegree[monictemplate,vars];
If[n<0, n=0];
(* Create a parametric cofactor of degree n *)
cofactBasis=MonBas[n];
cofactCoeffs=Table[Symbol["COFACTCOEFF"<>ToString[i]],{i,1,Length[cofactBasis]}];
cofactTemplate=cofactCoeffs.cofactBasis;
(* Bring everything in Lfp = q*p to the right hand side and collect the coefficients *)
eqns=DeleteDuplicates@(#[[2]]&/@CoefficientRules[cofactTemplate*monictemplate -Lftemplate,vars]);
(* Collect coefficients of those monomial terms that are generated by LT(p)*q *)
geqns= (CoefficientRules[(LT[monictemplate]*cofactTemplate),vars]/.
{Rule[exp_,coeff_]:>exp})/.CoefficientRules[Expand[cofactTemplate*monictemplate -Lftemplate],vars];
(* Separate the above coefficients from the rest *)
feqns=Complement[eqns,geqns];
(* Solve in terms of parametric coefficients of p *)
geqnsol=Solve[Map[#==0&,geqns],cofactCoeffs, Complexes];
(* Update the other coeffients with this solution *)
feqns=feqns/.geqnsol;
feqnsol=Solve[Map[#==0&,(feqns//Flatten)],Join[cofactCoeffs, moncoeffs[[1;;Length[monbas]-j]]], Complexes];
If[Length[feqnsol]>0,Sfg=Join[Sfg, Select[monictemplate/.feqnsol, IrreduciblePolynomialQ[#,Extension->All]&]]];
], {k,1,deg}, {j,1,Length[monbas]-1}];

Throw[Sfg//DeleteDuplicates];

]]


(* Implementation of new_ps_1 algorithm from Y.K. Man's 1993 JSC paper *)
ManPS2[deg_Integer,vf_List,vars_List]:=Catch[Module[
{monbas, moncoeffs, monictemplate, LieD, MonBas, cofactCoeffs, cofactBasis, 
cofactTemplate, Lftemplate, Lfdeg, lhs ,sol, problem, LT, LC, n, gi, indivisible, 
eqns,feqns,geqns, elimvar, s, geqnsol,feqnsol,Sfg,irreducibles,monomialOrder},
If[deg<=0, Throw[0]];
(* Fix monomial order *)
monomialOrder="DegreeLexicographic";
(* Final solution set is initially empty *)
Sfg={};
(* Leading Term computation *)
LT[p_]:=LT[p]=FromCoefficientRules[{CoefficientRules[p,vars, "DegreeLexicographic"][[1]]}, vars];
(* Leading Coefficient computation *)
LC[p_]:=LC[p]=(CoefficientRules[p,vars, monomialOrder][[1]])/.Rule[exp_,coeff_]:>coeff;
(* Lie derivative of template *)
LieD[p_]:=Grad[p,vars].vf;
(* Compute monomial basis in lexicographic order *)
MonBas[maxdeg_]:=MonBas[maxdeg]=Map[#/CoefficientRules[#][[1]][[2]]&,MonomialList[ (Plus @@ Join[vars,{1}])^maxdeg , vars, monomialOrder]];
monbas=MonBas[1];
Do[
monbas=MonBas[k];
(* Compute monic polynomial templates *)
moncoeffs=Join[Table[Unique[coeff],{i,1,Length[monbas]-j}], {1}, Table[0,{i,1,j-1}]];
monictemplate=moncoeffs.Reverse[monbas];
(* Compute Lie deriovative of the monic template *)
Lftemplate=LieD[monictemplate];

gi=0;
indivisible=False;
feqns={};
While[ TrueQ[Not[indivisible] && Not[TrueQ[Expand[Lftemplate]==0]]],
Which[
(* If LT(p) divides LT(D(p)) *)
TrueQ[PolynomialReduce[LT[Lftemplate],LT[monictemplate],vars][[2]]==0], 
(* Then *)
gi = gi + (LT[Lftemplate]/LT[monictemplate]);
Lftemplate = Expand[Lftemplate - monictemplate*(LT[Lftemplate]/LT[monictemplate])],
(* Else, if LC(D(p)) - the leading COEFFICIENT of D(p) - is a constant *)
NumericQ[LC[Lftemplate]],
(* Then *)
indivisible=True,
(* Else if LC(D(p)) contains ONLY ONE parameter of p *)
TrueQ[Length[Variables[LC[Lftemplate]]==1]],
(* Then *)
elimvar=Variables[LC[Lftemplate]];
s=Solve[LC[Lftemplate]==0,elimvar,Complexes];
monictemplate=monictemplate/.s;
gi=gi/.s;
feqns=feqns/.s, 
(* Anything else *)
True, 
(* Then *)
feqns=Join[feqns,{LC[Lftemplate]}];
Lftemplate = Lftemplate-LT[Lftemplate]
] (* End Which *) 
]; (* End While *)

If[TrueQ[Not[indivisible] && Length[feqns]>0], 
feqnsol=Solve[Map[#==0&,feqns], moncoeffs[[1;;Length[monbas]-j]], Complexes];
];

If[Length[feqnsol]>0, 
If[TrueQ[Not[indivisible] ],
irreducibles=Select[monictemplate/.feqnsol, IrreduciblePolynomialQ[#,Extension->All]&];
Sfg=Join[Sfg,irreducibles];
 ]
]
, {k,1,deg}, {j,1,Length[monbas]-1}];

Throw[Sfg//DeleteDuplicates];
]]


MinRank[mat_List, symbols_List]:=Catch[Module[{
m,n,dets,sols, minrk, rsubmats,minSol
},
{m,n}=Dimensions[mat];
minrk=m;
dets={};
minSol={};
Do[
dets=Join[dets,Minors[mat,r]//Flatten];
Print[r];
(* Solutions for Det\[Equal]0 *)
(* sols=FindInstance[Map[#==0&,dets], symbols, Reals, 5, WorkingPrecision->MachinePrecision]; *)
sols=Solve[Map[#==0&,dets], symbols, Complexes];
(* Print[sols]; *)
If[sols=={}, Print["Minimised rank is "<>ToString[r]<>". Returning all solutions: "]; Print[minSol]; Throw[minSol]];
minSol=Join[minSol,sols]//DeleteDuplicates;
,{r,n,1,-1}];
Print["Symbolic matrix has full rank. Can't help you."];
Throw[{}];
]]


MatringeCompleteDbx[deg_Integer,vf_List,vars_List]:=Catch[Module[
{monbas, newMonbas, coeffs, template, Dtemplate, basisVect, 
newBasisVect, PolyBasis, LieD, MD,LT, MonBas,cofactCoeffs,
cofactBasis,cofactTemplate, choices, evaluations, rk, minrk, 
cofactor,nullspaces,minInstance, r=deg, darboux, minSol,
(* Maximum total polynomial degree of the vector field *)
d=Max[Map[PolynomDegree,vf]]},

If[deg<=0, Throw[0]];
(* Compute monomial basis in lexicographic order *)
MonBas[maxdeg_]:=Map[#/CoefficientRules[#][[1]][[2]]&,MonomialList[ (Plus @@ Join[vars,{1}])^maxdeg , vars, "Lexicographic"]];
monbas= MonBas[deg];
(* Compute the symbolic coefficients of the polynomial template *)
coeffs=Table[Symbol["COEFF"<>ToString[i]],{i,1,Length[monbas]}];
cofactBasis=MonBas[d-1];
cofactCoeffs=Table[Symbol["COFACTCOEFF"<>ToString[i]],{i,1,Length[cofactBasis]}];

template=coeffs.monbas;
cofactTemplate=cofactCoeffs.cofactBasis;
LieD[p_]:=Grad[p,vars].vf;

(* Building a basis vector from given polynomial *)
newMonbas= MonBas[(r+d-1)];
newBasisVect=Map[CoefficientRules[#, vars, "Lexicographic"][[1]][[1]]&, newMonbas];
PolyBasis[p_]:=Replace[(newBasisVect/.CoefficientRules[p, vars, "Lexicographic"]),{__Integer}:> 0, {1}];
(* Build formal Lie derivative computation matrix *)
MD=(Map[PolyBasis,Map[LieD, monbas]])//Transpose;
(* Building the multiplication by cofactor matrix *)
LT=(Map[PolyBasis,cofactTemplate*monbas])//Transpose;
(* Print[(MD-LT)//MatrixForm]; *)
minSol=MinRank[(MD-LT), cofactCoeffs];
minrk=(MD-LT)/.minSol;
(*Print[(MD-LT)//MatrixForm];
Print[minrk]; *)
darboux=Map[NullSpace[#].monbas &, minrk]//Flatten//DeleteDuplicates;
Throw[darboux];

]]


End[]
EndPackage[]
