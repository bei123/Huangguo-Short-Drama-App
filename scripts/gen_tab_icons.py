# -*- coding: utf-8 -*-
"""液态玻璃风格 Tab 图标：透明底 + 柔和描边/内高光。"""
from PIL import Image, ImageDraw, ImageFilter
import math
import os

OUT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "static", "tab"))
os.makedirs(OUT, exist_ok=True)

SIZE = 96
PAD = 16
STROKE = 5
GRAY = (160, 164, 172, 255)
GOLD = (232, 194, 122, 255)
GOLD_SOFT = (232, 194, 122, 90)
GRAY_SOFT = (160, 164, 172, 70)


def blank():
	return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def save(img, name):
	path = os.path.join(OUT, name)
	img.save(path, "PNG")
	print("wrote", path)


def soft_glow(base, color, alpha=40):
	"""在线稿外加一圈极淡光晕，贴近玻璃高光。"""
	glow = blank()
	g = ImageDraw.Draw(glow)
	# 复制非透明像素并膨胀模糊
	mask = base.split()[-1]
	solid = Image.new("RGBA", (SIZE, SIZE), (color[0], color[1], color[2], alpha))
	solid.putalpha(mask)
	solid = solid.filter(ImageFilter.GaussianBlur(radius=2.2))
	out = blank()
	out = Image.alpha_composite(out, solid)
	out = Image.alpha_composite(out, base)
	return out


def draw_home(color, soft):
	img = blank()
	d = ImageDraw.Draw(img)
	left = PAD + 6
	right = SIZE - PAD - 6
	bottom = SIZE - PAD - 4
	peak = (SIZE // 2, PAD + 2)
	# 屋顶填充轻微透明
	d.polygon([peak, (left - 4, 46), (right + 4, 46)], fill=soft)
	d.line([(left - 4, 46), peak, (right + 4, 46)], fill=color, width=STROKE, joint="curve")
	# 墙体半透明底
	d.rounded_rectangle([left, 44, right, bottom], radius=4, fill=soft, outline=color, width=STROKE - 1)
	# 门
	dw = 12
	dx0 = SIZE // 2 - dw // 2
	dx1 = SIZE // 2 + dw // 2
	d.rounded_rectangle([dx0, bottom - 22, dx1, bottom], radius=2, outline=color, width=STROKE - 2)
	return soft_glow(img, color)


def draw_cate(color, soft):
	img = blank()
	d = ImageDraw.Draw(img)
	gap = 9
	side = (SIZE - 2 * PAD - gap) // 2
	coords = [
		(PAD, PAD),
		(PAD + side + gap, PAD),
		(PAD, PAD + side + gap),
		(PAD + side + gap, PAD + side + gap),
	]
	for x, y in coords:
		d.rounded_rectangle([x, y, x + side, y + side], radius=9, fill=soft, outline=color, width=STROKE - 1)
	return soft_glow(img, color)


def draw_search(color, soft):
	img = blank()
	d = ImageDraw.Draw(img)
	cx = SIZE // 2 - 4
	cy = SIZE // 2 - 6
	rad = 22
	# 玻璃盘面
	d.ellipse([cx - rad, cy - rad, cx + rad, cy + rad], fill=soft, outline=color, width=STROKE)
	# 高光弧
	hi = (min(255, color[0] + 30), min(255, color[1] + 30), min(255, color[2] + 20), 180)
	d.arc([cx - rad + 4, cy - rad + 4, cx + rad - 10, cy + rad - 10], start=200, end=300, fill=hi, width=2)
	ang = math.radians(45)
	x0 = cx + rad * math.cos(ang) * 0.78
	y0 = cy + rad * math.sin(ang) * 0.78
	x1 = cx + rad * math.cos(ang) + 15
	y1 = cy + rad * math.sin(ang) + 15
	d.line([(x0, y0), (x1, y1)], fill=color, width=STROKE + 1)
	return soft_glow(img, color)


def draw_mine(color, soft):
	img = blank()
	d = ImageDraw.Draw(img)
	cx = SIZE // 2
	head_r = 13
	hy = PAD + head_r + 2
	d.ellipse([cx - head_r, hy - head_r, cx + head_r, hy + head_r], fill=soft, outline=color, width=STROKE)
	# 身体胶囊
	body_box = [PAD + 8, hy + head_r + 6, SIZE - PAD - 8, SIZE - PAD]
	d.rounded_rectangle(body_box, radius=18, fill=soft, outline=color, width=STROKE)
	return soft_glow(img, color)


pairs = [
	("home.png", "home-active.png", draw_home),
	("cate.png", "cate-active.png", draw_cate),
	("search.png", "search-active.png", draw_search),
	("mine.png", "mine-active.png", draw_mine),
]

for inactive, active, fn in pairs:
	save(fn(GRAY, GRAY_SOFT), inactive)
	save(fn(GOLD, GOLD_SOFT), active)

print("done")
