# -*- coding: utf-8 -*-
"""生成第 3 周 MediaSession / MediaBrowserService 组件图与播放时序图。"""
import math
import os
from PIL import Image, ImageDraw, ImageFont

OUT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "docs")
FONT = "C:/Windows/Fonts/msyh.ttc"
FONT_B = "C:/Windows/Fonts/msyhbd.ttc"

BG = "#FAF7FE"
WHITE = "#FFFFFF"
W50 = "#F5F0FC"
W100 = "#E4D7F5"
W200 = "#D0BCEB"
W300 = "#B597DF"
W500 = "#9873CD"
W600 = "#7C56B4"
W700 = "#613F91"
W800 = "#472C6B"
TEXT = "#2C2140"
TEXT2 = "#6E6184"
GREEN = "#2C8B67"


def font(size, bold=False):
    return ImageFont.truetype(FONT_B if bold else FONT, size)


def text(draw, xy, value, size=20, fill=TEXT, bold=False, anchor="la", align="left", spacing=6):
    draw.multiline_text(
        xy, value, font=font(size, bold), fill=fill, anchor=anchor, align=align, spacing=spacing
    )


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


def arrow_head(draw, p1, p2, color, size=14):
    ang = math.atan2(p2[1] - p1[1], p2[0] - p1[0])
    half = size / 2
    a1 = (
        p2[0] - size * math.cos(ang) + half * math.sin(ang),
        p2[1] - size * math.sin(ang) - half * math.cos(ang),
    )
    a2 = (
        p2[0] - size * math.cos(ang) - half * math.sin(ang),
        p2[1] - size * math.sin(ang) + half * math.cos(ang),
    )
    draw.polygon([p2, a1, a2], fill=color)


def label(draw, xy, value, color, size=16, bg=BG):
    bbox = draw.textbbox(xy, value, font=font(size), anchor="mm")
    draw.rounded_rectangle(
        (bbox[0] - 6, bbox[1] - 3, bbox[2] + 6, bbox[3] + 3), radius=6, fill=bg
    )
    draw.text(xy, value, font=font(size), fill=color, anchor="mm")


def arrow(draw, p1, p2, color=W600, width=3, dashed=False, text_label=None, label_at=0.5, bg=BG):
    if dashed:
        dashed_line(draw, p1, p2, color, width)
    else:
        draw.line([p1, p2], fill=color, width=width)
    arrow_head(draw, p1, p2, color)
    if text_label:
        lx = p1[0] + (p2[0] - p1[0]) * label_at
        ly = p1[1] + (p2[1] - p1[1]) * label_at
        label(draw, (lx, ly), text_label, color, bg=bg)


def poly_arrow(draw, points, color=W600, width=3, dashed=False, text_label=None, label_at=0.5, bg=BG):
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
                label(
                    draw,
                    (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t),
                    text_label,
                    color,
                    bg=bg,
                )
                break
            walked += seg


def box(draw, rect, title, subtitle="", fill=WHITE, outline=W300, title_color=W800, title_size=22):
    x1, y1, x2, y2 = rect
    draw.rounded_rectangle(rect, radius=18, fill=fill, outline=outline, width=2)
    cx = (x1 + x2) // 2
    text(draw, (cx, y1 + 20), title, title_size, title_color, True, "ma", "center")
    if subtitle:
        text(draw, (cx, y1 + 56), subtitle, 15, TEXT2, False, "ma", "center", 5)


def band(draw, rect, title, fill=W50):
    draw.rounded_rectangle(rect, radius=24, fill=fill)
    text(draw, (rect[0] + 22, rect[1] + 14), title, 19, W700, True)


def component_diagram():
    img = Image.new("RGB", (2000, 1190), BG)
    d = ImageDraw.Draw(img)
    text(d, (60, 34), "AutoMediaPlayer 第 3 周组件图", 38, W800, True)
    text(d, (60, 86), "MediaSession 三角色：服务提供者 / 会话状态中心 / 控制消费者", 20, TEXT2)

    band(d, (50, 130, 1950, 470), "客户端与表现层")
    band(d, (50, 490, 1950, 925), "播放服务层：MusicPlayService（MediaBrowserService）")
    band(d, (50, 945, 1950, 1160), "系统媒体框架与数据源")

    ma = (80, 190, 560, 430)
    browser = (650, 185, 1000, 300)
    controller = (650, 335, 1000, 450)
    adapter = (1110, 190, 1450, 300)
    consumers = (1530, 190, 1920, 300)
    box(d, ma, "MainActivity", "权限入口 / 列表与播放卡片\n进度拖动 / 模式与收藏筛选")
    box(d, browser, "MediaBrowser", "connect() / subscribe()\n浏览媒体树的客户端")
    box(d, controller, "MediaController", "transportControls / sendCommand\n注册 Callback 接收状态")
    box(d, adapter, "MusicAdapter + 设置弹窗", "把 MediaItem 映射为 MusicData\n收藏 / 模式 / 进度 UI")
    box(d, consumers, "同一会话的其他消费者", "通知栏 / 系统媒体中心 / 副驾屏\n无需直接绑定播放器")

    service = (80, 540, 1520, 900)
    d.rounded_rectangle(service, radius=22, fill=WHITE, outline=W500, width=3)
    text(d, (108, 558), "MusicPlayService", 26, W800, True)
    text(d, (340, 566), "onGetRoot / onLoadChildren · MediaSession Callback · 前台通知", 15, TEXT2)
    playlist = (120, 630, 440, 770)
    player = (480, 630, 800, 770)
    session = (840, 630, 1180, 770)
    notification = (1220, 630, 1480, 770)
    box(d, playlist, "PlaylistManager", "队列 / 索引 / 四种模式\ngetAllMusic() 提供媒体树")
    box(d, player, "MediaPlayer", "prepareAsync / start / pause\nseekTo / onCompletion")
    box(d, session, "MediaSession", "PlaybackState\nMediaMetadata\nCallback / SessionToken", W50, W600)
    box(d, notification, "MediaStyle 通知", "上一首 / 播放暂停 / 下一首\nPendingIntent → Service")
    text(d, (120, 820), "服务生命周期：onCreate 建会话与播放器；startForeground(mediaPlayback)；Activity 解绑后继续播放。", 16, TEXT2)
    text(d, (120, 850), "浏览通道与命令通道共用同一服务实例；所有状态由 MediaSession 统一对外广播。", 16, TEXT2)

    scanner = (1600, 560, 1920, 680)
    favorites = (1600, 730, 1920, 850)
    box(d, scanner, "MusicScanner", "MediaStore 查询\nVOLUME_EXTERNAL 含 U 盘")
    box(d, favorites, "FavoriteManager", "MainActivity 读写 SharedPreferences\n只保存 MediaStore id")

    store = (80, 1010, 470, 1125)
    system = (560, 1010, 1000, 1125)
    adb = (1090, 1010, 1470, 1125)
    secondary = (1560, 1010, 1920, 1125)
    box(d, store, "MediaStore / U 盘", "由 MusicScanner 查询\ncontent://media/external/audio/media")
    box(d, system, "系统媒体服务", "MediaSessionManager / 蓝牙\n通知栏媒体卡片")
    box(d, adb, "adb / 方向盘 / 蓝牙按键", "KEYCODE_MEDIA_*\n媒体键事件")
    box(d, secondary, "副驾屏 / 其他客户端", "通过同一 sessionToken\n订阅状态并发控制")

    # 客户端内部关系
    arrow(d, (560, 235), (650, 235), W600, 3, False, "connect", 0.48)
    arrow(d, (560, 385), (650, 385), W600, 3, False, "transport / command", 0.50)
    arrow(d, (825, 300), (825, 335), W500, 3, False, "sessionToken", 0.50)
    arrow(d, (560, 300), (1110, 245), W500, 2, True, "展示 / 收藏", 0.35)

    # 浏览与控制双通道
    poly_arrow(
        d,
        [(700, 300), (700, 485), (300, 485), (300, 540)],
        W600,
        3,
        False,
        "onGetRoot / subscribe / onLoadChildren",
        0.55,
    )
    poly_arrow(
        d,
        [(1000, 400), (1050, 400), (1050, 520), (1010, 520), (1010, 630)],
        W600,
        3,
        False,
        "transportControls → MediaSession.Callback",
        0.60,
    )
    poly_arrow(
        d,
        [(940, 630), (940, 500), (990, 500), (990, 450)],
        W500,
        2,
        True,
        "PlaybackState / MediaMetadata",
        0.60,
    )

    # 服务内部关系
    arrow(d, (440, 700), (480, 700), W600, 3, False, "play(index)", 0.50)
    arrow(d, (800, 700), (840, 700), W600, 3, False, "元数据 / 状态", 0.50)
    arrow(d, (1180, 700), (1220, 700), W600, 3, False, "sync", 0.50)
    arrow(d, (1600, 620), (1520, 620), W500, 2, False, "scan result", 0.50)
    # 系统媒体键和状态广播
    arrow(d, (1090, 1080), (1000, 1080), W600, 3)
    label(d, (1045, 1050), "adb 媒体键", W600, 13)
    poly_arrow(
        d,
        [(780, 1010), (780, 950), (1060, 950), (1060, 770)],
        W600,
        3,
        False,
    )
    poly_arrow(
        d,
        [(1160, 770), (1160, 930), (800, 930), (800, 1010)],
        W500,
        2,
        True,
    )
    label(d, (980, 905), "媒体键命令 ↓ / 状态回调 ↑", W600, 14)
    poly_arrow(
        d,
        [(1560, 1010), (1560, 985), (1000, 985), (1000, 1010)],
        W500,
        2,
        True,
        "同一会话控制 / 状态订阅",
        0.52,
    )

    # 图例
    d.rounded_rectangle((1370, 1070, 1920, 1140), radius=14, fill=WHITE, outline=W100)
    draw_line = (1395, 1092)
    arrow(d, draw_line, (1460, 1092), W600, 3)
    text(d, (1470, 1081), "实线：调用 / 命令", 14, TEXT2)
    dashed_line(d, (1395, 1118), (1460, 1118), W500, 3)
    arrow_head(d, (1395, 1118), (1460, 1118), W500)
    text(d, (1470, 1107), "虚线：状态 / 数据回调", 14, TEXT2)

    path = os.path.join(OUT_DIR, "week3_component.png")
    img.save(path, quality=95)
    print("saved", path)


def sequence_diagram():
    width, height = 2200, 1510
    img = Image.new("RGB", (width, height), BG)
    d = ImageDraw.Draw(img)
    text(d, (56, 32), "AutoMediaPlayer 第 3 周播放与控制时序图", 38, W800, True)
    text(d, (56, 84), "MediaBrowser 订阅歌单 → MediaController 播放 → MediaSession 广播 → 通知栏 / adb 媒体键", 19, TEXT2)

    actors = [
        ("用户 / adb", 100),
        ("MainActivity", 330),
        ("MediaBrowser /\nMediaController", 650),
        ("MusicPlayService", 980),
        ("MediaSession", 1280),
        ("PlaylistManager", 1540),
        ("MediaPlayer", 1800),
        ("系统媒体 UI /\n通知栏", 2070),
    ]
    top, bottom = 145, 1400
    for name, x in actors:
        w = 150 if name == "用户 / adb" else (250 if "\n" in name else 205)
        d.rounded_rectangle(
            (x - w // 2, top, x + w // 2, top + 88), radius=16, fill=WHITE, outline=W300, width=2
        )
        text(d, (x, top + 28), name, 17, W800, True, "ma", "center", 4)
        dashed_line(d, (x, top + 88), (x, bottom), W200, 2, dash=8, gap=8)

    def phase(y, title):
        d.rounded_rectangle((62, y - 16, 430, y + 16), radius=14, fill=W100)
        text(d, (78, y - 10), title, 16, W700, True)

    messages = []
    phase_y = 280
    messages += [
        ("phase", phase_y, "阶段 A：MediaBrowser 连接并订阅歌单"),
        (330, 650, "new MediaBrowser + connect()", W600, False),
        (650, 980, "onGetRoot(callingPackage, uid)", W600, False),
        (980, 650, "BrowserRoot(root_id)", W500, True),
        (650, 980, "subscribe(root_id) → onLoadChildren", W600, False),
        (980, 650, "sendResult([])（扫描未完成）", W500, True),
        (980, 650, "notifyChildrenChanged() → 3 首 MediaItem", W500, True),
        (650, 330, "SubscriptionCallback.onChildrenLoaded", W500, True),
        (330, 650, "点击列表歌曲 → playFromMediaId(id)", W600, False),
        (650, 1280, "TransportControls.playFromMediaId", W600, False),
        (1280, 980, "Callback.onPlayFromMediaId", W600, False),
    ]
    messages += [
        ("phase", 660, "阶段 B：播放、元数据与状态回传"),
        (980, 1540, "moveToPosition(index)", W600, False),
        (1540, 980, "当前 MusicData", W500, True),
        (980, 1800, "reset / setDataSource / prepareAsync", W600, False),
        (1800, 980, "onPrepared() → start()", W500, True),
        (980, 1280, "setMetadata(title/artist/album/duration)", W600, False),
        (980, 1280, "setPlaybackState(PLAYING, position, speed)", W600, False),
        (1280, 650, "onMetadataChanged / onPlaybackStateChanged", W500, True),
        (650, 330, "刷新标题、进度、按钮与当前曲高亮", W500, True),
        (980, 2070, "startForeground(MediaStyle + sessionToken)", W600, False),
        (1280, 2070, "系统媒体卡片展示同一会话", W500, True),
    ]
    messages += [
        ("phase", 1045, "阶段 C：adb 媒体键 / 通知栏控制"),
        (100, 2070, "input keyevent 85 / 87 / 88", W600, False),
        (2070, 1280, "媒体键 → MediaSession.Callback", W600, False),
        (1280, 980, "onPause / onPlay / onSkipToNext", W600, False),
        (980, 1800, "pause() / start() / 重新 prepare", W600, False),
        (980, 1540, "moveToNext(manual = true)", W600, False),
        (980, 1280, "回写新的 PlaybackState / MediaMetadata", W600, False),
        (1280, 650, "所有 Controller 回调同步刷新", W500, True),
        (1280, 2070, "通知栏按钮与媒体卡片同步", W500, True),
    ]

    y = 300
    for item in messages:
        if item[0] == "phase":
            phase(item[1], item[2])
            y = item[1] + 48
            continue
        src, dst, text_label, color, dashed = item
        arrow(d, (src, y), (dst, y), color, 3, dashed, text_label, 0.50)
        y += 39

    d.rounded_rectangle((60, 1425, 2140, 1482), radius=14, fill=WHITE, outline=W100)
    text(
        d,
        (80, 1438),
        "关键点：MediaBrowser 负责“内容从哪里来”，MediaController 负责“命令往哪里去”，"
        "MediaSession 负责“所有人看到同一份状态”。",
        17,
        TEXT2,
    )

    path = os.path.join(OUT_DIR, "week3_sequence.png")
    img.save(path, quality=95)
    print("saved", path)


if __name__ == "__main__":
    os.makedirs(OUT_DIR, exist_ok=True)
    component_diagram()
    sequence_diagram()
