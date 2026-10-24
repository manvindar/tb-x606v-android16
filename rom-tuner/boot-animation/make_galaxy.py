# Galaxy boot animation: particles spiral in from the edges, a core ignites, then a 3-armed galaxy loops (seamless).
import numpy as np, os, zipfile, sys
from PIL import Image, ImageFilter
S=800; C=S/2; N0,N1=54,60; rng=np.random.default_rng(7)
OUT=sys.argv[1] if len(sys.argv)>1 else 'out'
NP=3200
# galaxy particles: arm structure, radius, integer revolutions per loop (differential rotation)
arm=rng.integers(0,3,NP); r=(rng.random(NP)**0.75)*340+14
a0=arm*2*np.pi/3 + r*0.017 + rng.normal(0,0.20+0.25*(r/340),NP)
rev=np.clip(np.round(4.0*(1-r/330)+1),1,5).astype(int)          # 5..1 revolutions per loop, inner spins faster
z=rng.normal(0,1,NP)*(0.25+0.1*(r/340))                          # thickness (depth cue)
size=rng.uniform(0.6,1.5,NP)*np.where(rng.random(NP)<0.06,3.0,1.0)
hue=np.clip(r/350,0,1)
stops=np.array([[255,244,214],[255,150,92],[255,70,160],[120,80,255],[40,205,230]],np.float32)/255
pos=hue*(len(stops)-1); i0=np.clip(pos.astype(int),0,len(stops)-2); f=(pos-i0)[:,None]
col=stops[i0]*(1-f)+stops[i0+1]*f
# intro start state: far out, random angle, extra spin
rs=rng.uniform(520,1100,NP); as_=rng.uniform(0,2*np.pi,NP); delay=rng.uniform(0,0.35,NP)
stars=rng.random((140,2))*S; star_ph=rng.random(140)*2*np.pi; star_b=rng.uniform(0.15,0.5,140)
TILT=0.46
def project(rr,aa,zz,plane):                     # plane: rotation of the galactic plane on screen
    x=rr*np.cos(aa); y=rr*np.sin(aa)*TILT
    cx=x*np.cos(plane)-y*np.sin(plane); cy=x*np.sin(plane)+y*np.cos(plane)
    return C+cx, C+cy+zz*8
def splat(img,px,py,w,c):
    ix=np.floor(px).astype(int); iy=np.floor(py).astype(int); fx=px-ix; fy=py-iy
    for dx,dy,ww in ((0,0,(1-fx)*(1-fy)),(1,0,fx*(1-fy)),(0,1,(1-fx)*fy),(1,1,fx*fy)):
        m=(ix+dx>=0)&(ix+dx<S)&(iy+dy>=0)&(iy+dy<S)
        for ch in range(3): np.add.at(img[...,ch],(iy[m]+dy,ix[m]+dx),(ww*w*c[:,ch])[m])
def blur(img,rad):
    im=Image.fromarray((np.clip(img,0,1)*255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(rad))
    return np.asarray(im,np.float32)/255
yy,xx=np.mgrid[0:S,0:S].astype(np.float32); rr2=np.sqrt((xx-C)**2+(yy-C)**2)
def frame(rr,aa,plane,core,alpha,ring_pulse=0.0,t=0.0,shock=None):
    img=np.zeros((S,S,3),np.float32)
    px,py=project(rr,aa,z,plane)
    depth=np.clip(1-(py-C)/700,0.5,1.4)                        # near side slightly brighter
    splat(img,px,py,1.7*size*depth*alpha,col)
    img*=1.0
    g=img+0.9*blur(img,2.2)+0.8*blur(img,9)+0.6*blur(img,26)
    # core
    cg=np.exp(-(rr2/(12+20*core))**2)*core; cg2=np.exp(-(rr2/150)**2)*core*0.30
    g+=cg[...,None]*np.array([1.0,0.93,0.82],np.float32)*1.0+cg2[...,None]*np.array([1.0,0.45,0.7],np.float32)
    # shockwave ring
    if shock is not None:
        sr,sa=shock; w=10+30*sr
        g+=(np.exp(-((rr2-sr*430)/w)**2)*sa)[...,None]*np.array([0.55,0.5,1.0],np.float32)*0.9
    # twinkling stars
    tw=star_b*(0.6+0.4*np.sin(star_ph+t*2*np.pi))*alpha
    sx=np.clip(stars[:,0].astype(int),0,S-1); sy=np.clip(stars[:,1].astype(int),0,S-1)
    for ch in range(3): g[sy,sx,ch]+=tw
    out=1-np.exp(-g*1.25)
    return Image.fromarray((np.clip(out,0,1)*255).astype(np.uint8))
def loop_state(p):                              # p in [0,1): seamless
    aa=a0+2*np.pi*rev*p
    return r, aa, 2*np.pi*p, 0.55+0.2*np.sin(2*np.pi*p)
os.makedirs(OUT+'/part0',exist_ok=True); os.makedirs(OUT+'/part1',exist_ok=True)
for i in range(N0):                              # intro
    p=i/(N0-1); rr_=np.empty(NP); aa_=np.empty(NP)
    for_e=np.clip((p-delay)/(1-0.35),0,1); e=1-(1-for_e)**3           # per-particle ease-out
    g_r,g_a,_,_=loop_state(0.0)
    rr_=g_r+(rs-g_r)*(1-e)**1.4
    aa_=g_a+(as_-g_a)*(1-e)*0+ (1-e)*(-5.5)                            # spiral in: trailing angle shrinks to 0
    aa_=g_a+(1-e)*(as_-g_a-2*np.pi*np.round((as_-g_a)/(2*np.pi)))-(1-e)*5.0
    core=0.15+0.4*np.clip((p-0.45)/0.55,0,1)**1.5+ (0.9*np.exp(-((p-0.82)/0.06)**2))
    shock=((p-0.8)/0.2,1-((p-0.8)/0.2)) if p>0.8 else None
    alpha=min(1,p*5)
    frame(rr_,aa_,2*np.pi*0.0*p,core,alpha,t=p,shock=shock).save('%s/part0/%03d.png'%(OUT,i),optimize=True)
for i in range(N1):                              # loop
    p=i/N1; rr_,aa_,plane,core=loop_state(p)
    frame(rr_,aa_,plane*0,core,1.0,t=p).save('%s/part1/%03d.png'%(OUT,i),optimize=True)
open(OUT+'/desc.txt','w').write('%d %d 30\np 1 0 part0\np 0 0 part1\n'%(S,S))
z_=zipfile.ZipFile(OUT+'/bootanimation.zip','w',zipfile.ZIP_STORED); z_.write(OUT+'/desc.txt','desc.txt')
for part,n in (('part0',N0),('part1',N1)):
    for i in range(n): z_.write('%s/%s/%03d.png'%(OUT,part,i),'%s/%03d.png'%(part,i))
z_.close(); print('zip',os.path.getsize(OUT+'/bootanimation.zip')//1024,'KB')
