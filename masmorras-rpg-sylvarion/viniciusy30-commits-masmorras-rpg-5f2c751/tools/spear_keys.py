import math
SHR=(-8.4,13.2,0.0); SHL=(8.4,13.2,0.0); REACH=11.3; G=8.6
def dist(a,b): return math.sqrt(sum((x-y)**2 for x,y in zip(a,b)))
def key(psi_w, pitch, e, T, crouch, lean, hip, slide, anchor=(-3.0,11.0,2.0), g=G):
    pl = psi_w - T                       # giro da haste no quadro do peito
    d = (math.sin(pl)*math.cos(pitch), math.sin(pitch), math.cos(pl)*math.cos(pitch))
    B = [anchor[i] + d[i]*e for i in range(3)]
    F = [B[i] + d[i]*g for i in range(3)]
    shifted = 0.0
    for _ in range(40):
        if dist(B,SHR) <= REACH and dist(F,SHL) <= REACH: break
        B = [B[i]-d[i]*0.25 for i in range(3)]; F = [F[i]-d[i]*0.25 for i in range(3)]; shifted += .25
    ok = dist(B,SHR) <= REACH+1e-3 and dist(F,SHL) <= REACH+1e-3
    return dict(v=[*B,*F,T,crouch,lean,hip,slide], shifted=shifted, ok=ok, pl=pl, d=d)

# (psi_world, pitch, ext, twist, crouch, lean, hip, slide)
A = {
 # 0 estocada rapida
 (0,'W'): (0.12, 0.04,-3.0,-1.00,0.30,-0.15,-0.50,-2.0),
 (0,'M'): (0.04, 0.04, 1.5,-0.90,0.45, 0.20,-0.30, 1.5),
 (0,'E'): (0.00, 0.02, 5.0,-0.90,0.55, 0.35, 0.10, 4.0),
 # 1 varrida lateral (direita -> esquerda)
 (1,'W'): (-1.15,0.02, 0.5,-2.10,0.35,-0.10,-1.60, 0.0),
 (1,'M'): ( 0.00,0.00, 1.5,-0.95,0.50, 0.15,-0.60, 1.0),
 (1,'E'): ( 1.10,0.04, 1.0, 0.15,0.45, 0.20, 0.10, 1.0),
 # 2 investida forte
 (2,'W'): (0.20,-0.05,-4.0,-1.25,0.62,-0.20,-0.70,-3.0),
 (2,'M'): (0.04, 0.00, 1.5,-0.90,0.78, 0.30,-0.35, 2.0),
 (2,'E'): (0.00, 0.08, 5.5,-0.92,0.85, 0.55, 0.15, 5.0),
 # 3 arremesso: preparo (W), chicote (M), solta (E)
 (3,'W'): (0.00, 0.32,-4.0,-1.10,0.35,-0.30,-0.80,-3.0),
 (3,'M'): (0.00, 0.22, 1.0,-0.75,0.55, 0.25,-0.20, 0.0),
 (3,'E'): (0.00, 0.10, 4.5,-0.90,0.70, 0.55, 0.25, 0.0),
}
ANCH = {(3,'W'):(-4.2,16.0,-2.8), (3,'M'):(-3.2,14.5,1.0), (3,'E'):(-2.6,12.6,4.0)}
if __name__=='__main__':
    out={}
    for k,v in A.items():
        r = key(*v, anchor=ANCH.get(k,(-3.0,11.0,2.0)))
        out[k]=r
        print(k, 'ok' if r['ok'] else 'FORA', 'recuo %.2f'%r['shifted'], 'hastaYawLocal %.2f'%r['pl'], [round(x,3) for x in r['v']])
    import json; json.dump({f"{a}{b}":o['v'] for (a,b),o in out.items()}, open('/home/claude/w/keys.json','w'))
