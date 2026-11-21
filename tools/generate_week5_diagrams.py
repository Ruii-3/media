# -*- coding: utf-8 -*-
"""生成第 5 周组件图与音频焦点/多屏时序图（Pillow，不依赖 Graphviz）。"""
import math
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "docs")
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
TEXT2 = "#5B4C73"
GREEN = "#2C8B67"
RED = "#C2415F"


def font(size, bold=False):
    return ImageFont.truetype(FONT_B if bold else FONT, size)


def text(draw, xy, value, size=20, fill=TEXT, bold=False, anchor="la", align="left", spacing=7):
    draw.multiline_text(
        xy, value, font=font(size, bold), fill=fill, anchor=anchor, align=align, spacing=spacing
    )


def dashed_line(draw, p1, p2, fill, width=3, dash=12, gap=8):
    dist = math.hypot(p2[0] - p1[0], p2[1] - p1[1])
    if dist <= 0:
        return
    step = dash + gap
    for i in range(int(dist // step) + 1):
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


def arrow_head(draw, p1, p2, color, size=16):
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


def arrow(draw, p1, p2, color=W600, width=3, dashed=False, label_text=None, label_offset=(0, -16)):
    if dashed:
        dashed_line(draw, p1, p2, color, width)
    else:
        draw.line([p1, p2], fill=color, width=width)
    arrow_head(draw, p1, p2, color)
    if label_text:
        cx = (p1[0] + p2[0]) / 2 + label_offset[0]
        cy = (p1[1] + p2[1]) / 2 + label_offset[1]
        bbox = draw.textbbox((cx, cy), label_text, font=font(15), anchor="mm")
        draw.rounded_rectangle(
            (bbox[0] - 6, bbox[1] - 3, bbox[2] + 6, bbox[3] + 3), radius=6, fill=BG
        )
        draw.text((cx, cy), label_text, font=font(15), fill=color, anchor="mm")


def box(draw, x, y, w, h, title, lines, fill=WHITE, border=W300, title_color=W800):
    draw.rounded_rectangle((x, y, x + w, y + h), radius=18, fill=fill, outline=border, width=2)
    draw.rounded_rectangle((x, y, x + w, y + 44), radius=18, fill=W100, outline=border, width=2)
    draw.rectangle((x, y + 24, x + w, y + 44), fill=W100)
    text(draw, (x + 18, y + 10), title, 20, title_color, True)
    text(draw, (x + 18, y + 58), "\n".join(lines), 16, TEXT2, False, spacing=7)


def component_diagram():
    img = Image.new("RGB", (2200, 1320), BG)
    d = ImageDraw.Draw(img)
    text(d, (60, 34), "第 5 周组件图：副驾屏、音频焦点与 USB 热插拔", 30, TEXT, True)
    text(d, (60, 78), "主屏和副驾屏共享同一个 MusicPlayService / MediaSession；AudioFocusManager 管焦点，USBStorageObserver 管插拔广播。", 17, TEXT2)

    box(d, 60, 150, 460, 180, "MainActivity（主屏）", [
        "MediaBrowser：订阅媒体树", "MediaController：play/pause/skip/seek",
        "展示焦点、模式、进度、收藏", "座舱场景演示：模拟焦点/USB"
    ])
    box(d, 60, 380, 460, 180, "PassengerActivity（副驾屏）", [
        "独立 Activity，不持有 MediaPlayer", "连接同一 sessionToken",
        "列表/进度/模式随主屏同步", "setLaunchDisplayId 指定副屏"
    ])
    box(d, 60, 610, 460, 140, "通知栏 / 媒体键", [
        "MediaStyle + PendingIntent.getService", "adb keyevent 85/87/88",
        "与 UI 共用 MediaSession"
    ])
    box(d, 60, 800, 460, 150, "系统音频场景", [
        "导航播报：CAN_DUCK", "来电：LOSS_TRANSIENT",
        "其他播放器：LOSS（永久）"
    ], fill=W50)

    box(d, 690, 220, 610, 440, "MusicPlayService（播放内核）", [
        "MediaBrowserService：onGetRoot / onLoadChildren",
        "MediaSession：Callback + PlaybackState + Metadata",
        "MediaPlayer：prepareAsync / start / seekTo",
        "play() / pause() / stopPlayback()",
        "wasPlayingBeforeFocusLoss 记录续播条件",
        "handleUsbConnected / handleUsbDisconnected",
        "模拟命令：SIMULATE_AUDIO_FOCUS / SIMULATE_USB",
        "状态持久化：1s 防抖 + 5s 保底"
    ], border=W500)
    box(d, 690, 720, 610, 150, "USBStorageObserver", [
        "MOUNTED / UNMOUNTED / REMOVED / BAD_REMOVAL", "IntentFilter.addDataScheme(\"file\")",
        "归一成 onUSBConnected / onUSBDisconnected"
    ])
    box(d, 690, 930, 610, 150, "MediaStore / 系统存储", [
        "ContentResolver 查询外部卷", "USB 挂载后重新扫描",
        "拔出后旧 URI 失效"
    ], fill=W50)

    box(d, 1480, 150, 620, 210, "MediaSession", [
        "会话 token 分发给所有客户端", "PlaybackState.extras: 模式 + 焦点状态",
        "SessionEvent: 歌单/模式/焦点变化"
    ], border=W500)
    box(d, 1480, 410, 620, 170, "AudioFocusManager", [
        "AudioFocusRequest（AUDIOFOCUS_GAIN）", "setWillPauseWhenDucked(false)",
        "四种回调 → 业务语义"
    ], border=GREEN)
    box(d, 1480, 630, 620, 140, "MediaPlayer", [
        "单一解码实例", "setVolume(0.3f) / setVolume(1.0f)"
    ])
    box(d, 1480, 820, 620, 140, "PlaylistManager", [
        "队列、索引、四种切歌模式", "随机模式维护已播历史"
    ])
    box(d, 1480, 1010, 620, 140, "StateManager", [
        "SharedPreferences：歌曲/进度/模式/播放态", "24 小时有效期"
    ])

    arrow(d, (520, 225), (690, 300), W600, label_text="browse")
    arrow(d, (690, 340), (520, 245), W500, label_text="state / event")
    arrow(d, (520, 450), (690, 450), W600, label_text="MediaBrowser/Controller")
    arrow(d, (690, 500), (520, 475), W500, label_text="同步回调")
    arrow(d, (520, 675), (690, 620), W600, label_text="通知/媒体键")
    arrow(d, (520, 865), (690, 585), GREEN, label_text="焦点四态")
    arrow(d, (1300, 300), (1480, 255), W600, label_text="sessionToken/状态")
    arrow(d, (1480, 320), (1300, 340), W500, label_text="Callback/Transport")
    arrow(d, (1300, 390), (1480, 470), GREEN, label_text="request/abandon")
    arrow(d, (1480, 520), (1300, 430), GREEN, label_text="GAIN/DUCK/LOSS")
    arrow(d, (1300, 480), (1480, 690), W500, label_text="start/pause/volume")
    arrow(d, (1300, 570), (1480, 880), W500, label_text="队列/模式")
    arrow(d, (1300, 630), (1480, 1070), W500, label_text="保存/恢复")
    arrow(d, (690, 790), (690, 650), W600, label_text="USB 事件")
    arrow(d, (690, 970), (690, 870), W600, label_text="扫描/广播")

    d.rounded_rectangle((60, 1110, 2140, 1290), radius=16, fill=W50, outline=W200, width=2)
    text(d, (82, 1130), "关键约束", 19, W800, True)
    text(d, (82, 1164), "① 副驾屏只通过 MediaController 控制，不 new MediaPlayer，避免双播放器。\n"
                       "② 普通 pause() 不释放焦点；只有 stopPlayback()/onDestroy() 才 abandon，否则收不到 GAIN。\n"
                       "③ USB 拔出必须先停 MediaPlayer，再清队列，最后通知客户端，避免访问已移除文件。",
         16, TEXT2, spacing=9)

    img.save(os.path.join(OUT_DIR, "week5_component.png"), quality=95)


def focus_sequence():
    img = Image.new("RGB", (2200, 1460), BG)
    d = ImageDraw.Draw(img)
    text(d, (60, 32), "第 5 周播放时序图：音频焦点四态与主/副驾屏同步", 30, TEXT, True)
    text(d, (60, 76), "模拟器可用“座舱场景演示”触发同样的业务分支；真实设备由 AudioManager 回调驱动。", 17, TEXT2)

    lanes = [
        ("MainActivity /\nPassengerActivity", 230),
        ("MusicPlayService", 690),
        ("AudioFocusManager", 1130),
        ("AudioManager\n（系统）", 1560),
        ("MediaPlayer", 1940),
    ]
    top, bottom = 170, 1390
    for name, x in lanes:
        d.rounded_rectangle((x - 170, top, x + 170, top + 84), radius=14, fill=W100, outline=W300, width=2)
        text(d, (x, top + 20), name, 17, W800, True, anchor="ma", align="center")
        dashed_line(d, (x, top + 88), (x, bottom), W300, 3, 8, 10)

    steps = [
        (0, 1, "playFromMediaId / play()"),
        (1, 2, "requestAudioFocus()"),
        (2, 3, "AudioFocusRequest(GAIN)"),
        (3, 2, "GRANTED"),
        (1, 4, "prepareAsync → start()"),
        (3, 2, "LOSS_TRANSIENT_CAN_DUCK"),
        (2, 1, "onAudioFocusLostTransientCanDuck()"),
        (1, 4, "setVolume(0.3f, 0.3f)"),
        (3, 2, "GAIN（导航结束）"),
        (2, 1, "onAudioFocusGained()"),
        (1, 4, "setVolume(1.0f, 1.0f)"),
        (2, 1, "若 wasPlaying=true → play()"),
        (3, 2, "LOSS_TRANSIENT（来电）"),
        (2, 1, "记录 wasPlaying，pause()"),
        (1, 0, "PlaybackState → 两个屏都显示“已暂停”"),
        (3, 2, "LOSS（其他播放器接管）"),
        (2, 1, "stopPlayback() + abandon"),
        (1, 0, "STATE_STOPPED / 焦点已丢失"),
    ]
    y = 310
    for i, (a, b, label) in enumerate(steps):
        x1, x2 = lanes[a][1], lanes[b][1]
        color = GREEN if "GAIN" in label or "GRANTED" in label else (RED if "LOSS" in label and "TRANSIENT" not in label else W600)
        if a == b:
            continue
        arrow(d, (x1, y), (x2, y), color, 3, label_text=label, label_offset=(0, -17))
        d.text((60, y - 10), f"{i + 1:02d}", font=font(15, True), fill=W700)
        y += 58

    d.rounded_rectangle((60, 1390, 2140, 1440), radius=12, fill=W50, outline=W200, width=2)
    text(d, (80, 1402), "说明：CAN_DUCK 不停播只降音；LOSS_TRANSIENT 暂停并记住播放态；LOSS 永久丢失不自动恢复。所有状态通过 PlaybackState.extras 同步到主屏与副驾屏。", 15, TEXT2)

    img.save(os.path.join(OUT_DIR, "week5_focus_sequence.png"), quality=95)


if __name__ == "__main__":
    os.makedirs(OUT_DIR, exist_ok=True)
    component_diagram()
    focus_sequence()
    print("generated:", os.path.join(OUT_DIR, "week5_component.png"))
    print("generated:", os.path.join(OUT_DIR, "week5_focus_sequence.png"))
