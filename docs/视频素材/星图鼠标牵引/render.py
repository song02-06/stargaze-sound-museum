from pathlib import Path
import math, random, subprocess
from PIL import Image, ImageDraw, ImageFont
import imageio_ffmpeg
OUT=Path(__file__).parent
W,H,S,FPS=1280,800,2,24
font_path='C:/Windows/Fonts/msyh.ttc'
def font(size): return ImageFont.truetype(font_path,size*S)
headline,body,small=font(26),font(17),font(13)
rng=random.Random(42)
stars=[(rng.randrange(W),rng.randrange(H),rng.choice([.6,.8,1.1]),rng.randrange(40,100)) for _ in range(100)]
base=Image.new('RGB',(W*S,H*S),(5,9,17));d=ImageDraw.Draw(base)
for x,y,r,a in stars:d.ellipse(((x-r)*S,(y-r)*S,(x+r)*S,(y+r)*S),fill=(a,a+5,a+14))
d.text((64*S,42*S),'星轨 · 声音博物馆',font=body,fill='#cbb88f')
d.text((64*S,85*S),'鼠标牵引 · 线条流动预览',font=headline,fill='#e9e5db')
d.text((64*S,132*S),'中心星点保持稳定，轨道随鼠标方向柔和偏移。',font=small,fill='#a5afc0')
cx,cy=640,430
# Position targets: right, left, lower right, then leave and settle.
keys=[(0,0,0),(1.2,0,0),(3.2,.95,-.18),(4.8,.95,-.18),(7,-.9,-.05),(8.4,-.9,-.05),(10.2,.45,.85),(11.7,.45,.85),(13.5,0,0),(15,0,0)]
def target(t):
 for (a,x,y),(b,u,v) in zip(keys,keys[1:]):
  if a<=t<=b:
   k=(t-a)/(b-a);k=k*k*(3-2*k);return x+(u-x)*k,y+(v-y)*k
 return 0,0
cmd=[imageio_ffmpeg.get_ffmpeg_exe(),'-y','-f','rawvideo','-vcodec','rawvideo','-s',f'{W}x{H}','-pix_fmt','rgb24','-r',str(FPS),'-i','-','-an','-vcodec','libx264','-preset','fast','-crf','19','-pix_fmt','yuv420p','-movflags','+faststart',str(OUT/'星图线条-鼠标牵引预览.mp4')]
proc=subprocess.Popen(cmd,stdin=subprocess.PIPE,stderr=subprocess.DEVNULL)
x=y=0
for i in range(15*FPS):
 t=i/FPS;tx,ty=target(t);x+=(tx-x)*.13;y+=(ty-y)*.13
 im=base.copy();d=ImageDraw.Draw(im)
 for rx,ry,angle,depth,color in [(270,105,-30,1,'#bca376'),(244,78,38,.65,'#728098'),(207,207,0,.35,'#334054'),(163,163,0,.23,'#293447')]:
  pts=[];a=math.radians(angle)
  for j in range(361):
   q=j*math.pi/180;px=rx*math.cos(q);py=ry*math.sin(q)
   u=px*math.cos(a)-py*math.sin(a);v=px*math.sin(a)+py*math.cos(a)
   # Spatially varying pull bends the line, rather than rotating the entire drawing.
   fall=.45+.55*(u*u+v*v)/(rx*rx+ry*ry)
   u+=x*72*depth*fall+math.sin(q*2)*y*15*depth
   v+=y*53*depth*fall+math.cos(q*2)*x*10*depth
   pts.append(((cx+u)*S,(cy+v)*S))
  d.line(pts,fill=color,width=S)
 # Stable central star.
 pts=[(cx,cy-19),(cx+4,cy-4),(cx+19,cy),(cx+4,cy+4),(cx,cy+19),(cx-4,cy+4),(cx-19,cy),(cx-4,cy-4)]
 d.polygon([(a*S,b*S) for a,b in pts],fill='#e5c895')
 d.ellipse(((cx-27)*S,(cy-27)*S,(cx+27)*S,(cy+27)*S),outline='#685f4b',width=S)
 d.text(((cx-47)*S,(cy+45)*S),'中心星点固定',font=small,fill='#a5afc0')
 if t<12.3:
  mx,my=cx+tx*350,cy+ty*235
  cursor=[(mx,my),(mx+4,my+22),(mx+10,my+15),(mx+20,my+14)]
  d.ellipse(((mx-18)*S,(my-18)*S,(mx+18)*S,(my+18)*S),outline='#596574',width=S)
  d.polygon([(a*S,b*S) for a,b in cursor],fill='#f0eee8',outline='#050911')
 caption='鼠标进入 · 线条开始响应' if t<1.5 else '鼠标在右侧 · 轨道向右牵引' if t<5 else '鼠标移向左侧 · 线条柔和跟随' if t<8.5 else '鼠标向下移动 · 多层轨道产生不同幅度的偏移' if t<12.3 else '鼠标离开 · 轨道缓慢回到原位'
 d.text((64*S,714*S),caption,font=body,fill='#e1dccf')
 d.text((64*S,752*S),'动效方向预览 / 非首页最终录屏',font=small,fill='#8e9aaa')
 frame=im.resize((W,H),Image.Resampling.LANCZOS)
 proc.stdin.write(frame.tobytes())
 if i==4*FPS:frame.save(OUT/'预览封面.png')
proc.stdin.close()
assert proc.wait()==0
print(OUT/'星图线条-鼠标牵引预览.mp4')
