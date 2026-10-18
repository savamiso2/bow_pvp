"""カードのテクスチャ（32x32 のピクセルアート）とモデル定義を生成する。

使い方: python3 resourcepack/generate.py
出力: resourcepack/pack/ 以下（これを zip にしたものがリソースパック）
"""
import json
import math
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "pack")
NS = "bowrounds"

# カード名 -> レア度（Card.java と合わせる）
CARDS = {
    "spread": "common", "bounce": "common", "quick_reload": "common", "big_magazine": "common",
    "tough": "common", "poison": "common", "straight": "rare", "homing": "rare", "vampire": "rare",
    "jet": "rare", "power": "rare", "explosive": "epic", "pierce": "rare", "split": "epic",
    "knockback": "common", "reflect": "rare", "shockwave": "rare", "guard_master": "common",
    "dash": "common", "blink_guard": "rare",
}

RARITY = {
    # 外枠, 枠の明るい色, 背景(上), 背景(下), 宝石
    "common": ((40, 40, 46), (190, 196, 204), (58, 62, 72), (28, 30, 36), (230, 230, 235)),
    "rare": ((14, 28, 60), (80, 170, 255), (30, 64, 120), (12, 24, 52), (120, 220, 255)),
    "epic": ((40, 10, 56), (220, 120, 255), (86, 30, 120), (30, 8, 48), (255, 200, 80)),
}


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


class Canvas:
    """16x16 のアイコン用キャンバス。"""

    def __init__(self):
        self.img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    def px(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.img.putpixel((int(x), int(y)), rgb(c) if isinstance(c, str) else c)

    def line(self, x0, y0, x1, y1, c, dotted=False):
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err, i = dx + dy, 0
        while True:
            if not dotted or i % 2 == 0:
                self.px(x0, y0, c)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy
            i += 1

    def ring(self, cx, cy, r, c, fill=None):
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - cx, y - cy)
                if fill and d < r - 0.5:
                    self.px(x, y, fill)
                if abs(d - r) < 0.5:
                    self.px(x, y, c)

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.px(x, y, c)

    def arrow(self, x0, y0, x1, y1, shaft="#c8a070", head="#e8e8f0", fletch="#f04040"):
        """(x0,y0) が羽根、(x1,y1) が矢じり。"""
        self.line(x0, y0, x1, y1, shaft)
        ux, uy = x1 - x0, y1 - y0
        n = math.hypot(ux, uy) or 1
        ux, uy = ux / n, uy / n
        # 矢じり：先端と、少し戻って左右
        bx, by = x1 - ux * 2, y1 - uy * 2
        self.line(round(bx - uy * 1.5), round(by + ux * 1.5), x1, y1, head)
        self.line(round(bx + uy * 1.5), round(by - ux * 1.5), x1, y1, head)
        self.px(x1, y1, head)
        # 羽根
        for k in (0, 1):
            fx, fy = x0 + ux * k, y0 + uy * k
            self.px(round(fx - uy), round(fy + ux), fletch)
            self.px(round(fx + uy), round(fy - ux), fletch)


def icon(name):
    c = Canvas()
    if name == "spread":
        for tail, tip in (((5, 15), (1, 4)), ((8, 15), (8, 2)), ((11, 15), (15, 4))):
            c.arrow(*tail, *tip, shaft="#e0c060", head="#fff6a0", fletch="#ffcc33")
    elif name == "bounce":
        c.rect(0, 1, 1, 14, "#6a6a74")
        c.rect(1, 1, 1, 14, "#8a8a96")
        c.line(14, 2, 2, 8, "#9be060", dotted=True)
        c.arrow(2, 8, 13, 14, shaft="#7cc840", head="#d8ffb0", fletch="#4a9a20")
    elif name == "quick_reload":
        c.rect(7, 0, 8, 1, "#d8b040")
        c.ring(7.5, 8.5, 6, "#f0c848", fill="#fff8e0")
        c.line(8, 9, 8, 4, "#303040")
        c.line(8, 9, 11, 9, "#c03030")
        c.px(8, 9, "#303040")
        for a in range(0, 360, 90):
            c.px(round(7.5 + 4.6 * math.cos(math.radians(a))), round(8.5 + 4.6 * math.sin(math.radians(a))), "#9a8a60")
    elif name == "big_magazine":
        for i, x in enumerate((5, 7, 9, 11)):
            c.line(x, 1 + (i % 2), x, 7, "#c8a070")
            c.px(x, 0 + (i % 2), "#e8e8f0")
        c.rect(4, 7, 12, 15, "#8a5a30")
        c.rect(5, 8, 11, 14, "#a8703c")
        c.rect(4, 10, 12, 10, "#5a3a1c")
        c.px(8, 12, "#ffd860")
    elif name == "tough":
        heart = ["..XX...XX..", ".XXXX.XXXX.", "XXXXXXXXXXX", "XXXXXXXXXXX", ".XXXXXXXXX.",
                 "..XXXXXXX..", "...XXXXX...", "....XXX....", ".....X....."]
        for y, row in enumerate(heart):
            for x, ch in enumerate(row):
                if ch == "X":
                    c.px(x + 2, y + 3, "#d03030")
        for y, row in enumerate(heart):
            for x, ch in enumerate(row):
                if ch == "X":
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        xx, yy = x + dx, y + dy
                        if not (0 <= yy < len(heart) and 0 <= xx < len(row)) or heart[yy][xx] != "X":
                            c.px(x + 2, y + 3, "#c8ccd4")
        c.rect(7, 5, 7, 9, "#ffffff")
        c.rect(5, 7, 9, 7, "#ffffff")
        c.px(4, 4, "#ffffff")
    elif name == "poison":
        for y in range(3, 15):
            w = max(0, min(y - 3, 5) if y < 10 else 5 - (y - 10))
            if y >= 10:
                w = [5, 5, 4, 3, 2][y - 10]
            c.rect(8 - w, y, 7 + w, y, "#58c838")
        c.px(7, 2, "#58c838")
        c.px(8, 2, "#58c838")
        c.px(5, 9, "#b8ff90")
        c.px(5, 10, "#b8ff90")
        c.px(6, 8, "#b8ff90")
        c.px(12, 1, "#90e070")
        c.px(13, 4, "#90e070")
        c.px(2, 5, "#90e070")
    elif name == "straight":
        c.line(0, 8, 15, 8, "#40e0ff")
        c.line(0, 7, 9, 7, "#1a6a8a", dotted=True)
        c.line(1, 9, 10, 9, "#1a6a8a", dotted=True)
        c.arrow(3, 8, 15, 8, shaft="#e0ffff", head="#ffffff", fletch="#40e0ff")
    elif name == "homing":
        c.ring(10.5, 5.5, 4.5, "#e83030", fill="#ffffff")
        c.ring(10.5, 5.5, 2.5, "#e83030", fill="#ffffff")
        c.px(10, 5, "#e83030")
        c.px(11, 6, "#e83030")
        c.px(10, 6, "#e83030")
        c.px(11, 5, "#e83030")
        pts = [(1, 15), (2, 13), (3, 12), (4, 11), (5, 10)]
        for i, (x, y) in enumerate(pts):
            if i % 2 == 0:
                c.px(x, y, "#c070ff")
        c.arrow(4, 11, 8, 8, shaft="#c8a070", head="#e8e8f0", fletch="#c070ff")
    elif name == "vampire":
        for y in range(5, 15):
            w = [0, 1, 1, 2, 3, 4, 4, 4, 3, 2][y - 5]
            c.rect(8 - w, y, 7 + w, y, "#b01020")
        c.px(5, 10, "#ff6070")
        c.px(5, 11, "#ff6070")
        for x0 in (3, 10):
            c.rect(x0, 0, x0 + 2, 1, "#f4f4f4")
            c.px(x0 + 1, 2, "#f4f4f4")
            c.px(x0 + 1, 3, "#d0d0d0")
    elif name == "jet":
        c.rect(6, 3, 9, 10, "#d8dce4")
        c.rect(6, 3, 6, 10, "#ffffff")
        c.rect(7, 1, 8, 2, "#e03030")
        c.px(7, 0, "#e03030")
        c.px(8, 0, "#e03030")
        c.rect(7, 5, 8, 6, "#40a0ff")
        c.rect(4, 8, 5, 11, "#e03030")
        c.rect(10, 8, 11, 11, "#e03030")
        c.rect(6, 11, 9, 12, "#ffd040")
        c.rect(7, 13, 8, 14, "#ff8020")
        c.px(7, 15, "#ff4010")
        c.px(5, 13, "#ffd040")
        c.px(10, 14, "#ffd040")
    elif name == "power":
        for y in range(1, 15):
            x = round(3 + 4 * math.sin(math.pi * (y - 1) / 13))
            c.px(x, y, "#8a5a30")
            c.px(x - 1, y, "#5a3a1c")
        c.line(3, 1, 9, 8, "#e8e8e8")
        c.line(9, 8, 3, 14, "#e8e8e8")
        c.arrow(9, 8, 15, 8, shaft="#ffb040", head="#ffffff", fletch="#ff6020")
        c.line(9, 7, 14, 7, "#ff8020", dotted=True)
    elif name == "explosive":
        pts = []
        for i in range(16):
            r = 7 if i % 2 == 0 else 3.5
            a = math.pi * 2 * i / 16
            pts.append((7.5 + r * math.cos(a), 7.5 + r * math.sin(a)))
        for y in range(16):
            for x in range(16):
                ang = math.atan2(y - 7.5, x - 7.5) % (2 * math.pi)
                i = int(ang / (2 * math.pi) * 16)
                f = (ang / (2 * math.pi) * 16) - i
                r0 = 7 if i % 2 == 0 else 3.5
                r1 = 7 if (i + 1) % 2 == 0 else 3.5
                rr = r0 + (r1 - r0) * f
                d = math.hypot(x - 7.5, y - 7.5)
                if d < rr * 0.45:
                    c.px(x, y, "#fff8c0")
                elif d < rr * 0.75:
                    c.px(x, y, "#ffc030")
                elif d < rr:
                    c.px(x, y, "#e84a10")
    elif name == "pierce":
        c.ring(8, 8, 4.5, "#b080ff")
        c.ring(8, 8, 3.5, "#6040a0")
        c.arrow(1, 14, 14, 1, shaft="#e0d0ff", head="#ffffff", fletch="#b080ff")
    elif name == "split":
        c.line(1, 14, 7, 8, "#80f0e0")
        c.px(1, 13, "#30a090")
        c.px(2, 14, "#30a090")
        c.arrow(7, 8, 14, 1, shaft="#80f0e0", head="#e0fffa", fletch="#80f0e0")
        c.arrow(7, 8, 15, 6, shaft="#80f0e0", head="#e0fffa", fletch="#80f0e0")
        c.arrow(7, 8, 9, 0, shaft="#80f0e0", head="#e0fffa", fletch="#80f0e0")
        c.px(7, 8, "#ffffff")
    elif name == "knockback":
        for y, x0 in ((4, 0), (8, 1), (12, 0)):
            c.line(x0, y, x0 + 3, y, "#a0b0c0")
        c.rect(5, 6, 10, 10, "#f0f4ff")
        for i in range(5):
            c.line(11 + i, 3 + i, 11 + i, 13 - i, "#f0f4ff")
        c.rect(5, 6, 10, 6, "#ffffff")
        c.line(11, 3, 15, 7, "#c0c8e0")
    elif name == "reflect":
        c.rect(5, 2, 12, 9, "#4070c0")
        for y in range(10, 15):
            w = 14 - y
            c.rect(8 - w // 2 - 1, y, 9 + w // 2, y, "#4070c0")
        c.line(5, 2, 12, 2, "#d0d8e8")
        c.line(5, 2, 5, 9, "#d0d8e8")
        c.line(12, 2, 12, 9, "#d0d8e8")
        c.rect(8, 3, 9, 12, "#e8c040")
        c.rect(6, 5, 11, 6, "#e8c040")
        c.arrow(4, 6, 0, 2, shaft="#c8a070", head="#e8e8f0", fletch="#f04040")
        c.line(3, 9, 0, 12, "#ffffff", dotted=True)
    elif name == "shockwave":
        c.ring(7.5, 7.5, 7, "#4090c0")
        c.ring(7.5, 7.5, 4.6, "#80d0ff")
        c.ring(7.5, 7.5, 2.2, "#e0f8ff")
        c.rect(7, 7, 8, 8, "#ffffff")
    elif name == "guard_master":
        c.rect(3, 3, 10, 9, "#8a8e98")
        for y in range(10, 14):
            w = 13 - y
            c.rect(7 - w, y, 6 + w, y, "#8a8e98")
        c.line(3, 3, 10, 3, "#d0d4dc")
        c.line(3, 3, 3, 9, "#d0d4dc")
        bolt = [(12, 0), (11, 1), (10, 2), (9, 3), (8, 4), (9, 4), (10, 4), (11, 4), (10, 5), (9, 6), (8, 7),
                (7, 8), (11, 0), (10, 1), (9, 2), (11, 5), (10, 6), (9, 7)]
        for x, y in bolt:
            c.px(x + 2, y + 6, "#ffe040")
    elif name == "dash":
        for off, col in ((0, "#606878"), (4, "#a0a8b8"), (8, "#ffffff")):
            for i in range(4):
                c.px(2 + off + i, 4 + i, col)
                c.px(2 + off + i, 11 - i, col)
                c.px(3 + off + i, 4 + i, col)
                c.px(3 + off + i, 11 - i, col)
        c.line(0, 14, 5, 14, "#ffe060", dotted=True)
        c.line(0, 1, 4, 1, "#ffe060", dotted=True)
    elif name == "blink_guard":
        c.ring(3.5, 11.5, 2.5, "#7040b0", fill="#2a1048")
        c.ring(11.5, 4.5, 3, "#c070ff", fill="#40c0a0")
        c.px(10, 3, "#e0fff8")
        c.line(5, 9, 9, 6, "#e0b0ff", dotted=True)
        for x, y in ((14, 9), (8, 1), (1, 7), (13, 13)):
            c.px(x, y, "#f0d0ff")
    return c.img


def card_texture(name, rarity):
    outline, frame, top, bottom, gem = RARITY[rarity]
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    x0, x1, y0, y1 = 4, 27, 0, 31
    for y in range(y0, y1 + 1):
        t = (y - y0) / (y1 - y0)
        bg = tuple(round(top[i] * (1 - t) + bottom[i] * t) for i in range(3)) + (255,)
        for x in range(x0, x1 + 1):
            img.putpixel((x, y), bg)
    # 中央をほんのり明るく
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            d = math.hypot(x - 15.5, y - 14.5)
            if d < 10:
                r, g, b, a = img.getpixel((x, y))
                k = (10 - d) / 10 * 0.35
                img.putpixel((x, y), (round(r + (frame[0] - r) * k), round(g + (frame[1] - g) * k), round(b + (frame[2] - b) * k), 255))
    # 外枠と枠（角を丸める）
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            edge = min(x - x0, x1 - x, y - y0, y1 - y)
            corner = min(x - x0, x1 - x) + min(y - y0, y1 - y)
            if corner == 0:
                img.putpixel((x, y), (0, 0, 0, 0))
            elif edge == 0 or corner == 1:
                img.putpixel((x, y), outline + (255,))
            elif edge == 1 or corner == 2:
                img.putpixel((x, y), frame + (255,))
    # 上の飾り線
    for x in range(x0 + 3, x1 - 2):
        img.putpixel((x, 3), tuple(round(c * 0.7) for c in frame) + (255,))
    # アイコン（影つき）
    ic = icon(name)
    shadow = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if ic.getpixel((x, y))[3] > 0:
                shadow.putpixel((x, y), (0, 0, 0, 110))
    img.alpha_composite(shadow, (9, 8))
    img.alpha_composite(ic, (8, 7))
    # 下部にレア度の宝石（コモン1・レア2・エピック3）
    count = {"common": 1, "rare": 2, "epic": 3}[rarity]
    start = 16 - count * 2 + 1
    for i in range(count):
        gx = start + i * 4 - 1
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            img.putpixel((gx + dx, 26 + dy), gem + (255,))
        img.putpixel((gx, 26), (255, 255, 255, 255))
    return img


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def main():
    write_json(os.path.join(ROOT, "pack.mcmeta"), {
        "pack": {
            "description": "BowRounds カード",
            "min_format": 69,
            "max_format": 999,
        }
    })
    tex_dir = os.path.join(ROOT, "assets", NS, "textures", "item", "card")
    os.makedirs(tex_dir, exist_ok=True)
    sheet = Image.new("RGBA", (32 * 10, 32 * 2), (0, 0, 0, 0))
    for i, (name, rarity) in enumerate(CARDS.items()):
        img = card_texture(name, rarity)
        img.save(os.path.join(tex_dir, name + ".png"))
        sheet.alpha_composite(img, ((i % 10) * 32, (i // 10) * 32))
        write_json(os.path.join(ROOT, "assets", NS, "models", "item", "card", name + ".json"), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"{NS}:item/card/{name}"},
        })
        write_json(os.path.join(ROOT, "assets", NS, "items", "card", name + ".json"), {
            "model": {"type": "minecraft:model", "model": f"{NS}:item/card/{name}"},
        })
    preview = os.path.join(os.path.dirname(ROOT), "preview.png")
    big = Image.new("RGBA", (sheet.width * 6, sheet.height * 6), (24, 24, 28, 255))
    big.alpha_composite(sheet.resize((sheet.width * 6, sheet.height * 6), Image.NEAREST))
    big.save(preview)
    print("wrote", len(CARDS), "cards and", preview)


if __name__ == "__main__":
    main()
