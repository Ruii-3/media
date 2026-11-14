# -*- coding: utf-8 -*-
"""生成第 2 周组件图与播放时序图（PNG）。"""
import math
import os
from PIL import Image, ImageDraw, ImageFont

OUT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "docs")
FONT = "C:/Windows/Fonts/msyh.ttc"
FONT_B = "C:/Windows/Fonts/msyhbd.ttc"

BG = "#FAF7FE"
WHITE = "#FFFFFF"
W50 = "#F2EBFB"
W100 = "#E4D7F5"
W300 = "#B597DF"
W500 = "#9873CD"
W600 = "#7C56B4"
W700 = "#613F91"
W800 = "#472C6B"
TEXT = "#2C2140"
TEXT2 = "#6E6184"


def font(size, bold=False):
    return ImageFont.truetype(FONT_B if bold else FONT, size)


def text(draw, xy, s, size=20, fill=TEXT, bold=False, anchor="la", align="left", spacing=6):
    draw.multiline_text(xy, s, font=font(size, bold), fill=fill, anchor=anchor, align=align, spacing=spacing)


def dashed_line(draw, p1, p2, fill, width=3, dash=12, gap=8):
    dist = math.hypot(p2[0] - p1[0], p2[1] - p1[1])
    if dist <= 0:
        return
    step = dash + gap
    count = int(dist // step) + 1
    for i in range(count):
        start = i * step
        end = min(start + dash, dist)
        if end <= start:
            continue
        t0, t1 = start / dist, end / dist
        draw.line(
            [
                (p1[0] + (p2[0] - p1[0]) * t0, p1[1] + (p2[1] - p1[1]) * t0),
                (p1[0] + (p2[0] - p1[0]) * t1, p1[1] + (p2[1] - p1[1]) * t1),
            ],
            fill=fill,
            width=width,
        )


def arrow_head(draw, p1, p2, color):
    ang = math.atan2(p2[1] - p1[1], p2[0] - p1[0])
    length, half = 14, 7
    a1 = (p2[0] - length * math.cos(ang) + half * math.sin(ang),
          p2[1] - length * math.sin(ang) - half * math.cos(ang))
    a2 = (p2[0] - length * math.cos(ang) - half * math.sin(ang),
          p2[1] - length * math.sin(ang) + half * math.cos(ang))
    draw.polygon([p2, a1, a2], fill=color)


def label(draw, xy, s, color, size=17):
    bbox = draw.textbbox(xy, s, font=font(size), anchor="mm")
    draw.rounded_rectangle((bbox[0] - 6, bbox[1] - 3, bbox[2] + 6, bbox[3] + 3), radius=6, fill=BG)
    draw.text(xy, s, font=font(size), fill=color, anchor="mm")


def arrow(draw, p1, p2, color=W600, width=3, dashed=False, text_label=None, label_at=0.5):
    if dashed:
        dashed_line(draw, p1, p2, color, width)
    else:
        draw.line([p1, p2], fill=color, width=width)
    arrow_head(draw, p1, p2, color)
    if text_label:
        lx = p1[0] + (p2[0] - p1[0]) * label_at
        ly = p1[1] + (p2[1] - p1[1]) * label_at
        label(draw, (lx, ly), text_label, color)


def poly_arrow(draw, points, color=W600, width=3, dashed=False, text_label=None, label_at=0.5):
    for i in range(len(points) - 1):
        if dashed:
            dashed_line(draw, points[i], points[i + 1], color, width)
        else:
            draw.line([points[i], points[i + 1]], fill=color, width=width)
    arrow_head(draw, points[-2], points[-1], color)
    if text_label:
        lengths = [math.hypot(b[0] - a[0], b[1] - a[1]) for a, b in zip(points, points[1:])]
        target = sum(lengths) * label_at
        walked = 0.0
        for (a, b), seg in zip(zip(points, points[1:]), lengths):
            if walked + seg >= target:
                t = (target - walked) / seg if seg else 0
                label(draw, (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t), text_label, color)
                break
            walked += seg


def box(draw, rect, title, subtitle="", fill=WHITE, outline=W300, title_color=W800):
    x1, y1, x2, y2 = rect
    draw.rounded_rectangle(rect, radius=18, fill=fill, outline=outline, width=2)
    cx = (x1 + x2) // 2
    text(draw, (cx, y1 + 22), title, 23, title_color, True, "ma")
    if subtitle:
        text(draw, (cx, y1 + 58), subtitle, 16, TEXT2, False, "ma", "center")


def band(draw, rect, title, fill=W50):
    draw.rounded_rectangle(rect, radius=22, fill=fill)
    text(draw, (rect[0] + 22, rect[1] + 14), title, 20, W700, True)


def component_diagram():
    img = Image.new("RGB", (2000, 1150), BG)
    d = ImageDraw.Draw(img)
    text(d, (60, 36), "AutoMediaPlayer 第 2 周组件图", 38, W800, True)
    text(d, (60, 88), "后台播放与播放队列：MainActivity → MusicPlayService → PlaylistManager", 20, TEXT2)

    band(d, (50, 130, 1950, 330), "表现层（UI）")
    band(d, (50, 350, 1950, 720), "播放核心层（Service + 队列）")
    band(d, (50, 740, 1950, 1110), "系统与数据层")

    ma = (90, 190, 560, 305)
    helper = (610, 190, 930, 305)
    adapter = (980, 190, 1270, 305)
    dialog = (1320, 190, 1620, 305)
    box(d, ma, "MainActivity", "权限 / 扫描 / 收藏\n进度条 / 模式设置")
    box(d, helper, "ServiceConnectionHelper", "bindService + startService\n异步回调返回服务实例")
    box(d, adapter, "MusicAdapter", "列表展示 / 当前曲高亮\n收藏按钮回调")
    box(d, dialog, "DialogPlaySettings", "四种播放模式\n只看收藏")

    service = (90, 410, 640, 640)
    playlist = (1060, 410, 1480, 550)
    mode = (1060, 590, 1330, 690)
    listener = (700, 550, 1010, 650)
    box(d, service, "MusicPlayService", "MediaPlayer 生命周期\nMediaSession / 前台通知\n队列调度与状态分发")
    box(d, playlist, "PlaylistManager", "原始列表 / 当前队列 / 索引\n顺序、循环、单曲、随机切歌")
    box(d, mode, "PlayMode", "ORDER / LOOP_ALL\nLOOP_ONE / SHUFFLE")
    box(d, listener, "PlaybackStateListener", "播放态 / 进度 / 歌曲 / 模式")

    player = (90, 800, 380, 910)
    session = (430, 800, 760, 910)
    notif = (810, 800, 1140, 910)
    store = (1190, 800, 1490, 910)
    prefs = (1540, 800, 1860, 910)
    scanner = (430, 960, 760, 1070)
    favorite = (810, 960, 1140, 1070)
    box(d, player, "MediaPlayer", "解码 / 播放 / seek")
    box(d, session, "MediaSession", "元数据 / 播放状态\n媒体键与锁屏控制")
    box(d, notif, "Notification", "MediaStyle 常驻通知\n上一首 / 暂停 / 下一首")
    box(d, store, "MediaStore", "本地 / U 盘音频")
    box(d, prefs, "SharedPreferences", "收藏歌曲 id 集合")
    box(d, scanner, "MusicScanner", "MediaStore 查询\nIO 协程")
    box(d, favorite, "FavoriteManager", "收藏 id 读写")

    # 绑定与播放控制
    arrow(d, (325, 305), (325, 410), W600, 3, False)
    label(d, (500, 396), "initPlaylist / play / pause / seek", W600)
    arrow(d, (560, 247), (610, 247), W500, 3, False, "bindService", 0.5)
    arrow(d, (770, 305), (770, 410), W500, 3, False)
    label(d, (746, 376), "Binder.getService", W500)

    # 服务与队列
    arrow(d, (640, 470), (1060, 470), W600, 3, False, "moveToNext / moveToPrevious / setPlayMode", 0.55)
    arrow(d, (1230, 550), (1230, 590), W500, 3, False, "切歌算法", 0.5)
    arrow(d, (640, 605), (700, 605), W500, 3, False, "回调", 0.5)
    poly_arrow(
        d, [(825, 550), (825, 340), (325, 340), (325, 305)], W500, 3, True,
        "onStateChanged / onMusicChanged / onProgress", 0.85
    )

    # 系统能力
    arrow(d, (250, 640), (235, 800), W600, 3, False, "播放控制", 0.5)
    arrow(d, (430, 640), (595, 800), W600, 3, False, "元数据 / 状态", 0.5)
    arrow(d, (760, 855), (810, 855), W600, 3, False, "MediaStyle", 0.5)
    arrow(d, (760, 1015), (1190, 855), W500, 2, False, "query", 0.5)
    arrow(d, (1140, 1015), (1540, 855), W500, 2, False, "id 集合", 0.5)

    # 沿边线绕行，避免穿过中间组件
    poly_arrow(
        d, [(90, 280), (75, 280), (75, 1015), (430, 1015)], W500, 2, False
    )
    label(d, (250, 1015), "scanMusicFiles", W500)
    poly_arrow(
        d, [(560, 320), (1900, 320), (1900, 1015), (1140, 1015)], W500, 2, False,
        "toggleFavorite", 0.35
    )

    d.rounded_rectangle((1380, 1020, 1930, 1095), radius=14, fill=WHITE, outline=W100)
    arrow(d, (1405, 1045), (1470, 1045), W600, 3)
    text(d, (1480, 1033), "实线：方法调用 / 控制流", 16, TEXT2)
    dashed_line(d, (1405, 1072), (1470, 1072), W500, 3)
    arrow(d, (1405, 1072), (1470, 1072), W500, 3)
    text(d, (1480, 1060), "虚线：状态回调", 16, TEXT2)

    path = os.path.join(OUT_DIR, "week2_component.png")
    img.save(path, quality=95)
    print("saved", path)


def sequence_diagram():
    img = Image.new("RGB", (2000, 1360), BG)
    d = ImageDraw.Draw(img)
    text(d, (60, 36), "AutoMediaPlayer 第 2 周播放时序图", 38, W800, True)
    text(d, (60, 88), "点击播放 → 服务准备 → 前台通知 → Home 后台 → 通知栏控制 → 模式切歌", 20, TEXT2)

    actors = [
        ("用户", 90),
        ("MainActivity", 330),
        ("ServiceConnection\nHelper", 610),
        ("MusicPlayService", 900),
        ("PlaylistManager", 1190),
        ("MediaPlayer", 1450),
        ("MediaSession /\n通知栏", 1730),
    ]
    top, bottom = 160, 1270
    for name, x in actors:
        width = 120 if name == "用户" else (210 if "\n" in name else 190)
        d.rounded_rectangle((x - width // 2, top, x + width // 2, top + 86), radius=16, fill=WHITE, outline=W300, width=2)
        text(d, (x, top + 30), name, 18, W800, True, "ma", "center")
        dashed_line(d, (x, top + 86), (x, bottom), W300, 2, dash=8, gap=8)

    messages = [
        (90, 330, "点击歌曲", W600, False),
        (330, 610, "bindService + startService", W500, False),
        (610, 900, "onServiceConnected(Binder)", W500, False),
        (330, 900, "initPlaylist(list, musicId)", W600, False),
        (900, 1190, "setPlaylist / moveToPosition", W600, False),
        (1190, 900, "当前 MusicData", W500, True),
        (900, 1450, "reset / setDataSource / prepareAsync", W600, False),
        (1450, 900, "onPrepared()", W500, True),
        (900, 1450, "start()", W600, False),
        (900, 1730, "setMetadata / setPlaybackState", W600, False),
        (900, 1730, "startForeground(MediaStyle)", W600, False),
        (900, 330, "onMusicChanged / onStateChanged", W500, True),
        (90, 330, "按 Home 键", W600, False),
        (330, 610, "unbind（服务仍在前台运行）", W500, True),
        (1730, 900, "用户点击通知栏：暂停 / 下一首", W600, False),
        (900, 1450, "pause() / 重新 prepare", W600, False),
        (900, 1730, "更新标题与按钮", W600, False),
        (1450, 900, "onCompletion()", W500, True),
        (900, 1190, "moveToNext(manual = false)", W600, False),
        (1190, 900, "新索引；ORDER 末曲返回 -1", W500, True),
        (900, 1450, "playMusicAt(newIndex)", W600, False),
    ]

    y = 290
    step = 44
    for src, dst, text_label, color, dashed in messages:
        arrow(d, (src, y), (dst, y), color, 3, dashed, text_label, 0.5)
        y += step

    d.rounded_rectangle((885, 260, 915, 830), radius=8, fill=W100)
    d.rounded_rectangle((885, 1010, 915, 1230), radius=8, fill=W100)
    d.rounded_rectangle((315, 260, 345, 480), radius=8, fill=W100)

    d.rounded_rectangle((60, 1288, 1940, 1340), radius=14, fill=WHITE, outline=W100)
    text(d, (80, 1300), "关键点：Activity 只负责展示与转发；MediaPlayer、队列、通知与 MediaSession 全部由服务持有，Home 退出不影响播放。", 17, TEXT2)

    path = os.path.join(OUT_DIR, "week2_sequence.png")
    img.save(path, quality=95)
    print("saved", path)


if __name__ == "__main__":
    os.makedirs(OUT_DIR, exist_ok=True)
    component_diagram()
    sequence_diagram()
