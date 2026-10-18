"""カードのテクスチャ（黒地にHUD風のパネル、フラットで少ない色数、256x256）とモデル定義を生成する。

使い方: python3 resourcepack/generate.py
出力: resourcepack/pack/ 以下（これを zip にしたものがリソースパック）と resourcepack/preview.png
custom/<カード名>.png があれば、そのカードは描かずにその画像をそのまま使う。
"""
import json
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "pack")
CUSTOM = os.path.join(HERE, "custom")
NS = "bowrounds"

S = 1024     # 描くときの大きさ（最後に縮小してなめらかにする）
OUT = 256    # テクスチャの大きさ

# カード名 -> (レア度, 表示名)。Card.java と合わせる
CARDS = {
    "spread": ("common", "Buckshot"),
    "bounce": ("common", "Ricochet"),
    "quick_reload": ("common", "Quick Reload"),
    "big_magazine": ("common", "Big Quiver"),
    "tough": ("common", "Tank"),
    "poison": ("common", "Toxic"),
    "straight": ("rare", "Laser"),
    "homing": ("rare", "Homing"),
    "vampire": ("rare", "Leech"),
    "jet": ("rare", "Jetpack"),
    "power": ("rare", "Power Shot"),
    "explosive": ("epic", "Explosive"),
    "pierce": ("rare", "Pierce"),
    "split": ("epic", "Splitter"),
    "knockback": ("common", "Knockback"),
    "reflect": ("rare", "Reflect"),
    "shockwave": ("rare", "Shockwave"),
    "guard_master": ("common", "Quick Guard"),
    "dash": ("common", "Dash"),
    "blink_guard": ("rare", "Blink"),
}

def hexc(h, a=255):
    if isinstance(h, tuple):
        return h if len(h) == 4 else h + (a,)
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (a,)


FLAT = True

# ---------------------------------------------------------------- 塗りの道具

def gradient(c1, c2, p1, p2, c3=None):
    """p1→p2 方向の線形グラデーション（c3 を渡すと 3 色）。"""
    ys, xs = np.mgrid[0:S, 0:S].astype(np.float32)
    dx, dy = p2[0] - p1[0], p2[1] - p1[1]
    t = ((xs - p1[0]) * dx + (ys - p1[1]) * dy) / max(dx * dx + dy * dy, 1)
    t = np.clip(t, 0, 1)[..., None]
    a, b = np.array(hexc(c1), np.float32), np.array(hexc(c2), np.float32)
    if c3 is None:
        arr = a + (b - a) * t
    else:
        c = np.array(hexc(c3), np.float32)
        arr = np.where(t < 0.5, a + (b - a) * (t * 2), b + (c - b) * ((t - 0.5) * 2))
    return Image.fromarray(arr.astype(np.uint8), "RGBA")


def radial(c1, c2, center, radius):
    ys, xs = np.mgrid[0:S, 0:S].astype(np.float32)
    t = np.clip(np.hypot(xs - center[0], ys - center[1]) / radius, 0, 1)[..., None]
    a, b = np.array(hexc(c1), np.float32), np.array(hexc(c2), np.float32)
    return Image.fromarray((a + (b - a) * t).astype(np.uint8), "RGBA")


def fill(layer, mask, paint):
    """mask の形に paint（色 or 画像）を塗る。"""
    if not isinstance(paint, Image.Image):
        paint = Image.new("RGBA", (S, S), hexc(paint))
    src = paint.copy()
    alpha = np.asarray(src.getchannel("A"), np.float32) * (np.asarray(mask, np.float32) / 255)
    src.putalpha(Image.fromarray(alpha.astype(np.uint8)))
    layer.alpha_composite(src)


def mask_of(draw_fn):
    m = Image.new("L", (S, S), 0)
    draw_fn(ImageDraw.Draw(m))
    return m


def poly(layer, pts, paint):
    fill(layer, mask_of(lambda d: d.polygon(pts, fill=255)), paint)


def ellipse(layer, box, paint):
    fill(layer, mask_of(lambda d: d.ellipse(box, fill=255)), paint)


def line(layer, pts, width, paint, round_caps=True):
    def draw(d):
        d.line(pts, fill=255, width=int(width), joint="curve")
        if round_caps:
            for x, y in (pts[0], pts[-1]):
                d.ellipse((x - width / 2, y - width / 2, x + width / 2, y + width / 2), fill=255)
    fill(layer, mask_of(draw), paint)


def ring(layer, c, r, width, paint):
    x, y = c
    fill(layer, mask_of(lambda d: d.ellipse((x - r, y - r, x + r, y + r), outline=255, width=int(width))), paint)


def sparkle(layer, c, r, paint):
    x, y = c
    k = r * 0.22
    poly(layer, [(x, y - r), (x + k, y - k), (x + r, y), (x + k, y + k), (x, y + r), (x - k, y + k), (x - r, y),
                 (x - k, y - k)], paint)


def glow(layer, color, radius, strength=1.0):
    """layer の形のまわりに色の光をにじませた画像を返す（layer の下に敷く）。フラットなデザインなので今は使わない。"""
    if FLAT:
        return Image.new("RGBA", (S, S), (0, 0, 0, 0))
    a = layer.getchannel("A").filter(ImageFilter.GaussianBlur(radius))
    a = Image.fromarray(np.clip(np.asarray(a, np.float32) * strength, 0, 255).astype(np.uint8))
    g = Image.new("RGBA", (S, S), hexc(color))
    g.putalpha(a)
    return g


def outlined(layer, color="#140c08", width=14):
    """絵の外側に濃い縁取りを付ける。"""
    a = layer.getchannel("A").point(lambda v: 255 if v > 40 else 0).filter(ImageFilter.MaxFilter(width | 1))
    base = Image.new("RGBA", (S, S), hexc(color))
    base.putalpha(a.filter(ImageFilter.GaussianBlur(1.5)))
    base.alpha_composite(layer)
    return base


# ---------------------------------------------------------------- 絵柄（中心 (512, 470)、-100〜100 の座標で描く）

CX, CY, K = 512, 470, 1.85


def P(x, y):
    return (CX + x * K, CY + y * K)


def W(w):
    return w * K


def arrow(layer, tail, tip, shaft=("#f5deb3", "#8b5a2b"), head=("#ffffff", "#7d8794"), fletch=("#ff6b5b", "#9e1b1b"),
          width=7):
    (x0, y0), (x1, y1) = P(*tail), P(*tip)
    ux, uy = x1 - x0, y1 - y0
    n = math.hypot(ux, uy)
    ux, uy = ux / n, uy / n
    nx, ny = -uy, ux
    hl, hw = W(26), W(13)
    bx, by = x1 - ux * hl, y1 - uy * hl
    line(layer, [(x0, y0), (bx, by)], W(width), gradient(shaft[0], shaft[1], (x0 + nx * 10, y0 + ny * 10), (x0 - nx * 10, y0 - ny * 10)))
    poly(layer, [(x1, y1), (bx + nx * hw, by + ny * hw), (bx + ux * W(5), by + uy * W(5)), (bx - nx * hw, by - ny * hw)],
         gradient(head[0], head[1], (bx + nx * hw, by + ny * hw), (bx - nx * hw, by - ny * hw)))
    fl, fw = W(24), W(12)
    for s in (1, -1):
        poly(layer, [(x0 + ux * W(4), y0 + uy * W(4)), (x0 + ux * fl, y0 + uy * fl),
                     (x0 + ux * (fl - W(6)) + s * nx * fw, y0 + uy * (fl - W(6)) + s * ny * fw),
                     (x0 - ux * W(4) + s * nx * fw, y0 - uy * W(4) + s * ny * fw)],
             gradient(fletch[0], fletch[1], (x0, y0), (x0 + s * nx * fw, y0 + s * ny * fw)))


def shield_pts(cx, cy, w, h):
    pts = []
    for i in range(41):
        t = i / 40
        x = -w + 2 * w * t
        pts.append(P(cx + x, cy - h * 0.55 + abs(x) * 0.08))
    for i in range(41):
        t = i / 40
        a = math.pi * t
        pts.append(P(cx + w * math.cos(a), cy + h * 0.1 + h * 0.9 * math.sin(a) ** 1.2))
    return pts


def heart_pts(cx, cy, s):
    pts = []
    for i in range(120):
        t = 2 * math.pi * i / 120
        x = 16 * math.sin(t) ** 3
        y = -(13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t))
        pts.append(P(cx + x * s, cy + y * s))
    return pts


def emblem(name):
    L = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    accent = "#ffd36b"
    if name == "spread":
        for tail, tip in (((-18, 88), (-78, -66)), ((0, 92), (0, -92)), ((18, 88), (78, -66))):
            arrow(L, tail, tip, shaft=("#ffe9a0", "#b7862a"), fletch=("#ffd34d", "#c26a00"))
        accent = "#ffcf40"
    elif name == "bounce":
        poly(L, [P(-98, -92), P(-64, -92), P(-64, 92), P(-98, 92)], gradient("#c9ced6", "#59606b", P(-98, 0), P(-64, 0)))
        for y in range(-80, 92, 26):
            line(L, [P(-98, y), P(-64, y)], W(2.5), "#3d424a", round_caps=False)
        for i in range(10):
            t0, t1 = i / 10, (i + 0.55) / 10
            line(L, [P(88 - 150 * t0, -82 + 82 * t0), P(88 - 150 * t1, -82 + 82 * t1)], W(5), "#b5f28a")
        sparkle(L, P(-60, 0), W(20), "#ffffff")
        arrow(L, (-56, 4), (86, 80), shaft=("#c8ff9e", "#3f8f1e"), fletch=("#8cff6a", "#2f7a12"))
        accent = "#9cff6a"
    elif name == "quick_reload":
        poly(L, [P(-14, -98), P(14, -98), P(14, -78), P(-14, -78)], gradient("#ffe58a", "#a8761c", P(-14, 0), P(14, 0)))
        ellipse(L, (*P(-76, -72), *P(76, 80)), gradient("#fff1a8", "#a0701a", P(-76, -72), P(76, 80)))
        ellipse(L, (*P(-62, -58), *P(62, 66)), radial("#ffffff", "#eadcb8", P(-10, -10), W(80)))
        for i in range(12):
            a = math.pi * 2 * i / 12
            r0 = 48 if i % 3 else 42
            line(L, [P(math.cos(a) * r0, 4 + math.sin(a) * r0), P(math.cos(a) * 55, 4 + math.sin(a) * 55)],
                 W(3 if i % 3 else 5), "#5a4630")
        line(L, [P(0, 4), P(0, -44)], W(6), "#2b2230")
        line(L, [P(0, 4), P(36, 22)], W(5), "#d8352a")
        ellipse(L, (*P(-7, -3), *P(7, 11)), "#2b2230")
        for r, a0 in ((92, 150), (100, 165)):
            pts = [P(r * math.cos(math.radians(a)), 4 + r * math.sin(math.radians(a))) for a in range(a0, a0 + 50, 5)]
            line(L, pts, W(4), "#fff4c8")
        accent = "#ffe08a"
    elif name == "big_magazine":
        for i, x in enumerate((-30, -15, 0, 15, 30)):
            top = -96 + (i % 2) * 10
            line(L, [P(x * 0.9, -10), P(x, top + 22)], W(5), gradient("#f5deb3", "#8b5a2b", P(x - 4, 0), P(x + 4, 0)))
            col = ("#ff6b5b", "#9e1b1b") if i % 2 == 0 else ("#ffffff", "#9aa6b8")
            poly(L, [P(x - 9, top), P(x + 9, top), P(x + 6, top + 26), P(x - 6, top + 26)],
                 gradient(col[0], col[1], P(x - 9, top), P(x + 9, top + 26)))
        poly(L, [P(-50, -16), P(50, -26), P(58, 92), P(-40, 96)], gradient("#c98a4b", "#5b3416", P(-50, 0), P(58, 0)))
        poly(L, [P(-50, -16), P(50, -26), P(51, -10), P(-49, 0)], gradient("#ffe58a", "#a8761c", P(0, -26), P(0, 0)))
        poly(L, [P(-46, 50), P(55, 44), P(56, 58), P(-44, 64)], gradient("#ffe58a", "#a8761c", P(0, 44), P(0, 64)))
        ellipse(L, (*P(0, 46), *P(14, 60)), "#fff3c4")
        line(L, [P(-38, 0), P(-30, 90)], W(3), "#3a2010")
        accent = "#ffbf6b"
    elif name == "tough":
        poly(L, heart_pts(0, -6, 5.4), gradient("#e9edf3", "#7c8592", P(-80, -80), P(80, 80)))
        poly(L, heart_pts(0, -4, 4.4), radial("#ff6b6b", "#8f0d1b", P(-20, -30), W(95)))
        poly(L, [P(-8, -38), P(8, -38), P(8, -8), P(38, -8), P(38, 8), P(8, 8), P(8, 38), P(-8, 38), P(-8, 8),
                 P(-38, 8), P(-38, -8), P(-8, -8)], gradient("#ffffff", "#ffd7d7", P(0, -38), P(0, 38)))
        ellipse(L, (*P(-52, -52), *P(-28, -36)), (255, 255, 255, 150))
        accent = "#ff6b6b"
    elif name == "poison":
        poly(L, [P(-20, -72), P(20, -72), P(20, -26), P(-20, -26)], (220, 255, 230, 120))
        poly(L, [P(-25, -96), P(25, -96), P(22, -70), P(-22, -70)], gradient("#c98a4b", "#6b3d18", P(0, -96), P(0, -70)))
        ellipse(L, (*P(-66, -38), *P(66, 94)), (225, 255, 235, 110))
        liquid = mask_of(lambda d: d.ellipse((*P(-58, -30), *P(58, 86)), fill=255))
        liquid.paste(0, (0, 0, S, int(P(0, -2)[1])))
        fill(L, liquid, radial("#c6ff6b", "#1c7a1c", P(-15, 30), W(80)))
        for x, y, r in ((-20, 40, 8), (14, 20, 6), (24, 56, 10), (-6, 10, 4), (8, -48, 6), (-10, -60, 4)):
            ring(L, P(x, y), W(r), W(2.5), (230, 255, 220, 230))
        ellipse(L, (*P(-46, -18), *P(-30, 26)), (255, 255, 255, 140))
        accent = "#7dff4a"
    elif name == "straight":
        beam = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        line(beam, [P(-100, 0), P(100, 0)], W(22), "#40e8ff")
        L.alpha_composite(glow(beam, "#40e8ff", 30, 1.6))
        line(L, [P(-100, 0), P(100, 0)], W(8), "#e8ffff")
        arrow(L, (-70, 0), (96, 0), shaft=("#ffffff", "#7fe6ff"), head=("#ffffff", "#7fe6ff"), fletch=("#7fe6ff", "#0e7ea8"))
        for x in (-90, -40, 30):
            sparkle(L, P(x, -26 if x % 20 else 24), W(10), "#ffffff")
        accent = "#40e8ff"
    elif name == "homing":
        for r, c in ((62, "#e8303a"), (48, "#ffffff"), (34, "#e8303a"), (20, "#ffffff"), (8, "#e8303a")):
            ellipse(L, (*P(34 - r, -34 - r), *P(34 + r, -34 + r)), c)
        pts = []
        for i in range(0, 21):
            t = i / 20
            x = (1 - t) ** 2 * -88 + 2 * (1 - t) * t * -70 + t * t * 4
            y = (1 - t) ** 2 * 92 + 2 * (1 - t) * t * -10 + t * t * -14
            pts.append((x, y))
        for i in range(0, 12, 2):
            line(L, [P(*pts[i]), P(*pts[i + 1])], W(5), "#d9a6ff")
        arrow(L, pts[12], (24, -26), shaft=("#ead2ff", "#7c3ac2"), fletch=("#c98bff", "#5a1fa0"))
        accent = "#ff6b8a"
    elif name == "vampire":
        drop = [P(0, -60)] + [P(56 * math.sin(math.radians(a)), 40 - 52 * math.cos(math.radians(a)))
                               for a in range(40, 321, 8)]
        poly(L, drop, radial("#ff4a5a", "#5c0010", P(-15, 20), W(90)))
        ellipse(L, (*P(-36, 20), *P(-22, 52)), (255, 255, 255, 150))
        poly(L, [P(-70, -96), P(70, -96), P(64, -80), P(-64, -80)], gradient("#8a1020", "#3a0008", P(0, -96), P(0, -80)))
        for x in (-34, 34):
            poly(L, [P(x - 14, -82), P(x + 14, -82), P(x, -44)], gradient("#ffffff", "#c8c0c0", P(x - 14, 0), P(x + 14, 0)))
        accent = "#ff3a4a"
    elif name == "jet":
        flame = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        poly(flame, [P(-26, 46), P(26, 46), P(0, 100)], gradient("#fff27a", "#ff3d00", P(0, 46), P(0, 100)))
        poly(flame, [P(-14, 46), P(14, 46), P(0, 80)], "#ffffff")
        L.alpha_composite(glow(flame, "#ff8a00", 18, 1.4))
        L.alpha_composite(flame)
        poly(L, [P(-34, 20), P(-60, 56), P(-30, 50)], gradient("#ff6b5b", "#9e1b1b", P(-60, 0), P(-30, 0)))
        poly(L, [P(34, 20), P(60, 56), P(30, 50)], gradient("#ff6b5b", "#9e1b1b", P(30, 0), P(60, 0)))
        body = [P(0, -96)] + [P(34 * math.sin(math.radians(a)) * (1 if a < 180 else 1), -40 - 56 * math.cos(math.radians(a)))
                              for a in range(10, 91, 8)] + [P(32, 50), P(-32, 50)] + \
               [P(-34 * math.sin(math.radians(a)), -40 - 56 * math.cos(math.radians(a))) for a in range(90, 9, -8)]
        poly(L, body, gradient("#ffffff", "#8d95a3", P(-34, 0), P(34, 0)))
        poly(L, [P(0, -96)] + [P(34 * math.sin(math.radians(a)), -40 - 56 * math.cos(math.radians(a))) for a in range(10, 50, 6)]
             + [P(-34 * math.sin(math.radians(a)), -40 - 56 * math.cos(math.radians(a))) for a in range(44, 9, -6)],
             gradient("#ff6b5b", "#9e1b1b", P(-30, 0), P(30, 0)))
        ellipse(L, (*P(-16, -26), *P(16, 6)), "#30343c")
        ellipse(L, (*P(-12, -22), *P(12, 2)), radial("#bfefff", "#1f6fd1", P(-5, -16), W(16)))
        accent = "#ffa040"
    elif name == "power":
        pts_o = [P(-30 - 52 * math.sin(math.pi * t / 20), -94 + 188 * t / 20) for t in range(21)]
        line(L, pts_o, W(14), gradient("#d9a066", "#5b3416", P(-80, 0), P(-30, 0)))
        for y in (-94, 94):
            ellipse(L, (*P(-38, y - 8), *P(-22, y + 8)), "#ffd36b")
        line(L, [P(-30, -94), P(30, 0), P(-30, 94)], W(3), "#f4f1e8", round_caps=False)
        shot = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        arrow(shot, (30, 0), (100, 0), shaft=("#fff0b0", "#ff7a00"), head=("#ffffff", "#ffb040"), fletch=("#ff8a3d", "#c22a00"))
        L.alpha_composite(glow(shot, "#ff8a00", 22, 1.5))
        L.alpha_composite(shot)
        for y in (-30, 30):
            line(L, [P(46, y), P(92, y * 0.5)], W(4), (255, 220, 150, 200))
        accent = "#ff9a3d"
    elif name == "explosive":
        def burst(r_out, r_in, n, rot):
            return [P((r_out if i % 2 == 0 else r_in) * math.cos(rot + math.pi * i / n),
                      (r_out if i % 2 == 0 else r_in) * math.sin(rot + math.pi * i / n)) for i in range(2 * n)]
        poly(L, burst(98, 56, 12, 0.1), radial("#ffb02e", "#c21a00", P(0, 0), W(100)))
        poly(L, burst(72, 40, 10, 0.4), radial("#fff3a0", "#ff8a00", P(0, 0), W(72)))
        ellipse(L, (*P(-30, -30), *P(30, 30)), radial("#ffffff", "#ffe56b", P(0, 0), W(30)))
        for a, r in ((20, 92), (140, 96), (250, 90), (320, 98)):
            ellipse(L, (*P(r * math.cos(math.radians(a)) - 5, r * math.sin(math.radians(a)) - 5),
                        *P(r * math.cos(math.radians(a)) + 5, r * math.sin(math.radians(a)) + 5)), "#ffcf40")
        accent = "#ff7a1a"
    elif name == "pierce":
        ring(L, P(0, 0), W(58), W(20), gradient("#e0c2ff", "#5a2a9e", P(-58, -58), P(58, 58)))
        arrow(L, (-92, 92), (92, -92), shaft=("#ffffff", "#b58cf0"), head=("#ffffff", "#9aa6b8"), fletch=("#d8a8ff", "#6a2ac2"))
        front = mask_of(lambda d: d.arc((*P(-58, -58), *P(58, 58)), 100, 170, fill=255, width=int(W(20))))
        fill(L, front, gradient("#e0c2ff", "#5a2a9e", P(-58, -58), P(58, 58)))
        for p in ((-30, -50), (50, 30)):
            sparkle(L, P(*p), W(12), "#ffffff")
        accent = "#c38bff"
    elif name == "split":
        c = ("#e6fffb", "#1aa89a")
        arrow(L, (-6, 6), (66, -84), shaft=c, fletch=("#7ff5e6", "#11736a"))
        arrow(L, (-6, 6), (92, -14), shaft=c, fletch=("#7ff5e6", "#11736a"))
        arrow(L, (-6, 6), (8, -98), shaft=c, fletch=("#7ff5e6", "#11736a"))
        line(L, [P(-94, 94), P(-6, 6)], W(7), gradient(c[0], c[1], P(-94, 94), P(-6, 6)))
        node = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        ellipse(node, (*P(-16, -4), *P(4, 16)), "#ffffff")
        L.alpha_composite(glow(node, "#7ff5e6", 20, 2))
        L.alpha_composite(node)
        accent = "#5ff0dc"
    elif name == "knockback":
        for y, x0 in ((-50, -98), (-16, -92), (18, -98), (52, -90)):
            line(L, [P(x0, y), P(x0 + 34, y)], W(6), (220, 235, 255, 210))
        poly(L, [P(-44, -30), P(20, -30), P(20, -70), P(96, 0), P(20, 70), P(20, 30), P(-44, 30)],
             gradient("#ffffff", "#8fb4e6", P(0, -70), P(0, 70)))
        poly(L, [P(-44, -30), P(20, -30), P(20, -70), P(30, -61), P(30, -22), P(-44, -22)], (255, 255, 255, 170))
        accent = "#a8d4ff"
    elif name == "reflect":
        poly(L, shield_pts(8, 0, 66, 96), gradient("#fff2b0", "#a8761c", P(-60, -90), P(70, 90)))
        poly(L, shield_pts(8, 2, 54, 82), gradient("#4f86ff", "#0d2a7a", P(-50, -80), P(60, 80)))
        poly(L, [P(0, -40), P(16, -40), P(16, -6), P(44, -6), P(44, 8), P(16, 8), P(16, 60), P(0, 60), P(0, 8),
                 P(-28, 8), P(-28, -6), P(0, -6)], gradient("#fff2b0", "#c9962f", P(0, -40), P(0, 60)))
        arrow(L, (-50, -8), (-96, -86))
        for i in range(4):
            line(L, [P(-58 - i * 10, 8 + i * 16), P(-62 - i * 10, 14 + i * 16)], W(5), "#ffffff")
        sparkle(L, P(-50, -4), W(18), "#fff6c8")
        accent = "#6fa0ff"
    elif name == "shockwave":
        for r, a in ((92, 130), (66, 190), (40, 255)):
            ring(L, P(0, 0), W(r), W(10), (120, 220, 255, a))
        orb = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        ellipse(orb, (*P(-22, -22), *P(22, 22)), radial("#ffffff", "#7fd8ff", P(-6, -6), W(26)))
        L.alpha_composite(glow(orb, "#7fd8ff", 24, 2))
        L.alpha_composite(orb)
        for i in range(8):
            a = math.pi * 2 * i / 8 + 0.4
            line(L, [P(math.cos(a) * 74, math.sin(a) * 74), P(math.cos(a) * 84, math.sin(a) * 84)], W(4), "#e8f8ff")
        accent = "#6fd2ff"
    elif name == "guard_master":
        poly(L, shield_pts(-12, 0, 66, 96), gradient("#f4f6fa", "#5b6370", P(-80, -90), P(60, 90)))
        poly(L, shield_pts(-12, 2, 54, 82), gradient("#c9ced6", "#4a515c", P(-60, -80), P(40, 80)))
        bolt = [P(40, -96), P(-2, 6), P(30, 6), P(4, 98), P(78, -16), P(44, -16), P(72, -96)]
        poly(L, bolt, gradient("#fff7a8", "#ffb000", P(0, -96), P(60, 98)))
        accent = "#ffd84a"
    elif name == "dash":
        for i, (c1, c2) in enumerate((("#7d8794", "#3d4450"), ("#c9d6e6", "#7d8ea6"), ("#ffffff", "#ffd84a"))):
            x = -70 + i * 44
            poly(L, [P(x, -66), P(x + 30, -66), P(x + 76, 0), P(x + 30, 66), P(x, 66), P(x + 46, 0)],
                 gradient(c1, c2, P(x, -66), P(x + 76, 66)))
        for y in (-86, 86):
            line(L, [P(-96, y), P(-40, y)], W(5), (255, 220, 120, 220))
        accent = "#ffe066"
    elif name == "blink_guard":
        for (cx, cy, r, c1, c2) in ((-48, 48, 38, "#8a4be0", "#1a0636"), (48, -48, 42, "#5ff0dc", "#0b3d52")):
            portal = Image.new("RGBA", (S, S), (0, 0, 0, 0))
            ellipse(portal, (*P(cx - r, cy - r), *P(cx + r, cy + r)), radial(c2, c1, P(cx, cy), W(r)))
            for k in range(3):
                rr = r * (0.75 - k * 0.2)
                fill(portal, mask_of(lambda d, rr=rr, k=k: d.arc((*P(cx - rr, cy - rr), *P(cx + rr, cy + rr)),
                                                                  k * 90, k * 90 + 200, fill=255, width=int(W(4)))),
                     (255, 255, 255, 170))
            L.alpha_composite(glow(portal, c1, 22, 1.5))
            L.alpha_composite(portal)
        for i in range(6):
            t = (i + 0.5) / 6
            x, y = -20 + 40 * t, 20 - 40 * t
            ellipse(L, (*P(x - 4, y - 4 - math.sin(t * math.pi) * 18), *P(x + 4, y + 4 - math.sin(t * math.pi) * 18)),
                    "#f2d9ff")
        for p, r in (((80, 40), 12), ((-80, -30), 10), ((10, 80), 8), ((-10, -84), 11)):
            sparkle(L, P(*p), W(r), "#ffffff")
        accent = "#c38bff"
    return L, accent


# ---------------------------------------------------------------- カード本体（黒地にHUD風のパネル）

CARD = (224, 16, 800, 1008)  # x0, y0, x1, y1

# レア度ごとの色。パネルの色とアクセントの2色。ほかは黒と白だけ
PALETTES = {
    "common": {"panel": "#d3d9df", "accent": "#18c29c", "label": "COMMON"},
    "rare": {"panel": "#3f9be0", "accent": "#ff1f8e", "label": "RARE"},
    "epic": {"panel": "#ff1f8e", "accent": "#ffd400", "label": "EPIC"},
}
INK = "#0d0d10"
WHITE = "#ffffff"

BOLD_FONTS = ["/usr/share/fonts/opentype/inter/InterDisplay-BlackItalic.otf",
              "/usr/share/fonts/opentype/inter/Inter-BlackItalic.otf",
              "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"]
MONO_FONTS = ["/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf",
              "/usr/share/fonts/truetype/liberation/LiberationMono-Bold.ttf"]


def load_font(paths, size):
    for path in paths:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def text_mask(text, size, xy, anchor="mm", mono=False, angle=0.0):
    f = load_font(MONO_FONTS if mono else BOLD_FONTS, size)
    m = Image.new("L", (S, S), 0)
    ImageDraw.Draw(m).text(xy, text, font=f, fill=255, anchor=anchor)
    if angle:
        m = m.rotate(angle, resample=Image.BICUBIC, center=xy)
    return m


def chamfer(x0, y0, x1, y1, cuts):
    """角を斜めに落とした四角形。cuts = (左上, 右上, 右下, 左下) の切り落とす長さ。"""
    a, b, c, d = cuts
    return [(x0 + a, y0), (x1 - b, y0), (x1, y0 + b), (x1, y1 - c), (x1 - c, y1), (x0 + d, y1), (x0, y1 - d), (x0, y0 + a)]


def hatch(layer, box, paint, step=26, width=12, slope=1.0):
    """box の中に斜線を引く。"""
    x0, y0, x1, y1 = box
    m = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(m)
    h = y1 - y0
    x = x0 - h * slope
    while x < x1:
        d.polygon([(x, y1), (x + width, y1), (x + width + h * slope, y0), (x + h * slope, y0)], fill=255)
        x += step
    clip = mask_of(lambda dd: dd.rectangle(box, fill=255))
    m = Image.fromarray(np.minimum(np.asarray(m), np.asarray(clip)))
    fill(layer, m, paint)


def cross(layer, c, r, w, paint, plus=False):
    x, y = c
    if plus:
        poly(layer, [(x - w, y - r), (x + w, y - r), (x + w, y + r), (x - w, y + r)], paint)
        poly(layer, [(x - r, y - w), (x + r, y - w), (x + r, y + w), (x - r, y + w)], paint)
    else:
        line(layer, [(x - r, y - r), (x + r, y + r)], w * 2, paint, round_caps=False)
        line(layer, [(x - r, y + r), (x + r, y - r)], w * 2, paint, round_caps=False)


def corner_arrow(layer, c, r, paint):
    """左下向きの矢印（↙）。"""
    x, y = c
    line(layer, [(x + r, y - r), (x - r * 0.55, y + r * 0.55)], r * 0.36, paint, round_caps=False)
    poly(layer, [(x - r, y + r), (x - r, y - r * 0.1), (x + r * 0.1, y + r)], paint)


def flatten(art, accent):
    """絵柄を 黒・白・アクセント の3色だけに塗り分け、黒い縁取りを付ける。"""
    arr = np.asarray(art, np.float32)
    lum = arr[..., 0] * 0.3 + arr[..., 1] * 0.59 + arr[..., 2] * 0.11
    out = np.zeros_like(arr)
    for lo, hi, c in ((-1, 80, INK), (80, 165, accent), (165, 256, WHITE)):
        out[(lum > lo) & (lum <= hi)] = hexc(c)
    alpha = np.where(arr[..., 3] > 110, 255, 0).astype(np.uint8)
    out[..., 3] = alpha
    flat = Image.fromarray(out.astype(np.uint8), "RGBA")
    edge = Image.fromarray(alpha).filter(ImageFilter.MaxFilter(17))
    base = Image.new("RGBA", (S, S), hexc(INK))
    base.putalpha(edge)
    base.alpha_composite(flat)
    return base


def card_texture(index, name, rarity, title):
    custom = os.path.join(CUSTOM, name + ".png")
    if os.path.exists(custom):
        return Image.open(custom).convert("RGBA")
    pal = PALETTES[rarity]
    panel, accent = pal["panel"], pal["accent"]
    x0, y0, x1, y1 = CARD
    num = f"{index + 1:02d}"
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    poly(img, chamfer(x0, y0, x1, y1, (0, 40, 0, 40)), INK)

    # 左の細い帯：斜線ブロック、バーコード、ノイズ、＋＋
    poly(img, chamfer(244, 44, 290, 190, (0, 0, 22, 0)), accent)
    hatch(img, (244, 44, 290, 190), INK, step=34, width=10)
    for i, w in enumerate((10, 4, 6, 14, 4, 8, 4, 12, 6, 4, 10)):
        y = 220 + sum((10, 4, 6, 14, 4, 8, 4, 12, 6, 4, 10)[:i]) + i * 6
        poly(img, [(244, y), (290 - (i % 3) * 8, y), (290 - (i % 3) * 8, y + w), (244, y + w)], WHITE)
    rng = random.Random(name)
    for gy in range(400, 470, 10):
        for gx in range(244, 290, 10):
            if rng.random() < 0.55:
                poly(img, [(gx, gy), (gx + 9, gy), (gx + 9, gy + 9), (gx, gy + 9)], WHITE)
    for cx, cy in ((254, 500), (282, 500), (254, 528), (282, 528)):
        cross(img, (cx, cy), 10, 3, WHITE, plus=True)
    hatch(img, (244, 560, 290, 700), WHITE, step=30, width=9)

    # メインのパネル（左に段差、角を落とす）
    main = [(326, 40), (744, 40), (780, 76), (780, 650), (752, 678), (700, 678), (676, 700), (420, 700), (396, 678),
            (326, 678), (306, 658), (306, 470), (322, 454), (322, 300), (306, 284), (306, 60)]
    poly(img, main, panel)
    # パネル内の飾り：右の斜線、右上の×、左上の斜線、左下の↙
    hatch(img, (736, 300, 768, 560), WHITE, step=26, width=10)
    poly(img, [(736, 572), (752, 588), (768, 572), (768, 590), (752, 606), (736, 590)], accent)
    cross(img, (742, 82), 18, 6, WHITE)
    hatch(img, (338, 62, 440, 92), accent, step=30, width=14)
    corner_arrow(img, (352, 640), 22, WHITE)
    fill(img, text_mask(f"TYPE-{num} / {pal['label']}", 20, (366, 678 - 18), anchor="lm", mono=True), INK)

    # 絵柄（3色＋黒い縁取り）。パネルの中央に縮めて置く
    art, _ = emblem(name)
    art = flatten(art, accent)
    k = 0.82
    tx, ty = 543, 360
    art = art.transform((S, S), Image.AFFINE, (1 / k, 0, CX - tx / k, 0, 1 / k, CY - ty / k), resample=Image.BICUBIC)
    img.alpha_composite(art)

    # 下のパネル：名前と小さな文字
    lower = [(244, 722), (560, 722), (584, 744), (780, 744), (780, 930), (750, 960), (244, 960)]
    poly(img, lower, accent)
    size = 96
    while size > 40:
        bb = text_mask(title.upper(), size, (512, 834)).getbbox()
        if bb[2] - bb[0] <= 470:
            break
        size -= 4
    fill(img, text_mask(title.upper(), size, (512, 834), anchor="mm"), INK)
    fill(img, text_mask(f"No.{num}", 26, (262, 748), anchor="lm", mono=True), INK)
    fill(img, text_mask("BOW//ROUNDS", 22, (764, 772), anchor="rm", mono=True), INK)
    for i in range(3):
        x = 640 + i * 30
        if i < {"common": 1, "rare": 2, "epic": 3}[rarity]:
            poly(img, [(x, 912), (x + 20, 912), (x + 30, 900), (x + 10, 900)], INK)
        else:
            line(img, [(x, 912), (x + 20, 912), (x + 30, 900), (x + 10, 900), (x, 912)], 4, INK, round_caps=False)
    hatch(img, (262, 896, 600, 922), INK, step=24, width=10)

    # いちばん下：斜線の帯とVの切り欠き
    hatch(img, (244, 974, 780, 996), WHITE, step=26, width=11)
    poly(img, [(490, 974), (534, 974), (512, 996)], accent)
    poly(img, [(494, 974), (530, 974), (512, 990)], INK)
    return img.resize((OUT, OUT), Image.LANCZOS)


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def main():
    write_json(os.path.join(ROOT, "pack.mcmeta"), {
        "pack": {"description": "BowRounds カード", "min_format": 69, "max_format": 999}
    })
    tex_dir = os.path.join(ROOT, "assets", NS, "textures", "item", "card")
    os.makedirs(tex_dir, exist_ok=True)
    cols = 10
    sheet = Image.new("RGBA", (OUT * cols, OUT * 2), (24, 24, 28, 255))
    for i, (name, (rarity, title)) in enumerate(CARDS.items()):
        img = card_texture(i, name, rarity, title)
        img.save(os.path.join(tex_dir, name + ".png"))
        sheet.alpha_composite(img.resize((OUT, OUT), Image.LANCZOS), ((i % cols) * OUT, (i // cols) * OUT))
        write_json(os.path.join(ROOT, "assets", NS, "models", "item", "card", name + ".json"), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"{NS}:item/card/{name}"},
        })
        write_json(os.path.join(ROOT, "assets", NS, "items", "card", name + ".json"), {
            "model": {"type": "minecraft:model", "model": f"{NS}:item/card/{name}"},
        })
        print("drew", name)
    sheet.save(os.path.join(HERE, "preview.png"))


if __name__ == "__main__":
    main()
