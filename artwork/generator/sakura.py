"""Cherry blossom TV theme: static wallpaper + seamless falling-petal animation frames."""
import math, os, random, sys
from PIL import Image, ImageDraw, ImageFilter, ImageChops

W, H = 1920, 1080
SS = 2  # supersampling for the background
OUT = sys.argv[1] if len(sys.argv) > 1 else "theme"
os.makedirs(OUT, exist_ok=True)
rng = random.Random(7)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def gradient(w, h, stops):
    img = Image.new("RGB", (1, h))
    px = img.load()
    for y in range(h):
        t = y / (h - 1)
        for i in range(len(stops) - 1):
            p0, c0 = stops[i]
            p1, c1 = stops[i + 1]
            if p0 <= t <= p1:
                px[0, y] = lerp(c0, c1, (t - p0) / (p1 - p0))
                break
    return img.resize((w, h))


def glow(size, center, radius, color, alpha):
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    cx, cy = center
    for i in range(40, 0, -1):
        r = radius * i / 40
        a = int(alpha * (1 - i / 40) ** 1.6)
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color + (a,))
    return layer.filter(ImageFilter.GaussianBlur(radius / 12))


def blossom(d, x, y, r, rot, tone):
    """Five-petal sakura flower with notched petals and a deep pink centre."""
    base = [(255, 222, 232), (255, 200, 216), (250, 180, 204), (255, 236, 242)][tone]
    edge = lerp(base, (236, 120, 160), 0.35)
    for k in range(5):
        a = rot + k * 2 * math.pi / 5
        px, py = x + math.cos(a) * r * 0.55, y + math.sin(a) * r * 0.55
        pts = []
        for s in range(24):
            u = s / 23 * 2 * math.pi
            ex, ey = math.cos(u) * r * 0.62, math.sin(u) * r * 0.42
            # notch at the petal tip
            if math.cos(u) > 0.93:
                ex -= r * 0.18 * (math.cos(u) - 0.93) / 0.07
            pts.append((px + ex * math.cos(a) - ey * math.sin(a), py + ex * math.sin(a) + ey * math.cos(a)))
        d.polygon(pts, fill=edge)
        inner = [((p[0] - px) * 0.82 + px, (p[1] - py) * 0.82 + py) for p in pts]
        d.polygon(inner, fill=base)
    d.ellipse([x - r * 0.26, y - r * 0.26, x + r * 0.26, y + r * 0.26], fill=(214, 70, 120))
    for k in range(8):
        a = rot + k * math.pi / 4
        sx, sy = x + math.cos(a) * r * 0.38, y + math.sin(a) * r * 0.38
        d.line([x, y, sx, sy], fill=(200, 60, 105), width=max(1, int(r * 0.05)))
        d.ellipse([sx - r * 0.06, sy - r * 0.06, sx + r * 0.06, sy + r * 0.06], fill=(255, 214, 120))


def branch(d, flowers, x, y, ang, length, width, depth):
    if depth == 0 or length < 18:
        flowers.append((x, y))
        return
    segs = 6
    cx, cy, a = x, y, ang
    for s in range(segs):
        a += rng.uniform(-0.18, 0.18)
        nx, ny = cx + math.cos(a) * length / segs, cy + math.sin(a) * length / segs
        w = max(2, int(width * (1 - s / segs * 0.45)))
        d.line([cx, cy, nx, ny], fill=(58, 34, 46), width=w)
        d.ellipse([nx - w / 2, ny - w / 2, nx + w / 2, ny + w / 2], fill=(58, 34, 46))
        if s > 2 and rng.random() < 0.55:
            flowers.append((nx + rng.uniform(-30, 30), ny + rng.uniform(-30, 30)))
        cx, cy = nx, ny
    for k in range(rng.choice([2, 2, 3])):
        branch(d, flowers, cx, cy, a + rng.uniform(-0.75, 0.75), length * rng.uniform(0.55, 0.75), width * 0.62, depth - 1)


def background():
    w, h = W * SS, H * SS
    sky = gradient(w, h, [(0, (58, 36, 78)), (0.38, (148, 82, 128)), (0.62, (226, 150, 170)), (0.78, (120, 70, 110)), (1, (30, 20, 42))]).convert("RGBA")
    sky = Image.alpha_composite(sky, glow((w, h), (w * 0.62, h * 0.48), h * 0.55, (255, 210, 220), 150))
    d = ImageDraw.Draw(sky)
    # moon
    mx, my, mr = w * 0.62, h * 0.33, h * 0.11
    sky = Image.alpha_composite(sky, glow((w, h), (mx, my), mr * 2.6, (255, 236, 240), 120))
    d = ImageDraw.Draw(sky)
    moon = Image.new("RGBA", (int(2 * mr), int(2 * mr)), (0, 0, 0, 0))
    mdraw = ImageDraw.Draw(moon)
    for i in range(50, 0, -1):  # limb darkening: bright centre, warmer edge
        rr = mr * i / 50
        mdraw.ellipse([mr - rr, mr - rr, mr + rr, mr + rr], fill=lerp((255, 250, 250), (246, 214, 226), (i / 50) ** 2) + (255,))
    maria = Image.new("RGBA", moon.size, (0, 0, 0, 0))
    md2 = ImageDraw.Draw(maria)
    for (fx_, fy_, fr) in [(0.38, 0.40, 0.22), (0.62, 0.34, 0.15), (0.55, 0.62, 0.20), (0.30, 0.66, 0.10), (0.72, 0.58, 0.08)]:
        md2.ellipse([(fx_ - fr) * 2 * mr, (fy_ - fr) * 2 * mr, (fx_ + fr) * 2 * mr, (fy_ + fr) * 2 * mr], fill=(222, 186, 204, 70))
    maria = maria.filter(ImageFilter.GaussianBlur(mr * 0.06))
    mmask = Image.new("L", moon.size, 0)
    ImageDraw.Draw(mmask).ellipse([0, 0, 2 * mr - 1, 2 * mr - 1], fill=255)
    maria.putalpha(ImageChops.multiply(maria.getchannel("A"), mmask))
    moon = Image.alpha_composite(moon, maria)
    sky.alpha_composite(moon, (int(mx - mr), int(my - mr)))
    d = ImageDraw.Draw(sky)
    # far mountain (Fuji-like) and near hills
    m = [(0, h * 0.80), (w * 0.30, h * 0.70), (w * 0.47, h * 0.47), (w * 0.53, h * 0.46), (w * 0.72, h * 0.66), (w, h * 0.74), (w, h), (0, h)]
    d.polygon(m, fill=(104, 64, 104))
    snow = [(w * 0.425, h * 0.53), (w * 0.47, h * 0.47), (w * 0.53, h * 0.46), (w * 0.575, h * 0.52), (w * 0.55, h * 0.515), (w * 0.52, h * 0.535), (w * 0.49, h * 0.515), (w * 0.455, h * 0.54)]
    d.polygon(snow, fill=(236, 206, 222))
    hill = [(0, h * 0.86)] + [(x, h * 0.84 + math.sin(x / w * 7) * h * 0.025) for x in range(0, w + 1, 40)] + [(w, h), (0, h)]
    d.polygon(hill, fill=(58, 36, 66))
    # pagoda silhouette on the right hill
    px, base = w * 0.80, h * 0.835
    for i in range(4):
        tw, th = w * (0.05 - i * 0.008), h * 0.045
        y0 = base - i * th
        d.polygon([(px - tw, y0), (px + tw, y0), (px + tw * 0.55, y0 - th * 0.35), (px - tw * 0.55, y0 - th * 0.35)], fill=(40, 24, 48))
        d.rectangle([px - tw * 0.42, y0 - th, px + tw * 0.42, y0 - th * 0.35], fill=(40, 24, 48))
    d.line([px, base - 4 * h * 0.045, px, base - 4 * h * 0.045 - h * 0.05], fill=(40, 24, 48), width=6)
    # soft bokeh
    bok = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    bd = ImageDraw.Draw(bok)
    for _ in range(70):
        x, y, r = rng.uniform(0, w), rng.uniform(0, h * 0.8), rng.uniform(10, 60)
        bd.ellipse([x - r, y - r, x + r, y + r], fill=(255, 200, 220, rng.randint(25, 70)))
    sky = Image.alpha_composite(sky, bok.filter(ImageFilter.GaussianBlur(14)))
    # branches from both top corners
    tree = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    td = ImageDraw.Draw(tree)
    flowers = []
    branch(td, flowers, -40, h * 0.10, 0.25, w * 0.30, 44, 4)
    branch(td, flowers, -40, h * 0.42, -0.18, w * 0.20, 30, 3)
    branch(td, flowers, w + 40, -20, math.pi - 0.45, w * 0.26, 40, 4)
    shadow = tree.filter(ImageFilter.GaussianBlur(10))
    sky = Image.alpha_composite(sky, Image.eval(shadow, lambda v: v // 3))
    sky = Image.alpha_composite(sky, tree)
    fl = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    fd = ImageDraw.Draw(fl)
    for (x, y) in flowers:
        for _ in range(rng.randint(2, 5)):
            blossom(fd, x + rng.uniform(-38, 38), y + rng.uniform(-38, 38), rng.uniform(16, 34), rng.uniform(0, 6.3), rng.randrange(4))
    sky = Image.alpha_composite(sky, Image.eval(fl.filter(ImageFilter.GaussianBlur(6)), lambda v: v // 4))
    sky = Image.alpha_composite(sky, fl)
    # bottom vignette so launcher text stays readable
    vig = gradient(1, h, [(0, (0, 0, 0)), (0.55, (0, 0, 0)), (1, (0, 0, 0))]).convert("L")
    va = Image.new("L", (1, h))
    for y in range(h):
        t = max(0, (y / h - 0.5) / 0.5)
        va.putpixel((0, y), int(150 * t ** 1.5))
    vlayer = Image.new("RGBA", (w, h), (20, 10, 26, 0))
    vlayer.putalpha(va.resize((w, h)))
    sky = Image.alpha_composite(sky, vlayer)
    return sky.resize((W, H), Image.LANCZOS)


def petal_sprite(size, tone):
    s = size * 4
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    base = [(255, 214, 228), (250, 186, 208), (255, 234, 240)][tone]
    pts = []
    for k in range(40):
        u = k / 39 * 2 * math.pi
        x, y = math.cos(u) * s * 0.42, math.sin(u) * s * 0.26 * (1 - 0.35 * math.cos(u))
        if math.cos(u) > 0.92:
            x -= s * 0.12 * (math.cos(u) - 0.92) / 0.08
        pts.append((s / 2 + x, s / 2 + y))
    d.polygon(pts, fill=lerp(base, (230, 120, 160), 0.3) + (235,))
    d.polygon([((p[0] - s / 2) * 0.8 + s / 2, (p[1] - s / 2) * 0.8 + s / 2) for p in pts], fill=base + (240,))
    return img.resize((size, size), Image.LANCZOS)


def petals(n, seed):
    r = random.Random(seed)
    out = []
    for _ in range(n):
        size = r.choice([18, 22, 26, 30, 36, 44])
        out.append(dict(
            sprite=petal_sprite(size, r.randrange(3)), size=size,
            x0=r.uniform(0, W), y0=r.uniform(0, H + 200),
            laps=r.choice([1, 1, 2]) if size < 30 else 1,  # screen passes per loop
            sway=r.uniform(30, 110), sw_n=r.choice([1, 2, 3]), ph=r.uniform(0, 6.3),
            spin=r.choice([-2, -1, 1, 2]), flip=r.choice([2, 3, 4]), a0=r.uniform(0, 360),
            blur=size >= 40))
    return out


MOON = (W * 0.62, H * 0.33)
RAYS = 12  # rays layer has 12-fold symmetry, so rotating 360/12 per loop is seamless


def make_fx(seed=11):
    r = random.Random(seed)
    # slow god-rays around the moon
    R = 900
    rays = Image.new("RGBA", (2 * R, 2 * R), (0, 0, 0, 0))
    d = ImageDraw.Draw(rays)
    for k in range(RAYS):
        a = k * 2 * math.pi / RAYS
        for j, (wid, al) in enumerate([(0.10, 18), (0.05, 22)]):
            aa = a + j * math.pi / RAYS
            d.polygon([(R, R), (R + R * math.cos(aa - wid), R + R * math.sin(aa - wid)), (R + R * math.cos(aa + wid), R + R * math.sin(aa + wid))], fill=(255, 220, 235, al))
    mask = Image.new("L", (2 * R, 2 * R), 0)
    md = ImageDraw.Draw(mask)
    for i in range(60, 0, -1):
        rr = R * i / 60
        md.ellipse([R - rr, R - rr, R + rr, R + rr], fill=int(255 * (1 - i / 60) ** 1.2))
    rays.putalpha(ImageChops.multiply(rays.getchannel("A"), mask))
    rays = rays.filter(ImageFilter.GaussianBlur(6))
    # breathing halo around the moon
    halo = glow((W, H), MOON, H * 0.34, (255, 200, 225), 120)
    # sparkles / fireflies
    spark = Image.new("RGBA", (48, 48), (0, 0, 0, 0))
    sd = ImageDraw.Draw(spark)
    sd.ellipse([14, 14, 34, 34], fill=(255, 210, 230, 120))
    spark = spark.filter(ImageFilter.GaussianBlur(5))
    sd = ImageDraw.Draw(spark)
    sd.ellipse([21, 21, 27, 27], fill=(255, 250, 252, 255))
    sd.line([24, 12, 24, 36], fill=(255, 240, 248, 140), width=1)
    sd.line([12, 24, 36, 24], fill=(255, 240, 248, 140), width=1)
    sparks = [dict(x=r.uniform(0, W), y=r.uniform(0, H * 0.85), n=r.choice([1, 2, 3]), ph=r.uniform(0, 6.3),
                   dx=r.uniform(15, 50), dy=r.uniform(10, 35), s=r.choice([0.6, 0.8, 1.0, 1.3])) for _ in range(45)]
    petal_glow = {}
    # drifting valley mist, twice the screen wide so it can scroll and wrap
    mist = Image.new("RGBA", (W * 2, H), (0, 0, 0, 0))
    md = ImageDraw.Draw(mist)
    for _ in range(80):
        cx, cy = r.uniform(0, W), r.uniform(H * 0.66, H * 0.86)
        rw, rh = r.uniform(140, 360), r.uniform(25, 60)
        for off in (0, W):
            md.ellipse([cx + off - rw, cy - rh, cx + off + rw, cy + rh], fill=(255, 215, 235, r.randint(14, 30)))
    mist = mist.filter(ImageFilter.GaussianBlur(28))
    # cinematic edge vignette
    vig = Image.new("L", (W // 8, H // 8), 0)
    vp = vig.load()
    for y in range(vig.height):
        for x in range(vig.width):
            dx, dy = (x / vig.width - 0.5) * 2, (y / vig.height - 0.5) * 2
            vp[x, y] = int(min(255, 140 * max(0, (dx * dx * 0.8 + dy * dy) ** 0.5 - 0.55) ** 1.4 * 2.2))
    vlayer = Image.new("RGBA", (W, H), (14, 6, 20, 0))
    vlayer.putalpha(vig.resize((W, H), Image.BICUBIC))
    # big out-of-focus foreground petals (depth of field)
    fg = []
    for _ in range(5):
        size = r.choice([90, 110, 130])
        spr = petal_sprite(size, r.randrange(3)).filter(ImageFilter.GaussianBlur(7))
        fg.append(dict(sprite=with_alpha(spr, 0.75), size=size, x0=r.uniform(0, W), y0=r.uniform(0, H + 400),
                       sway=r.uniform(60, 140), ph=r.uniform(0, 6.3), spin=r.choice([-1, 1]), a0=r.uniform(0, 360)))
    return dict(rays=rays, halo=halo, spark=spark, sparks=sparks, petal_glow=petal_glow, mist=mist, vignette=vlayer, fg=fg)


def with_alpha(img, f):
    out = img.copy()
    out.putalpha(out.getchannel("A").point(lambda v: int(v * f)))
    return out


def frame(bg, ps, t, fx=None):
    """t in [0,1): every motion is periodic in t, so the loop is seamless."""
    img = bg.copy()
    if fx:
        rays = fx["rays"].rotate(360 / RAYS * t, resample=Image.BICUBIC)
        img.alpha_composite(rays, (int(MOON[0] - rays.width / 2), int(MOON[1] - rays.height / 2)))
        img.alpha_composite(with_alpha(fx["halo"], 0.55 + 0.45 * math.sin(2 * math.pi * 2 * t)))
        shift = int(W * t)
        img.alpha_composite(fx["mist"].crop((shift, 0, shift + W, H)))
        for s in fx["sparks"]:
            tw = 0.5 + 0.5 * math.sin(2 * math.pi * s["n"] * t + s["ph"])
            if tw < 0.08:
                continue
            sz = int(48 * s["s"] * (0.7 + 0.3 * tw))
            sp = with_alpha(fx["spark"].resize((sz, sz)), tw)
            x = s["x"] + s["dx"] * math.sin(2 * math.pi * t + s["ph"])
            y = s["y"] + s["dy"] * math.cos(2 * math.pi * 2 * t + s["ph"])
            img.alpha_composite(sp, (int(x - sz / 2), int(y - sz / 2)))
    span = H + 200
    for p in ps:
        y = (p["y0"] + p["laps"] * span * t) % span - 100
        x = (p["x0"] + 0.35 * W * t + p["sway"] * math.sin(2 * math.pi * p["sw_n"] * t + p["ph"])) % (W + 100) - 50
        flipv = abs(math.cos(2 * math.pi * p["flip"] * t + p["ph"]))
        spr = p["sprite"].resize((p["size"], max(2, int(p["size"] * (0.25 + 0.75 * flipv)))))
        spr = spr.rotate(p["a0"] + 360 * p["spin"] * t, expand=True, resample=Image.BICUBIC)
        if p["blur"]:
            spr = spr.filter(ImageFilter.GaussianBlur(1.5))
        if fx:
            g = fx["petal_glow"].get(p["size"])
            if g is None:
                gs = p["size"] * 3
                g = Image.new("RGBA", (gs, gs), (0, 0, 0, 0))
                ImageDraw.Draw(g).ellipse([gs / 3, gs / 3, 2 * gs / 3, 2 * gs / 3], fill=(255, 170, 205, 90))
                g = g.filter(ImageFilter.GaussianBlur(p["size"] / 3))
                fx["petal_glow"][p["size"]] = g
            img.alpha_composite(g, (int(x - g.width / 2), int(y - g.height / 2)))
        img.alpha_composite(spr, (int(x - spr.width / 2), int(y - spr.height / 2)))
    if fx:
        span = H + 400
        for p in fx["fg"]:
            y = (p["y0"] + span * t) % span - 200
            x = (p["x0"] + 0.5 * W * t * 0 + p["sway"] * math.sin(2 * math.pi * t + p["ph"])) % W
            spr = p["sprite"].rotate(p["a0"] + 360 * p["spin"] * t, expand=True, resample=Image.BICUBIC)
            img.alpha_composite(spr, (int(x - spr.width / 2), int(y - spr.height / 2)))
        img.alpha_composite(fx["vignette"])
    return img


if __name__ == "__main__":
    mode = sys.argv[2] if len(sys.argv) > 2 else "still"
    bg = background()
    bg.convert("RGB").save(os.path.join(OUT, "sakura_wallpaper_plain.png"))
    ps = petals(55, 3)
    fx = make_fx()
    frame(bg, ps, 0.0).convert("RGB").save(os.path.join(OUT, "sakura_wallpaper.png"))
    frame(bg, ps, 0.0, fx).convert("RGB").save(os.path.join(OUT, "sakura_wallpaper_glow.png"))
    if mode == "frames":
        fdir = sys.argv[3]
        os.makedirs(fdir, exist_ok=True)
        n = int(sys.argv[4]) if len(sys.argv) > 4 else 600
        for i in range(n):
            frame(bg, ps, i / n).convert("RGB").save(os.path.join(fdir, f"f{i:04d}.png"), compress_level=1)
            if i % 100 == 0:
                print("frame", i, flush=True)
    if mode == "video":
        import subprocess, imageio_ffmpeg
        n = int(sys.argv[3]) if len(sys.argv) > 3 else 600
        cmd = [imageio_ffmpeg.get_ffmpeg_exe(), "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", "30", "-i", "-",
               "-c:v", "libx264", "-preset", "slow", "-crf", "20", "-pix_fmt", "yuv420p", "-movflags", "+faststart",
               os.path.join(OUT, "sakura_animated.mp4")]
        proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stderr=subprocess.DEVNULL)
        for i in range(n):
            proc.stdin.write(frame(bg, ps, i / n, fx).convert("RGB").tobytes())
            if i % 100 == 0:
                print("frame", i, flush=True)
        proc.stdin.close()
        proc.wait()
    print("done")
