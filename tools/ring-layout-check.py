"""按 ringSlots 的同一套角度公式，画出 2-6 人的实际摆放，用来检查重叠与越界。

纯校验脚本，不参与构建。面板尺寸取自样式（宽 168，普通高约 130，自己面板多一行翻牌约 166）。
"""
import math
from PIL import Image, ImageDraw

W, H = 1080, 620          # arena 容器
RX, RY = 30, 31           # 与 ringSeats.ts 一致
PANEL_W = 168
PANEL_H = 130
MY_H = 166
BELL_R = 66

COL_BG = (250, 250, 252)
COL_PANEL = (226, 234, 245)
COL_MINE = (198, 219, 242)
COL_BELL = (255, 214, 102)
COL_OUT = (255, 120, 120)


def slots(n, self_seat=1):
    out = []
    for i in range(n):
        offset = (i - 0) % n
        rad = math.radians(90 + (360 / n) * offset)
        out.append((i + 1, math.cos(rad) * RX, math.sin(rad) * RY))
    return out


def draw_case(draw, ox, oy, n, label):
    draw.rectangle([ox, oy, ox + W, oy + H], fill=COL_BG, outline=(200, 200, 210))
    cx, cy = ox + W / 2, oy + H / 2

    # 外接椭圆参考线
    draw.ellipse(
        [cx - W * RX / 100, cy - H * RY / 100, cx + W * RX / 100, cy + H * RY / 100],
        outline=(215, 215, 225),
    )
    # 铃
    draw.ellipse([cx - BELL_R, cy - BELL_R, cx + BELL_R, cy + BELL_R], fill=COL_BELL, outline=(200, 160, 40))

    boxes = []
    for seat, dx, dy in slots(n):
        px = cx + W * dx / 100
        py = cy + H * dy / 100
        h = MY_H if seat == 1 else PANEL_H
        box = [px - PANEL_W / 2, py - h / 2, px + PANEL_W / 2, py + h / 2]
        boxes.append((seat, box))
        draw.rounded_rectangle(box, radius=14, fill=COL_MINE if seat == 1 else COL_PANEL, outline=(160, 175, 195))
        draw.text((box[0] + 8, box[1] + 6), f"seat {seat}", fill=(40, 50, 70))

    # 铃与面板、面板与面板是否重叠
    bx = [cx - BELL_R, cy - BELL_R, cx + BELL_R, cy + BELL_R]
    problems = []
    for seat, b in boxes:
        if not (b[2] < ox or b[0] > ox + W or b[3] < oy or b[1] > oy + H):
            if b[0] < ox or b[2] > ox + W or b[1] < oy or b[3] > oy + H:
                problems.append(f"seat{seat} 越界")
        if b[0] < bx[2] and b[2] > bx[0] and b[1] < bx[3] and b[3] > bx[1]:
            problems.append(f"seat{seat} 压铃")
    for i in range(len(boxes)):
        for j in range(i + 1, len(boxes)):
            a, b = boxes[i][1], boxes[j][1]
            if a[0] < b[2] and a[2] > b[0] and a[1] < b[3] and a[3] > b[1]:
                problems.append(f"seat{boxes[i][0]}∩seat{boxes[j][0]}")

    draw.text((ox + 10, oy + 8), f"{label}  {'OK' if not problems else ' / '.join(problems)}",
              fill=(180, 40, 40) if problems else (30, 120, 60))
    return problems


cases = [(n, f"{n} 人") for n in range(2, 7)]
SCALE = 0.5
tile_w, tile_h = int(W * SCALE) + 16, int(H * SCALE) + 16
canvas = Image.new("RGB", (tile_w * 2, tile_h * 3), (255, 255, 255))
d = ImageDraw.Draw(canvas)

all_problems = []
for idx, (n, label) in enumerate(cases):
    tile = Image.new("RGB", (W, H), (255, 255, 255))
    td = ImageDraw.Draw(tile)
    p = draw_case(td, 0, 0, n, label)
    all_problems += [(label, x) for x in p]
    tile = tile.resize((int(W * SCALE), int(H * SCALE)), Image.LANCZOS)
    x = (idx % 2) * tile_w + 8
    y = (idx // 2) * tile_h + 8
    canvas.paste(tile, (x, y))

out = "tools/ring-layout-check.png"
canvas.save(out)
print("problems:", all_problems if all_problems else "none")
print("saved", out)
