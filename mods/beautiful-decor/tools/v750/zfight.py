#!/usr/bin/env python3
"""Busca caras que coinciden en el mismo plano, miran al mismo lado y se solapan (parpadean) en un modelo de TrainCarModel."""
import re, sys, itertools
src=open(sys.argv[1],encoding='utf-8').read()
want=sys.argv[2]
a=src.index("private static LayerDefinition create"+want+"Layer")
b=src.find("private static LayerDefinition create",a+10)
lay=src[a:b if b>0 else None]
# solo la parte body (primer addOrReplaceChild("body"...))
bm=re.search(r'addOrReplaceChild\("body",(.*?)PartPose',lay,re.S)
body=bm.group(1)
boxes=[tuple(map(float,m)) for m in re.findall(r'addBox\((-?[\d.]+)f, (-?[\d.]+)f, (-?[\d.]+)f, (-?[\d.]+)f, (-?[\d.]+)f, (-?[\d.]+)f',body)]
tex=re.findall(r'texOffs\((\d+), (\d+)\)\.addBox',body)
print(want,len(boxes),"cajas")
def ov(a0,a1,b0,b1): return min(a1,b1)-max(a0,b0)
found=0
for i,j in itertools.combinations(range(len(boxes)),2):
    A=boxes[i];B=boxes[j]
    for ax in range(3):
        o=[k for k in range(3) if k!=ax]
        for side in (0,1):
            pa=A[ax]+(A[3+ax] if side else 0); pb=B[ax]+(B[3+ax] if side else 0)
            if abs(pa-pb)>1e-6: continue
            w1=ov(A[o[0]],A[o[0]]+A[3+o[0]],B[o[0]],B[o[0]]+B[3+o[0]])
            w2=ov(A[o[1]],A[o[1]]+A[3+o[1]],B[o[1]],B[o[1]]+B[3+o[1]])
            if w1>1e-6 and w2>1e-6:
                found+=1
                print(f"  eje{'XYZ'[ax]} lado{side} plano={pa}: caja{i}{A} tex{tex[i]}  <->  caja{j}{B} tex{tex[j]}  solape {w1}x{w2}")
print("total",found)
