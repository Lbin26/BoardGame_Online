"""把白底水果图抠成透明底正方形图标（纯 Pillow，无 numpy）。

策略：从四条边出发做泛洪填充，只吃掉与画布边缘连通的浅色像素。
主体内部的高光白斑不与边缘连通，因此不会被误抠。
"""
import os
from collections import deque
from PIL import Image

SRC_DIR = "design/games/halli-galli/art-src"
OUT_DIR = "boardgame-ui/public/cards/halli-galli/fruits"
WORK = 768          # 处理分辨率，最终输出 256 足够
LUM_MIN = 222       # 背景亮度下限
SAT_MAX = 26        # 背景饱和度上限（灰/白都低饱和）
MARGIN = 0.06       # 裁切后四周留白比例

NAMES = ["apple", "lemon", "cherry", "grape"]


def is_bg(px):
    r, g, b = px[0], px[1], px[2]
    lum = (r * 299 + g * 587 + b * 114) // 1000
    sat = max(r, g, b) - min(r, g, b)
    return lum >= LUM_MIN and sat <= SAT_MAX


def wipe_watermark(im):
    """右下角是灰阶水印（豆包 AI 生成）。先把它所在的低饱和区域刷白，
    否则泛洪吃不到、会被算进主体 bbox 导致裁切框顶到画布边缘。"""
    w, h = im.size
    px = im.load()
    x0, y0 = int(w * 0.60), int(h * 0.85)
    for y in range(y0, h):
        for x in range(x0, w):
            r, g, b = px[x, y][:3]
            if max(r, g, b) - min(r, g, b) <= 42:
                px[x, y] = (255, 255, 255)


def flood_bg(im):
    w, h = im.size
    px = im.load()
    seen = bytearray(w * h)
    q = deque()

    def push(x, y):
        i = y * w + x
        if not seen[i] and is_bg(px[x, y]):
            seen[i] = 1
            q.append((x, y))

    for x in range(w):
        push(x, 0)
        push(x, h - 1)
    for y in range(h):
        push(0, y)
        push(w - 1, y)

    while q:
        x, y = q.popleft()
        if x > 0:
            push(x - 1, y)
        if x < w - 1:
            push(x + 1, y)
        if y > 0:
            push(x, y - 1)
        if y < h - 1:
            push(x, y + 1)
    return seen


def process(name):
    src = os.path.join(SRC_DIR, name + ".jpg")
    im = Image.open(src).convert("RGB")
    im.thumbnail((WORK, WORK), Image.LANCZOS)
    wipe_watermark(im)
    w, h = im.size

    seen = flood_bg(im)
    alpha = Image.new("L", (w, h), 255)
    ap = alpha.load()
    for y in range(h):
        base = y * w
        for x in range(w):
            if seen[base + x]:
                ap[x, y] = 0

    rgba = im.convert("RGBA")
    rgba.putalpha(alpha)

    bbox = rgba.getbbox()
    if bbox is None:
        raise RuntimeError(name + ": 抠图后为空")
    trimmed = rgba.crop(bbox)

    tw, th = trimmed.size
    side = int(max(tw, th) * (1 + MARGIN * 2))
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(trimmed, ((side - tw) // 2, (side - th) // 2), trimmed)

    out = canvas.resize((256, 256), Image.LANCZOS)
    os.makedirs(OUT_DIR, exist_ok=True)
    dst = os.path.join(OUT_DIR, name + ".webp")
    out.save(dst, "WEBP", quality=92, method=6)
    print("%-8s bbox=%s -> %s (%d bytes)" % (name, bbox, dst, os.path.getsize(dst)))


if __name__ == "__main__":
    for n in NAMES:
        process(n)
