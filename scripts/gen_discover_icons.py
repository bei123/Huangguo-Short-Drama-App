# -*- coding: utf-8 -*-
"""发现页图形图标：榜单 / 分类 / 频道"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parents[1] / "static" / "quick"
OUT.mkdir(parents=True, exist_ok=True)
SIZE = 96
PAD = 14


def blank():
	return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def soft_glow(base, color, alpha=36):
	mask = base.split()[-1]
	solid = Image.new("RGBA", (SIZE, SIZE), (color[0], color[1], color[2], alpha))
	solid.putalpha(mask)
	solid = solid.filter(ImageFilter.GaussianBlur(radius=2.0))
	out = blank()
	out = Image.alpha_composite(out, solid)
	out = Image.alpha_composite(out, base)
	return out


def save(img, name):
	p = OUT / name
	img.save(p, "PNG")
	print("wrote", p)


def draw_pick(color, soft):
	"""推荐：星形"""
	img = blank()
	d = ImageDraw.Draw(img)
	cx, cy = SIZE // 2, SIZE // 2 + 2
	# 五角星近似：外圆点 + 内多边形
	import math
	pts = []
	for i in range(10):
		ang = math.radians(-90 + i * 36)
		r = 34 if i % 2 == 0 else 15
		pts.append((cx + r * math.cos(ang), cy + r * math.sin(ang)))
	d.polygon(pts, fill=soft, outline=color)
	d.line(pts + [pts[0]], fill=color, width=4)
	return soft_glow(img, color)


def draw_potential(color, soft):
	"""潜力：上升折线 + 圆点"""
	img = blank()
	d = ImageDraw.Draw(img)
	base = SIZE - PAD - 6
	d.line([(PAD + 4, base), (SIZE - PAD - 4, base)], fill=soft, width=4)
	pts = [(PAD + 8, base - 8), (36, base - 28), (56, base - 20), (SIZE - PAD - 8, PAD + 16)]
	d.line(pts, fill=color, width=5, joint="curve")
	for x, y in pts:
		d.ellipse([x - 5, y - 5, x + 5, y + 5], fill=color)
	return soft_glow(img, color)


def draw_cate(color, soft):
	"""分类：四宫格"""
	img = blank()
	d = ImageDraw.Draw(img)
	gap = 8
	side = (SIZE - 2 * PAD - gap) // 2
	coords = [
		(PAD, PAD),
		(PAD + side + gap, PAD),
		(PAD, PAD + side + gap),
		(PAD + side + gap, PAD + side + gap),
	]
	for x, y in coords:
		d.rounded_rectangle([x, y, x + side, y + side], radius=8, fill=soft, outline=color, width=4)
	return soft_glow(img, color)


def draw_posts(color, soft):
	"""资讯：文档"""
	img = blank()
	d = ImageDraw.Draw(img)
	d.rounded_rectangle([PAD + 10, PAD, SIZE - PAD - 10, SIZE - PAD], radius=8, fill=soft, outline=color, width=4)
	for i, w in enumerate([40, 34, 28]):
		y = PAD + 22 + i * 14
		d.rounded_rectangle([PAD + 22, y, PAD + 22 + w, y + 6], radius=3, fill=color)
	return soft_glow(img, color)


def draw_feed(color, soft):
	"""信息流：三波浪"""
	img = blank()
	d = ImageDraw.Draw(img)
	import math
	for i, y0 in enumerate([28, 48, 68]):
		pts = []
		amp = 8 - i
		for x in range(PAD, SIZE - PAD + 1, 2):
			t = (x - PAD) / float(SIZE - 2 * PAD) * math.pi * 2
			pts.append((x, y0 + amp * math.sin(t)))
		d.line(pts, fill=color if i == 1 else soft, width=5 if i == 1 else 4)
	return soft_glow(img, color)


def draw_original(color, soft):
	"""原创：笔尖"""
	img = blank()
	d = ImageDraw.Draw(img)
	# 笔身
	d.polygon([(38, PAD + 4), (58, PAD + 10), (42, SIZE - PAD - 8), (28, SIZE - PAD - 18)], fill=soft, outline=color)
	d.line([(38, PAD + 4), (58, PAD + 10), (42, SIZE - PAD - 8), (28, SIZE - PAD - 18), (38, PAD + 4)], fill=color, width=4)
	# 笔尖
	d.polygon([(28, SIZE - PAD - 18), (42, SIZE - PAD - 8), (22, SIZE - PAD)], fill=color)
	return soft_glow(img, color)


def draw_long(color, soft):
	"""长视频：胶片"""
	img = blank()
	d = ImageDraw.Draw(img)
	d.rounded_rectangle([PAD, PAD + 12, SIZE - PAD, SIZE - PAD - 12], radius=8, fill=soft, outline=color, width=4)
	# 齿孔
	for y in [PAD + 22, PAD + 40, PAD + 58]:
		d.rounded_rectangle([PAD + 6, y, PAD + 16, y + 10], radius=2, fill=color)
		d.rounded_rectangle([SIZE - PAD - 16, y, SIZE - PAD - 6, y + 10], radius=2, fill=color)
	# 中窗
	d.rounded_rectangle([PAD + 24, PAD + 24, SIZE - PAD - 24, SIZE - PAD - 24], radius=4, outline=color, width=3)
	return soft_glow(img, color)


GOLD = (232, 194, 122, 255)
GOLD_SOFT = (232, 194, 122, 70)
BLUE = (122, 158, 200, 255)
BLUE_SOFT = (122, 158, 200, 60)
MINT = (155, 196, 168, 255)
MINT_SOFT = (155, 196, 168, 60)
GRAY = (200, 204, 211, 255)
GRAY_SOFT = (200, 204, 211, 55)
CORAL = (232, 150, 122, 255)
CORAL_SOFT = (232, 150, 122, 60)
LILAC = (176, 122, 200, 255)
LILAC_SOFT = (176, 122, 200, 55)

save(draw_pick(BLUE, BLUE_SOFT), "pick.png")
save(draw_potential(MINT, MINT_SOFT), "potential.png")
save(draw_cate(GOLD, GOLD_SOFT), "cate.png")
save(draw_posts(GRAY, GRAY_SOFT), "posts.png")
save(draw_feed(CORAL, CORAL_SOFT), "feed.png")
save(draw_original(LILAC, LILAC_SOFT), "original.png")
save(draw_long(GOLD, GOLD_SOFT), "long.png")
