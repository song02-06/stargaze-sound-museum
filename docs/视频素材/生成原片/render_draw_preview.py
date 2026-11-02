"""Render a silent, procedural preview of the museum's retrieval ritual."""
from pathlib import Path
import math
import random
import subprocess

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent
OUT = ROOT / '打捞-完整预览-桌面-v01.mp4'
W, H, FPS, SECONDS = 1280, 720, 30, 3.2
rng = random.Random(20261027)


def ease(a):
    a = max(0.0, min(1.0, a))
    return a * a * (3 - 2 * a)


yy, xx = np.mgrid[0:H, 0:W]
cloud = np.exp(-(((xx - W * .5) / 410) ** 2 + ((yy - H * .45) / 270) ** 2))
base = np.zeros((H, W, 3), dtype=np.float32)
for c, (floor, light) in enumerate([(4, 8), (6, 14), (11, 25)]):
    base[:, :, c] = floor + light * cloud
background = Image.fromarray(np.uint8(base), 'RGB')
draw = ImageDraw.Draw(background)
for _ in range(185):
    x, y = rng.randrange(W), rng.randrange(H)
    brightness = rng.randrange(24, 105)
    draw.point((x, y), fill=(brightness, int(brightness * 1.06), min(255, int(brightness * 1.22))))
for radius in (115, 185, 255):
    draw.ellipse((W/2-radius, H*.52-radius, W/2+radius, H*.52+radius), outline=(17, 24, 37), width=1)
dust = [(rng.uniform(-1, 1), rng.random(), rng.random()) for _ in range(40)]


def frame(t):
    image = background.copy()
    light = Image.new('RGB', (W, H))
    d = ImageDraw.Draw(light)
    start_y = H * .19
    descend = ease(t / .88)
    rise = ease((t - 1.55) / .93)
    end_y = start_y + (H * .61 - start_y) * descend - H * .14 * rise
    power = ease(t / .4) * (1 - ease((t - 2.5) / .7))
    x = W / 2
    for j in range(110):
        a = j / 110
        sy = start_y + (end_y - start_y) * a
        strength = power * (a ** 2) * .62
        d.line((x, sy, x, sy + (end_y-start_y)/110+1), fill=tuple(int(v*strength) for v in (190, 218, 255)))
    ignite = ease((t - .6) / .48) * (1 - ease((t - 2.42) / .68))
    for index in range(3):
        age = (t - .82 - index * .18) / 1.55
        if 0 < age < 1:
            radius = 12 + 175 * ease(age)
            strength = math.sin(age*math.pi) * (1-age) * .36
            color = tuple(int(v*strength) for v in (143, 188, 244))
            d.ellipse((x-radius, end_y-radius*.56, x+radius, end_y+radius*.56), outline=color, width=1)
    for dx, dy, phase in dust:
        strength = ignite * rise * (1 - phase) * .55
        px = x + dx * (18 + 55 * dy)
        py = end_y + 10 + dy * 95 * rise
        d.point((px, py), fill=tuple(int(v*strength) for v in (201, 182, 143)))
    image = Image.fromarray(np.uint8(np.minimum(255, np.asarray(image, dtype=np.float32) + np.asarray(light, dtype=np.float32))))
    # Gaussian falloff gives the focal point soft volume without a hard disk.
    glow = ignite * np.exp(-(((xx-x)/19)**2 + ((yy-end_y)/19)**2)*2)
    halo = ignite * np.exp(-(((xx-x)/70)**2 + ((yy-end_y)/70)**2)*2)
    arr = np.asarray(image, dtype=np.float32).copy()
    for c, (core, outer) in enumerate([(240, 15), (241, 28), (235, 48)]):
        arr[:,:,c] += core * glow + outer * halo
    image = Image.fromarray(np.uint8(np.clip(arr, 0, 255)))
    d = ImageDraw.Draw(image)
    if ignite > .02:
        r = 1.2 + ignite * 1.6
        d.ellipse((x-r,end_y-r,x+r,end_y+r), fill=tuple(int(v*ignite) for v in (255,248,227)))
    spread = ease((t-2.22)/.6)
    if spread:
        glow_line = Image.new('RGB', (W,H))
        ld = ImageDraw.Draw(glow_line)
        length = 145 * spread
        strength = math.sin(spread*math.pi) * .6
        for i in range(int(length)+1):
            value = strength * (1-i/max(1,length))**2
            color = tuple(int(v*value) for v in (180,205,235))
            ld.line((x-i,end_y,x+i,end_y), fill=color)
        from PIL import ImageChops
        image = ImageChops.add(image, glow_line.filter(ImageFilter.GaussianBlur(.7)))
    fade = ease((t-2.85)/.35)
    if fade:
        image = Image.blend(image, Image.new('RGB',(W,H),(4,6,11)), fade)
    return image


if __name__ == '__main__':
    process = subprocess.Popen(['ffmpeg','-hide_banner','-loglevel','error','-y',
        '-f','rawvideo','-vcodec','rawvideo','-pix_fmt','rgb24','-s',f'{W}x{H}',
        '-r',str(FPS),'-i','-','-an','-c:v','libx264','-crf','18','-preset','medium',
        '-pix_fmt','yuv420p','-movflags','+faststart',str(OUT)], stdin=subprocess.PIPE)
    for n in range(round(FPS * SECONDS)):
        process.stdin.write(frame(n/FPS).tobytes())
    process.stdin.close()
    if process.wait():
        raise RuntimeError('Video encoding failed')
    frame(1.35).save(ROOT / '打捞-完整预览-桌面-v01.jpg', quality=94)
    print(str(OUT))
