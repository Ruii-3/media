# -*- coding: utf-8 -*-
"""Generate the CP4 UI design sketch with car-HMI dimension annotations."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "docs" / "week4_ui_final.png"
OUTPUT = ROOT / "docs" / "UI_Design_Sketch.png"

FONT_REGULAR = "C:/Windows/Fonts/msyh.ttc"
FONT_BOLD = "C:/Windows/Fonts/msyhbd.ttc"

INK = "#2C2140"
MUTED = "#5B4C73"
TERTIARY = "#6E6184"
WISTERIA = "#613F91"
WISTERIA_LIGHT = "#F2EBFB"
BORDER = "#D8C8EC"
PAGE = "#FAF7FE"
WHITE = "#FFFFFF"


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REGULAR, size)


def text(draw, xy, value, size=24, color=INK, bold=False, anchor="la"):
    draw.text(xy, value, font=font(size, bold), fill=color, anchor=anchor)


def marker(draw, xy, number, radius=18):
    x, y = xy
    draw.ellipse(
        (x - radius, y - radius, x + radius, y + radius),
        fill=WISTERIA,
        outline=WHITE,
        width=3,
    )
    text(draw, (x, y - 1), str(number), 20, WHITE, True, "mm")


def vertical_bracket(draw, x, y1, y2, label):
    draw.line((x, y1, x, y2), fill=WISTERIA, width=3)
    draw.line((x - 8, y1, x + 8, y1), fill=WISTERIA, width=3)
    draw.line((x - 8, y2, x + 8, y2), fill=WISTERIA, width=3)
    text(draw, (x - 12, (y1 + y2) // 2), label, 20, WISTERIA, True, "rm")


def rounded_label(draw, xy, value, size=20, fill=WISTERIA_LIGHT, color=WISTERIA):
    x, y = xy
    width = draw.textlength(value, font=font(size, True)) + 24
    draw.rounded_rectangle((x, y, x + width, y + 36), radius=18, fill=fill)
    text(draw, (x + 12, y + 18), value, size, color, True, "lm")


def main():
    canvas = Image.new("RGB", (2260, 1100), PAGE)
    draw = ImageDraw.Draw(canvas)

    # Header
    text(draw, (54, 38), "UI DESIGN SKETCH", 46, INK, True)
    text(draw, (56, 98), "AutoMediaPlayer · 智能座舱本地音乐播放器 · CP4 车机 UI/UX", 24, MUTED)
    rounded_label(draw, (1728, 38), "1408 × 792 px", 22)
    text(draw, (2176, 58), "横屏 / 160 dpi", 18, TERTIARY, False, "rm")

    # Rendered screen: crop status/navigation bars so only the app surface is measured.
    shot = Image.open(SOURCE).convert("RGB").crop((0, 76, 1408, 696))
    scale = 1.0
    shot = shot.resize(
        (round(shot.width * scale), round(shot.height * scale)),
        Image.Resampling.LANCZOS,
    )
    sx, sy = 54, 180
    canvas.paste(shot, (sx, sy))
    draw.rounded_rectangle(
        (sx - 3, sy - 3, sx + shot.width + 3, sy + shot.height + 3),
        radius=10,
        outline=BORDER,
        width=3,
    )

    def p(x, y):
        return round(sx + x * scale), round(sy + (y - 76) * scale)

    # 60 / 40 split guide
    split_x = p(845, 196)[0]
    draw.line(
        (split_x, p(845, 196)[1], split_x, p(845, 667)[1]),
        fill="#B597DF",
        width=3,
    )
    marker(draw, (split_x, 810), 4)
    text(draw, (split_x - 26, 810), "60% 播放区 / 40% 列表", 18, WISTERIA, True, "rm")

    # 20dp safe margin + 72dp top bar
    x0, y0 = p(0, 93)
    x1, y1 = p(29, 196)
    draw.rounded_rectangle((x0, y0, x1, y1), radius=5, outline=WISTERIA, width=3)
    seed_y = y0 - 12
    draw.line((x0, seed_y, x1, seed_y), fill=WISTERIA, width=3)
    draw.line((x0, seed_y - 6, x0, seed_y + 6), fill=WISTERIA, width=3)
    draw.line((x1, seed_y - 6, x1, seed_y + 6), fill=WISTERIA, width=3)
    text(draw, (x1 + 8, seed_y), "20dp", 17, WISTERIA, True, "lm")
    bracket_x = 1476
    draw.line((bracket_x, y0, bracket_x, y1), fill=WISTERIA, width=3)
    draw.line((bracket_x - 8, y0, bracket_x + 8, y0), fill=WISTERIA, width=3)
    draw.line((bracket_x - 8, y1, bracket_x + 8, y1), fill=WISTERIA, width=3)
    text(draw, (bracket_x, y0 - 12), "72dp", 17, WISTERIA, True, "mm")
    marker(draw, (p(1200, 150)), 3)

    # 96dp cover / 32dp seek bar hit area / 80dp & 88dp controls
    cover_x0, cover_y0 = p(80, 344)
    cover_x1, cover_y1 = p(176, 440)
    draw.rounded_rectangle((cover_x0, cover_y0, cover_x1, cover_y1), radius=12, outline=WISTERIA, width=3)
    rounded_label(draw, (cover_x0, cover_y1 + 8), "封面 96dp", 17)

    seek_y = p(0, 494)[1]
    draw.line((p(75, 494)[0], seek_y, p(800, 494)[0], seek_y), fill="#9873CD", width=3)
    marker(draw, (p(800, 494)), 10)

    play_box = (p(438, 472), p(564, 598))
    draw.ellipse(
        (play_box[0][0], play_box[0][1], play_box[1][0], play_box[1][1]),
        outline=WISTERIA,
        width=4,
    )
    marker(draw, (play_box[1][0], play_box[0][1]), 5)
    rounded_label(draw, (play_box[1][0] + 8, play_box[1][1] - 34), "播放键 88dp", 17)

    prev_box = (p(294, 477), p(409, 592))
    draw.ellipse(
        (prev_box[0][0], prev_box[0][1], prev_box[1][0], prev_box[1][1]),
        outline="#9873CD",
        width=2,
    )
    marker(draw, (prev_box[0][0], prev_box[0][1]), 7)
    rounded_label(draw, (350, prev_box[1][1] + 10), "80dp", 16)

    # 92dp list row + favorite touch target
    row_box = (p(891, 532), p(1350, 638))
    draw.rounded_rectangle(
        (row_box[0][0], row_box[0][1], row_box[1][0], row_box[1][1]),
        radius=12,
        outline=WISTERIA,
        width=3,
    )
    marker(draw, (row_box[1][0] + 2, row_box[0][1] + 22), 6)
    text(draw, (row_box[1][0] - 12, row_box[1][1] + 12), "列表行最小 92dp", 18, WISTERIA, True, "ra")

    fav_box = (p(1247, 391), p(1327, 471))
    draw.rounded_rectangle(
        (fav_box[0][0], fav_box[0][1], fav_box[1][0], fav_box[1][1]),
        radius=8,
        outline="#E2557B",
        width=2,
    )
    marker(draw, (fav_box[1][0], fav_box[0][1]), 8)

    # Right specification panel
    panel_x = 1510
    draw.rounded_rectangle(
        (panel_x, 178, 2200, 820),
        radius=24,
        fill=WHITE,
        outline=BORDER,
        width=2,
    )
    text(draw, (panel_x + 32, 204), "尺寸与规范依据", 32, INK, True)
    text(draw, (panel_x + 32, 250), "教材第五章车机 HMI 规范 / WCAG 2.1 AA", 18, TERTIARY)

    rows = [
        ("①", "屏幕基准", "1408×792 px · 横屏 · 160 dpi"),
        ("②", "安全边距", "20dp · 模块间距 ≥20dp"),
        ("③", "顶部品牌栏", "72dp 固定高度"),
        ("④", "主分栏", "播放区 60% · 列表区 40%"),
        ("⑤", "主控制按钮", "普通 80dp · 播放键 88dp"),
        ("⑥", "歌曲列表行", "最小行高 92dp"),
        ("⑦", "触摸目标", "本界面 ≥48dp（规范 ≥40dp）"),
        ("⑧", "字号层级", "32 / 24 / 22 / 18 / 16sp"),
        ("⑨", "文本对比度", "15.01 / 7.70 / 5.67 : 1"),
        ("⑩", "进度条", "8dp 轨道 · 32dp 触控热区"),
        ("11", "播放状态持久化", "1s 防抖 + 5s 保底 + 24h 有效"),
    ]
    row_y = 292
    for number, label, value in rows:
        text(draw, (panel_x + 34, row_y), number, 22, WISTERIA, True, "lm")
        text(draw, (panel_x + 76, row_y), label, 21, INK, True, "lm")
        text(draw, (panel_x + 236, row_y), value, 20, MUTED, False, "lm")
        draw.line(
            (panel_x + 32, row_y + 28, panel_x + 658, row_y + 28),
            fill="#F0E8F9",
            width=1,
        )
        row_y += 48

    # Footer: evidence and accessibility basis
    draw.rounded_rectangle((54, 846, 2200, 1040), radius=22, fill=WHITE, outline=BORDER, width=2)
    text(draw, (82, 872), "规范依据", 24, WISTERIA, True)
    text(
        draw,
        (82, 912),
        "① 第五章车机 UI：大触控、强对比、低认知负荷；② 最小字体 12sp，重要信息 48sp+；"
        "③ WCAG AA 正文对比度 ≥4.5:1；④ 状态保存遵循“最小状态集合”。",
        19,
        MUTED,
    )
    text(
        draw,
        (82, 950),
        "验收结论：触控 48–88dp、正文对比度 ≥5.67:1、字号 16–32sp；列表行与主按钮均满足车机远距离可读、盲操可点。",
        19,
        INK,
    )
    rounded_label(draw, (82, 984), "CP4-② UI_Design_Sketch", 18)
    rounded_label(draw, (510, 984), "紫藤色 #613F91 / 白色 #FFFFFF", 18, "#F3EAFD", WISTERIA)

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(OUTPUT, "PNG")
    print(f"written: {OUTPUT} ({canvas.width}x{canvas.height})")


if __name__ == "__main__":
    main()
