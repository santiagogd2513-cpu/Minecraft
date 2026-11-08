# Port de la logica de ConveyorBeltBlock para comprobar esquinas y recorrido de un item
N,E,S,W=(0,-1),(1,0),(0,1),(-1,0)
cw={N:E,E:S,S:W,W:N}; ccw={v:k for k,v in cw.items()}
opp={N:S,S:N,E:W,W:E}
def add(p,d): return (p[0]+d[0],p[1]+d[1])
class Grid:
    def __init__(s): s.b={}
    def feeds(s,frm,towards): return frm in s.b and s.b[frm]['f']==towards
    def shape(s,pos,f):
        if s.feeds(add(pos,opp[f]),f): return 'straight'
        left=s.feeds(add(pos,ccw[f]),cw[f]); right=s.feeds(add(pos,cw[f]),ccw[f])
        if left and not right: return 'curve_left'
        if right and not left: return 'curve_right'
        return 'straight'
    def place(s,pos,f):
        s.b[pos]={'f':f}
        s.refresh()
    def refresh(s):
        for p,v in s.b.items(): v['s']=s.shape(p,v['f'])
def dir_at(b,pos,x,z):
    f=b['f']; sh=b['s']
    if sh=='straight': return f
    entry=cw[f] if sh=='curve_left' else ccw[f]
    along=(x-(pos[0]+.5))*entry[0]+(z-(pos[1]+.5))*entry[1]
    return entry if along<0 else f
def run(g,start,steps=400):
    x,z=start; path=[]
    for _ in range(steps):
        pos=(int(x//1),int(z//1))
        if pos not in g.b: break
        b=g.b[pos]; d=dir_at(b,pos,x,z)
        side=cw[d]; lat=(x-(pos[0]+.5))*side[0]+(z-(pos[1]+.5))*side[1]
        pull=max(-.04,min(.04,-lat*.3))
        vx=d[0]*.11+side[0]*pull; vz=d[1]*.11+side[1]*pull
        x+=vx*.588; z+=vz*.588
        if not path or path[-1]!=pos: path.append(pos)
    return path
# 1) L: A(0,0) este, B(1,0) norte (puesto despues)
g=Grid(); g.place((0,0),E); g.place((1,0),N)
assert g.b[(1,0)]['s']=='curve_left', g.b[(1,0)]
# 2) orden inverso
g=Grid(); g.place((1,0),N); g.place((0,0),E)
assert g.b[(1,0)]['s']=='curve_left'
# 3) recto: A este, B este
g=Grid(); g.place((0,0),E); g.place((1,0),E); assert g.b[(1,0)]['s']=='straight'
# 4) giro a la derecha: A este, B sur => curve_right (feeder a la derecha? B mira sur: izquierda de sur es este, derecha es oeste)
g=Grid(); g.place((0,0),E); g.place((1,0),S)
print('B sur:',g.b[(1,0)]['s'])
# 5) recorrido en serpiente: E E N N W W
g=Grid()
for p,f in [((0,0),E),((1,0),E),((2,0),N),((2,-1),N),((2,-2),W),((1,-2),W),((0,-2),W)]: g.place(p,f)
print({p:v['s'] for p,v in g.b.items()})
path=run(g,(0.1,0.5))
print(path)
assert path==[(0,0),(1,0),(2,0),(2,-1),(2,-2),(1,-2),(0,-2)], path
# 6) desde el costado en una recta: alimenta lateral
g=Grid(); g.place((0,0),E); g.place((1,0),E); g.place((1,1),N)  # (1,1) mira norte hacia (1,0)
assert g.b[(1,0)]['s']=='straight'
print('OK')
