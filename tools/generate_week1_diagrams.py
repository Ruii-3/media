"""Generate the week-1 project structure and SWE.2 component diagrams."""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "docs"
SCALE = 2

FONT_YAHEI = r"C:\Windows\Fonts\msyh.ttc"
FONT_YAHEI_BOLD = r"C:\Windows\Fonts\msyhbd.ttc"
FONT_MONO = r"C:\Windows\Fonts\consola.ttf"


def font(path: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, size * SCALE)


def canvas(width: int, height: int, color: str = "#F5F8FD") -> tuple[Image.Image, ImageDraw.ImageDraw]:
    image = Image.new("RGB", (width * SCALE, height * SCALE), color)
    return image, ImageDraw.Draw(image)


def save(image: Image.Image, name: str) -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    image = image.resize((image.width // SCALE, image.height // SCALE), Image.LANCZOS)
    path = OUT_DIR / name
    image.save(path, "PNG")
    print(f"generated {path}")


def text(draw: ImageDraw.ImageDraw, xy: tuple[int, int], value: str, face: ImageFont.FreeTypeFont,
         fill: str, anchor: str | None = None) -> None:
    draw.text((xy[0] * SCALE, xy[1] * SCALE), value, font=face, fill=fill, anchor=anchor)


def arrow(draw: ImageDraw.ImageDraw, points: list[tuple[int, int]], color: str = "#3B6FD4",
          width: int = 3, head: int = 14) -> None:
    scaled = [(x * SCALE, y * SCALE) for x, y in points]
    draw.line(scaled, fill=color, width=width * SCALE, joint="curve")
    (x1, y1), (x2, y2) = scaled[-2], scaled[-1]
    angle = math.atan2(y2 - y1, x2 - x1)
    length = head * SCALE
    spread = math.radians(24)
    left = (x2 - length * math.cos(angle - spread), y2 - length * math.sin(angle - spread))
    right = (x2 - length * math.cos(angle + spread), y2 - length * math.sin(angle + spread))
    draw.polygon([(x2, y2), left, right], fill=color)


def project_structure() -> None:
    image, draw = canvas(1800, 1200)
    draw.rounded_rectangle(
        (40 * SCALE, 40 * SCALE, 1760 * SCALE, 1160 * SCALE),
        radius=28 * SCALE,
        fill="#FFFFFF",
        outline="#DDE5F2",
        width=2 * SCALE,
    )
    title = font(FONT_YAHEI_BOLD, 42)
    subtitle = font(FONT_YAHEI, 21)
    mono = font(FONT_MONO, 23)
    note = font(FONT_YAHEI, 19)
    small = font(FONT_YAHEI, 16)

    text(draw, (90, 78), "AutoMediaPlayer 项目结构（第 1 周 MVP）", title, "#10233F")
    text(draw, (92, 142), "包名 com.example.automediaplayer · Kotlin · minSdk 31 · 分层：ui / adapter / model / util", subtitle, "#66758A")
    draw.line((90 * SCALE, 196 * SCALE, 1710 * SCALE, 196 * SCALE), fill="#E3E9F2", width=2 * SCALE)

    tree = [
        ("AutoMediaPlayer/", "项目根目录"),
        ("├── MVP_Requirements.txt", "SWE.1 MVP 需求规格（交付物 2-1）"),
        ("├── build.gradle.kts", "工程级构建脚本"),
        ("├── settings.gradle.kts", "模块与仓库配置"),
        ("├── gradle/libs.versions.toml", "依赖版本目录"),
        ("├── docs/", "结构图、组件图与 CP1 验收记录"),
        ("├── tools/generate_week1_diagrams.py", "结构图生成脚本（可重复生成）"),
        ("├── testdata/", "CP1 演示音频（不打包进 APK）"),
        ("├── README.md", "项目说明与构建/演示步骤"),
        ("└── app/", "应用模块"),
        ("    ├── build.gradle.kts", "ViewBinding、RecyclerView、协程依赖"),
        ("    └── src/main/", "源码与资源根目录"),
        ("        ├── AndroidManifest.xml", "音频权限 + .ui.MainActivity(singleInstance)"),
        ("        ├── java/com/example/automediaplayer/", "Kotlin 源码包"),
        ("        │   ├── model/MusicData.kt", "音乐元数据模型 + 时长格式化"),
        ("        │   ├── util/MusicScanner.kt", "MediaStore 扫描（IO 协程，含 U 盘卷）"),
        ("        │   ├── adapter/MusicAdapter.kt", "RecyclerView 列表适配器"),
        ("        │   └── ui/MainActivity.kt", "权限 / 扫描 / 列表 / MediaPlayer 控制"),
        ("        └── res/", "界面资源"),
        ("            ├── layout/activity_main.xml", "左 70% 播放区 + 右 30% 歌曲列表"),
        ("            ├── layout/item_music.xml", "列表项：歌名 / 歌手 / 专辑 / 时长"),
        ("            ├── drawable/icon_controls_*.xml", "播放、暂停、上一首、下一首矢量图标"),
        ("            └── values/strings.xml", "文案、颜色、主题"),
    ]

    y = 238
    for line, annotation in tree:
        if line.rstrip().endswith("/"):
            color = "#1D4ED8"
        elif ".kt" in line:
            color = "#C26A08"
        elif ".xml" in line:
            color = "#0F8A5F"
        elif ".kts" in line or ".toml" in line:
            color = "#7C3AED"
        else:
            color = "#26364D"
        text(draw, (100, y), line, mono, color)
        text(draw, (1080, y + 1), annotation, note, "#66758A")
        y += 37

    legend_y = 1100
    for x, color, label in [
        (100, "#1D4ED8", "目录"),
        (210, "#C26A08", "Kotlin"),
        (350, "#0F8A5F", "XML 资源"),
        (520, "#7C3AED", "Gradle"),
    ]:
        draw.rounded_rectangle(
            (x * SCALE, (legend_y - 2) * SCALE, (x + 16) * SCALE, (legend_y + 14) * SCALE),
            radius=4 * SCALE,
            fill=color,
        )
        text(draw, (x + 25, legend_y), label, small, "#66758A")
    text(draw, (1250, 1096), "生成日期：2026-11-07 · 对应 CP1 交付物 project_structure.png", small, "#97A3B4")

    save(image, "project_structure.png")


def component_box(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int], title: str,
                  lines: list[str], accent: str, fill: str = "#FFFFFF") -> None:
    x1, y1, x2, y2 = box
    draw.rounded_rectangle(
        (x1 * SCALE, y1 * SCALE, x2 * SCALE, y2 * SCALE),
        radius=16 * SCALE,
        fill=fill,
        outline=accent,
        width=2 * SCALE,
    )
    draw.rounded_rectangle(
        (x1 * SCALE, y1 * SCALE, x2 * SCALE, (y1 + 46) * SCALE),
        radius=16 * SCALE,
        fill=accent,
    )
    text(draw, (x1 + 18, y1 + 11), title, font(FONT_YAHEI_BOLD, 20), "#FFFFFF")
    line_font = font(FONT_YAHEI, 16)
    y = y1 + 62
    for line in lines:
        text(draw, (x1 + 18, y), "• " + line, line_font, "#405068")
        y += 27


def component_diagram() -> None:
    image, draw = canvas(1800, 1120)
    draw.rounded_rectangle(
        (40 * SCALE, 40 * SCALE, 1760 * SCALE, 1080 * SCALE),
        radius=28 * SCALE,
        fill="#FFFFFF",
        outline="#DDE5F2",
        width=2 * SCALE,
    )
    text(draw, (90, 78), "第 1 周 MVP 组件图（SWE.2）", font(FONT_YAHEI_BOLD, 40), "#10233F")
    text(draw, (92, 138), "依赖方向：UI → Adapter / Util → Model；Util 通过 ContentResolver 访问系统媒体库，UI 通过 MediaPlayer 播放音频", font(FONT_YAHEI, 18), "#66758A")

    component_box(
        draw,
        (90, 300, 540, 720),
        "MainActivity（ui）",
        ["权限申请与扫描调度", "列表加载与点击播放", "MediaPlayer 状态控制", "播放按钮图标复位"],
        "#1D4ED8",
        "#F4F7FF",
    )
    component_box(
        draw,
        (650, 220, 1030, 420),
        "MusicAdapter（adapter）",
        ["ViewHolder 复用", "updateData()", "onItemClick 回调"],
        "#B45309",
        "#FFF9F2",
    )
    component_box(
        draw,
        (650, 500, 1070, 760),
        "MusicScanner（util）",
        ["Dispatchers.IO 查询", "IS_MUSIC 与 45 秒过滤", "标题升序 + use{} 关闭 Cursor"],
        "#0F8A5F",
        "#F3FBF7",
    )
    component_box(
        draw,
        (1140, 300, 1520, 520),
        "MusicData（model）",
        ["歌曲元数据（data class）", "content:// Uri", "getFormattedDuration()"],
        "#7C3AED",
        "#F8F5FF",
    )
    component_box(
        draw,
        (1160, 600, 1580, 800),
        "MediaPlayer（Android）",
        ["Idle → Initialized", "Prepared → Started/Paused", "release() 释放底层资源"],
        "#BE123C",
        "#FFF5F7",
    )
    component_box(
        draw,
        (1160, 880, 1580, 1030),
        "MediaStore / ContentResolver",
        ["系统媒体数据库", "音频索引与 USB 卷"],
        "#0369A1",
        "#F2FAFF",
    )

    arrow(draw, [(540, 390), (650, 330)], "#3B6FD4")
    arrow(draw, [(540, 600), (650, 600)], "#3B6FD4")
    arrow(draw, [(1030, 300), (1140, 360)], "#B45309")
    arrow(draw, [(1070, 550), (1140, 440)], "#0F8A5F")
    arrow(draw, [(1070, 690), (1160, 900)], "#0F8A5F")
    # Route MainActivity → MediaPlayer through the free channel under the scanner box.
    arrow(draw, [(540, 760), (640, 840), (1080, 840), (1080, 700), (1160, 700)], "#BE123C")

    label = font(FONT_YAHEI, 15)
    text(draw, (552, 356), "updateData()", label, "#3B6FD4")
    text(draw, (566, 566), "扫描", label, "#3B6FD4")
    text(draw, (1035, 278), "读取元数据", label, "#B45309")
    text(draw, (1078, 470), "构造", label, "#0F8A5F")
    text(draw, (1088, 800), "ContentResolver.query()", label, "#0F8A5F")
    text(draw, (640, 860), "MediaPlayer 状态机", label, "#BE123C")
    text(draw, (640, 884), "setDataSource / prepareAsync / start / pause / release", font(FONT_YAHEI, 13), "#BE123C")

    # UML-style interface notation in the lower-left corner.
    draw.ellipse((100 * SCALE, 905 * SCALE, 124 * SCALE, 929 * SCALE), outline="#66758A", width=2 * SCALE)
    draw.line((124 * SCALE, 917 * SCALE, 175 * SCALE, 917 * SCALE), fill="#66758A", width=2 * SCALE)
    text(draw, (188, 902), "提供接口（棒棒糖）", font(FONT_YAHEI, 16), "#66758A")
    draw.arc((100 * SCALE, 960 * SCALE, 124 * SCALE, 984 * SCALE), 90, 270, fill="#66758A", width=2 * SCALE)
    draw.line((112 * SCALE, 960 * SCALE, 175 * SCALE, 960 * SCALE), fill="#66758A", width=2 * SCALE)
    draw.line((112 * SCALE, 984 * SCALE, 175 * SCALE, 984 * SCALE), fill="#66758A", width=2 * SCALE)
    text(draw, (188, 956), "需求接口（插座）", font(FONT_YAHEI, 16), "#66758A")
    text(draw, (188, 990), "实线箭头表示调用/依赖方向", font(FONT_YAHEI, 15), "#97A3B4")

    save(image, "architecture_component.png")


if __name__ == "__main__":
    project_structure()
    component_diagram()
