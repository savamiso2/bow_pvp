"""カードのテクスチャ（配信画面のUI風、黒・白・レア度の1色だけのフラットな絵、256x256）とモデル定義を生成する。

使い方: python3 resourcepack/generate.py
出力: resourcepack/pack/ 以下（これを zip にしたものがリソースパック）と resourcepack/preview.png
custom/<カード名>.png があれば、そのカードは描かずにその画像をそのまま使う。
"""
import json
import math
import os

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


# テーマは「電脳アリーナ配信」。カードは運営から支給される改造パッチで、配信画面のUIの形をしている。
# 色は 黒・白・レア度の1色 の3色だけ。グラデーションや影は使わない
INK = "#0d0d10"
WHITE = "#f5f5f0"
PALETTES = {
    "common": {"accent": "#2fe0a8", "label": "COMMON", "level": 1},
    "rare": {"accent": "#3d8bff", "label": "RARE", "level": 2},
    "epic": {"accent": "#ff2e7e", "label": "EPIC", "level": 3},
}

# ---------------------------------------------------------------- 塗りの道具


def fill(layer, mask, color):
    """mask の形に単色を塗る。"""
    src = Image.new("RGBA", (S, S), hexc(color))
    src.putalpha(mask)
    layer.alpha_composite(src)


def mask_of(draw_fn):
    m = Image.new("L", (S, S), 0)
    draw_fn(ImageDraw.Draw(m))
    return m


def poly(layer, pts, color):
    fill(layer, mask_of(lambda d: d.polygon(pts, fill=255)), color)


def ellipse(layer, box, color):
    fill(layer, mask_of(lambda d: d.ellipse(box, fill=255)), color)


def line(layer, pts, width, color, round_caps=True):
    def draw(d):
        d.line(pts, fill=255, width=int(width), joint="curve")
        if round_caps:
            for x, y in (pts[0], pts[-1]):
                d.ellipse((x - width / 2, y - width / 2, x + width / 2, y + width / 2), fill=255)
    fill(layer, mask_of(draw), color)


def ring(layer, c, r, width, color):
    x, y = c
    fill(layer, mask_of(lambda d: d.ellipse((x - r, y - r, x + r, y + r), outline=255, width=int(width))), color)


def arc(layer, c, r, a0, a1, width, color):
    x, y = c
    fill(layer, mask_of(lambda d: d.arc((x - r, y - r, x + r, y + r), a0, a1, fill=255, width=int(width))), color)


def sparkle(layer, c, r, color):
    x, y = c
    k = r * 0.22
    poly(layer, [(x, y - r), (x + k, y - k), (x + r, y), (x + k, y + k), (x, y + r), (x - k, y + k), (x - r, y),
                 (x - k, y - k)], color)


# ---------------------------------------------------------------- 絵柄（白い面＋黒い線の2色。-100〜100 の座標で描く）

CX, CY, K = 512, 372, 1.7


def P(x, y):
    return (CX + x * K, CY + y * K)


def W(w):
    return w * K


def arrow(layer, tail, tip, width=10):
    """白い矢（軸・矢じり・矢羽）。縁取りはあとでまとめて付ける。"""
    (x0, y0), (x1, y1) = P(*tail), P(*tip)
    ux, uy = x1 - x0, y1 - y0
    n = math.hypot(ux, uy)
    ux, uy = ux / n, uy / n
    nx, ny = -uy, ux
    hl, hw = W(28), W(16)
    bx, by = x1 - ux * hl, y1 - uy * hl
    line(layer, [(x0, y0), (bx + ux * 4, by + uy * 4)], W(width), WHITE, round_caps=False)
    poly(layer, [(x1, y1), (bx + nx * hw, by + ny * hw), (bx + ux * W(6), by + uy * W(6)), (bx - nx * hw, by - ny * hw)],
         WHITE)
    fl, fw = W(26), W(13)
    for s in (1, -1):
        poly(layer, [(x0 + ux * W(2), y0 + uy * W(2)), (x0 + ux * fl, y0 + uy * fl),
                     (x0 + ux * (fl - W(8)) + s * nx * fw, y0 + uy * (fl - W(8)) + s * ny * fw),
                     (x0 - ux * W(6) + s * nx * fw, y0 - uy * W(6) + s * ny * fw)], WHITE)


def shield_pts(cx, cy, w, h):
    pts = []
    for i in range(41):
        x = -w + 2 * w * i / 40
        pts.append(P(cx + x, cy - h * 0.55 + abs(x) * 0.08))
    for i in range(41):
        a = math.pi * i / 40
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


def plus_pts(cx, cy, r, w):
    return [P(cx - w, cy - r), P(cx + w, cy - r), P(cx + w, cy - w), P(cx + r, cy - w), P(cx + r, cy + w),
            P(cx + w, cy + w), P(cx + w, cy + r), P(cx - w, cy + r), P(cx - w, cy + w), P(cx - r, cy + w),
            P(cx - r, cy - w), P(cx - w, cy - w)]


def burst_pts(r_out, r_in, n, rot):
    return [P((r_out if i % 2 == 0 else r_in) * math.cos(rot + math.pi * i / n),
              (r_out if i % 2 == 0 else r_in) * math.sin(rot + math.pi * i / n)) for i in range(2 * n)]


def speed_lines(layer, rows, length=34):
    for x, y in rows:
        line(layer, [P(x, y), P(x + length, y)], W(7), WHITE)


def emblem(name):
    L = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    if name == "spread":
        for tail, tip in (((-14, 90), (-72, -70)), ((0, 92), (0, -94)), ((14, 90), (72, -70))):
            arrow(L, tail, tip)
    elif name == "bounce":
        poly(L, [P(-98, -92), P(-66, -92), P(-66, 92), P(-98, 92)], WHITE)
        for y in range(-62, 92, 30):
            line(L, [P(-98, y), P(-66, y)], W(5), INK, round_caps=False)
        for i in range(5):
            t0, t1 = i / 5, (i + 0.5) / 5
            line(L, [P(88 - 148 * t0, -84 + 84 * t0), P(88 - 148 * t1, -84 + 84 * t1)], W(8), WHITE)
        arrow(L, (-54, 6), (86, 80))
    elif name == "quick_reload":
        poly(L, [P(-16, -98), P(16, -98), P(16, -74), P(-16, -74)], WHITE)
        poly(L, [P(48, -70), P(66, -88), P(78, -76), P(60, -58)], WHITE)
        ellipse(L, (*P(-68, -62), *P(68, 74)), WHITE)
        for i in range(12):
            a = math.pi * 2 * i / 12
            r0 = 44 if i % 3 else 36
            line(L, [P(math.cos(a) * r0, 6 + math.sin(a) * r0), P(math.cos(a) * 54, 6 + math.sin(a) * 54)],
                 W(4 if i % 3 else 7), INK, round_caps=False)
        line(L, [P(0, 6), P(0, -34)], W(8), INK)
        line(L, [P(0, 6), P(28, 22)], W(8), INK)
        speed_lines(L, ((-100, -20), (-104, 10), (-100, 40)), 22)
    elif name == "big_magazine":
        for i, x in enumerate((-30, -10, 10, 30)):
            top = -96 + (i % 2) * 12
            line(L, [P(x * 0.9, -10), P(x, top + 20)], W(7), WHITE, round_caps=False)
            poly(L, [P(x - 11, top), P(x + 11, top), P(x + 7, top + 26), P(x - 7, top + 26)], WHITE)
            line(L, [P(x, top + 2), P(x, top + 24)], W(3), INK, round_caps=False)
        poly(L, [P(-50, -16), P(50, -26), P(58, 94), P(-42, 98)], WHITE)
        line(L, [P(-49, 4), P(51, -6)], W(7), INK, round_caps=False)
        line(L, [P(-45, 56), P(55, 48)], W(7), INK, round_caps=False)
    elif name == "tough":
        poly(L, heart_pts(0, -6, 5.6), WHITE)
        poly(L, plus_pts(0, -4, 34, 11), INK)
    elif name == "poison":
        poly(L, [P(-18, -84), P(18, -84), P(18, -28), P(-18, -28)], WHITE)
        poly(L, [P(-26, -100), P(26, -100), P(26, -82), P(-26, -82)], INK)
        ellipse(L, (*P(-62, -40), *P(62, 96)), WHITE)
        liquid = mask_of(lambda d: d.ellipse((*P(-48, -26), *P(48, 82)), fill=255))
        liquid.paste(0, (0, 0, S, int(P(0, 14)[1])))
        fill(L, liquid, INK)
        for x, y, r in ((-18, 50, 9), (16, 34, 6), (20, 62, 5)):
            ellipse(L, (*P(x - r, y - r), *P(x + r, y + r)), WHITE)
    elif name == "straight":
        line(L, [P(-100, 0), P(100, 0)], W(5), WHITE)
        arrow(L, (-72, 0), (98, 0), width=12)
        for x in (-86, -40, 30):
            sparkle(L, P(x, -30 if x % 20 else 30), W(13), WHITE)
    elif name == "homing":
        for r, c in ((62, WHITE), (48, INK), (36, WHITE), (22, INK), (10, WHITE)):
            ellipse(L, (*P(34 - r, -34 - r), *P(34 + r, -34 + r)), c)
        pts = []
        for i in range(21):
            t = i / 20
            pts.append(((1 - t) ** 2 * -90 + 2 * (1 - t) * t * -74 + t * t * 4,
                        (1 - t) ** 2 * 94 + 2 * (1 - t) * t * -6 + t * t * -14))
        for i in range(0, 10, 3):
            ellipse(L, (*P(pts[i][0] - 5, pts[i][1] - 5), *P(pts[i][0] + 5, pts[i][1] + 5)), WHITE)
        arrow(L, pts[11], (26, -26))
    elif name == "vampire":
        drop = [P(0, -54)] + [P(58 * math.sin(math.radians(a)), 44 - 54 * math.cos(math.radians(a)))
                               for a in range(40, 321, 8)]
        poly(L, drop, WHITE)
        line(L, [P(-34, 34), P(-28, 58)], W(7), INK)
        poly(L, [P(-74, -98), P(74, -98), P(68, -78), P(-68, -78)], WHITE)
        for x in (-38, 38):
            poly(L, [P(x - 16, -80), P(x + 16, -80), P(x, -40)], WHITE)
    elif name == "jet":
        poly(L, [P(-28, 46), P(28, 46), P(0, 102)], WHITE)
        poly(L, [P(-12, 50), P(12, 50), P(0, 78)], INK)
        poly(L, [P(-34, 16), P(-64, 58), P(-30, 50)], WHITE)
        poly(L, [P(34, 16), P(64, 58), P(30, 50)], WHITE)
        body = [P(0, -98)] + [P(34 * math.sin(math.radians(a)), -40 - 58 * math.cos(math.radians(a)))
                              for a in range(10, 91, 8)] + [P(32, 50), P(-32, 50)] + \
               [P(-34 * math.sin(math.radians(a)), -40 - 58 * math.cos(math.radians(a))) for a in range(90, 9, -8)]
        poly(L, body, WHITE)
        ellipse(L, (*P(-18, -30), *P(18, 6)), INK)
        ellipse(L, (*P(-8, -20), *P(8, -4)), WHITE)
        line(L, [P(-30, 30), P(30, 30)], W(6), INK, round_caps=False)
    elif name == "power":
        pts = [P(-24 - 50 * math.sin(math.pi * t / 20), -94 + 188 * t / 20) for t in range(21)]
        line(L, pts, W(16), WHITE)
        line(L, [P(-24, -94), P(-48, 0), P(-24, 94)], W(4), WHITE, round_caps=False)
        arrow(L, (-50, 0), (98, 0), width=12)
        speed_lines(L, ((40, -34), (40, 34)), 40)
    elif name == "explosive":
        poly(L, burst_pts(98, 58, 12, 0.1), WHITE)
        poly(L, burst_pts(66, 38, 10, 0.4), INK)
        ellipse(L, (*P(-22, -22), *P(22, 22)), WHITE)
    elif name == "pierce":
        ring(L, P(0, 0), W(58), W(18), WHITE)
        arrow(L, (-92, 92), (92, -92), width=11)
        arc(L, P(0, 0), W(58), 100, 170, W(18), WHITE)
    elif name == "split":
        line(L, [P(-94, 94), P(-6, 6)], W(10), WHITE, round_caps=False)
        for tip in ((66, -86), (94, -14), (6, -98)):
            arrow(L, (-6, 6), tip)
        ellipse(L, (*P(-22, -10), *P(10, 22)), WHITE)
        ellipse(L, (*P(-12, 0), *P(0, 12)), INK)
    elif name == "knockback":
        speed_lines(L, ((-100, -50), (-94, -16), (-100, 18), (-92, 52)))
        poly(L, [P(-44, -30), P(20, -30), P(20, -72), P(98, 0), P(20, 72), P(20, 30), P(-44, 30)], WHITE)
    elif name == "reflect":
        poly(L, shield_pts(10, 0, 66, 96), WHITE)
        poly(L, plus_pts(10, 8, 30, 10), INK)
        arrow(L, (-48, -8), (-96, -88))
        for i in range(3):
            ellipse(L, (*P(-64 - i * 12, 12 + i * 20), *P(-54 - i * 12, 22 + i * 20)), WHITE)
    elif name == "shockwave":
        for r in (92, 66, 40):
            ring(L, P(0, 0), W(r), W(10), WHITE)
        ellipse(L, (*P(-16, -16), *P(16, 16)), WHITE)
    elif name == "guard_master":
        poly(L, shield_pts(0, 0, 70, 98), WHITE)
        poly(L, [P(16, -66), P(-22, 8), P(4, 8), P(-12, 70), P(32, -12), P(6, -12), P(26, -66)], INK)
    elif name == "dash":
        for i in range(3):
            x = -78 + i * 44
            poly(L, [P(x, -66), P(x + 30, -66), P(x + 76, 0), P(x + 30, 66), P(x, 66), P(x + 46, 0)], WHITE)
    elif name == "blink_guard":
        for cx, cy, r in ((-50, 50, 38), (50, -50, 44)):
            ellipse(L, (*P(cx - r, cy - r), *P(cx + r, cy + r)), WHITE)
            ellipse(L, (*P(cx - r * 0.55, cy - r * 0.55), *P(cx + r * 0.55, cy + r * 0.55)), INK)
        for i in range(5):
            t = (i + 0.5) / 5
            x, y = -22 + 44 * t, 22 - 44 * t - math.sin(t * math.pi) * 22
            ellipse(L, (*P(x - 5, y - 5), *P(x + 5, y + 5)), WHITE)
        for p, r in (((84, 40), 14), ((-84, -30), 12)):
            sparkle(L, P(*p), W(r), WHITE)
    # まとめて黒い縁取りを付ける（白い面が背景の色に埋もれないように）
    a = L.getchannel("A").point(lambda v: 255 if v > 100 else 0)
    base = Image.new("RGBA", (S, S), hexc(INK))
    base.putalpha(a.filter(ImageFilter.MaxFilter(19)))
    base.alpha_composite(L)
    return base


# ---------------------------------------------------------------- カード本体（配信画面のUI：LIVE表示・画面・テロップ帯）

CARD = (224, 16, 800, 1008)  # x0, y0, x1, y1

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


def text_mask(text, size, xy, anchor="mm", mono=False):
    f = load_font(MONO_FONTS if mono else BOLD_FONTS, size)
    m = Image.new("L", (S, S), 0)
    ImageDraw.Draw(m).text(xy, text, font=f, fill=255, anchor=anchor)
    return m


def text(layer, s, size, xy, color, anchor="mm", mono=False):
    fill(layer, text_mask(s, size, xy, anchor, mono), color)


def chamfer(x0, y0, x1, y1, cuts):
    """角を斜めに落とした四角形。cuts = (左上, 右上, 右下, 左下) の切り落とす長さ。"""
    a, b, c, d = cuts
    return [(x0 + a, y0), (x1 - b, y0), (x1, y0 + b), (x1, y1 - c), (x1 - c, y1), (x0 + d, y1), (x0, y1 - d), (x0, y0 + a)]


def bracket(layer, x, y, dx, dy, color, size=40, width=10):
    """ファインダーの四隅のかぎ括弧。dx, dy は内側への向き（±1）。"""
    poly(layer, [(x, y), (x + dx * size, y), (x + dx * size, y + dy * width), (x + dx * width, y + dy * width),
                 (x + dx * width, y + dy * size), (x, y + dy * size)], color)


def card_texture(index, name, rarity, title):
    custom = os.path.join(CUSTOM, name + ".png")
    if os.path.exists(custom):
        return Image.open(custom).convert("RGBA")
    pal = PALETTES[rarity]
    accent, level = pal["accent"], pal["level"]
    x0, y0, x1, y1 = CARD
    num = f"{index + 1:02d}"
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    poly(img, chamfer(x0, y0, x1, y1, (0, 44, 0, 44)), INK)

    # 上の帯：LIVE 表示、チャンネル番号、電波の強さ（レア度の数だけ点く）
    poly(img, chamfer(248, 40, 392, 92, (0, 0, 14, 0)), WHITE)
    ellipse(img, (262, 54, 286, 78), accent)
    text(img, "LIVE", 34, (338, 67), INK)
    text(img, f"CH.{num}", 28, (412, 67), WHITE, anchor="lm", mono=True)
    for i in range(3):
        bx = 718 + i * 20
        h = 18 + i * 12
        poly(img, [(bx, 88 - h), (bx + 12, 88 - h), (bx + 12, 88), (bx, 88)], accent if i < level else WHITE)

    # 画面：レア度の色で塗り、四隅にかぎ括弧、REC とタイムコード
    sx0, sy0, sx1, sy1 = 248, 108, 776, 636
    poly(img, chamfer(sx0, sy0, sx1, sy1, (0, 36, 0, 36)), accent)
    for x, y, dx, dy in ((sx0 + 18, sy0 + 18, 1, 1), (sx1 - 18, sy0 + 18, -1, 1),
                         (sx0 + 18, sy1 - 18, 1, -1), (sx1 - 18, sy1 - 18, -1, -1)):
        bracket(img, x, y, dx, dy, INK)
    ellipse(img, (sx0 + 44, sy0 + 34, sx0 + 62, sy0 + 52), INK)
    text(img, "REC", 22, (sx0 + 70, sy0 + 43), INK, anchor="lm", mono=True)
    text(img, f"00:{num}:{(index * 37) % 60:02d}", 22, (sx1 - 44, sy1 - 43), INK, anchor="rm", mono=True)

    art = emblem(name)
    img.alpha_composite(art)

    # テロップ帯（ロワーサード）：白い帯にカード名、上にレア度色のタブ
    poly(img, chamfer(248, 664, 776, 790, (0, 0, 30, 0)), WHITE)
    poly(img, chamfer(248, 648, 470, 684, (0, 0, 14, 0)), accent)
    text(img, f"PATCH No.{num}", 22, (262, 666), INK, anchor="lm", mono=True)
    size = 92
    while size > 40:
        bb = text_mask(title.upper(), size, (512, 740)).getbbox()
        if bb[2] - bb[0] <= 480:
            break
        size -= 4
    text(img, title.upper(), size, (504, 740), INK)

    # その下：パッチの種類とレア度
    text(img, "MOD // BOW_ROUNDS", 22, (252, 826), WHITE, anchor="lm", mono=True)
    text(img, pal["label"], 26, (772, 826), accent, anchor="rm", mono=True)

    # いちばん下：インストールの進み具合（レア度が高いほど長い）
    filled = {1: 4, 2: 7, 3: 10}[level]
    for i in range(10):
        bx = 252 + i * 52
        if i < filled:
            poly(img, chamfer(bx, 868, bx + 44, 920, (0, 12, 0, 0)), accent)
        else:
            poly(img, [(bx, 908), (bx + 44, 908), (bx + 44, 920), (bx, 920)], WHITE)
    text(img, "INSTALL", 20, (252, 956), WHITE, anchor="lm", mono=True)
    text(img, f"{filled * 10}%", 20, (772, 956), WHITE, anchor="rm", mono=True)
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
