# -*- coding: utf-8 -*-
"""生成 CP6 测试报告 Excel。

依据《Android 智能座舱本地音乐播放器开发》第 317 页之后的内容：
- 5.2.1 单元测试结果分析表
- 5.2.2 集成测试结果分析表
- 附录一 集成测试报告模板（INT-xxx / 关联需求 / 模块 / 目的 / 前置 / 步骤 / 预期 / 实际 / 状态 / 备注）
"""

from pathlib import Path

from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs" / "integration_test_report.xlsx"

PURPLE = "6F4E9C"
PURPLE_LIGHT = "EDE7F6"
GREEN_FILL = "E2EFDA"
GREEN_FONT = "375623"
RED_FILL = "FFC7CE"
RED_FONT = "9C0006"
GRAY_FILL = "EDEDED"
GRAY_FONT = "595959"
THIN = Side(style="thin", color="BFBFBF")
BORDER = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)


def title(ws, text, last_col):
    ws.merge_cells(start_row=1, start_column=1, end_row=1, end_column=last_col)
    cell = ws.cell(row=1, column=1, value=text)
    cell.font = Font(name="微软雅黑", size=16, bold=True, color="FFFFFF")
    cell.fill = PatternFill("solid", fgColor=PURPLE)
    cell.alignment = Alignment(horizontal="center", vertical="center")
    ws.row_dimensions[1].height = 30


def info(ws, row, pairs):
    col = 1
    for label, value, span in pairs:
        c = ws.cell(row=row, column=col, value=label)
        c.font = Font(name="微软雅黑", size=10, bold=True)
        c.fill = PatternFill("solid", fgColor=PURPLE_LIGHT)
        c.border = BORDER
        c.alignment = Alignment(vertical="center")
        ws.merge_cells(start_row=row, start_column=col + 1, end_row=row, end_column=col + span)
        v = ws.cell(row=row, column=col + 1, value=value)
        v.font = Font(name="微软雅黑", size=10)
        v.border = BORDER
        v.alignment = Alignment(vertical="center", wrap_text=True)
        col += span + 1
    ws.row_dimensions[row].height = 24


def header(ws, row, headers):
    for i, text in enumerate(headers, start=1):
        c = ws.cell(row=row, column=i, value=text)
        c.font = Font(name="微软雅黑", size=10, bold=True, color="FFFFFF")
        c.fill = PatternFill("solid", fgColor=PURPLE)
        c.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
        c.border = BORDER
    ws.row_dimensions[row].height = 34


def body(ws, row, values, heights=None):
    for i, text in enumerate(values, start=1):
        c = ws.cell(row=row, column=i, value=text)
        c.font = Font(name="微软雅黑", size=9)
        c.alignment = Alignment(vertical="top", wrap_text=True)
        c.border = BORDER
        if row % 2 == 0:
            c.fill = PatternFill("solid", fgColor="F7F4FB")
    if heights:
        ws.row_dimensions[row].height = heights


def status_style(ws, row, col):
    c = ws.cell(row=row, column=col)
    value = str(c.value or "").upper()
    if value == "PASS":
        c.fill = PatternFill("solid", fgColor=GREEN_FILL)
        c.font = Font(name="微软雅黑", size=9, bold=True, color=GREEN_FONT)
    elif value == "FAIL":
        c.fill = PatternFill("solid", fgColor=RED_FILL)
        c.font = Font(name="微软雅黑", size=9, bold=True, color=RED_FONT)
    else:
        c.fill = PatternFill("solid", fgColor=GRAY_FILL)
        c.font = Font(name="微软雅黑", size=9, bold=True, color=GRAY_FONT)
    c.alignment = Alignment(horizontal="center", vertical="center")


# ---------------- 集成测试用例 ----------------
# 字段：ID, 需求, 模块, 目的, 前置, 步骤, 预期, 实际, 状态, 备注
CASES = [
    ("INT-001", "REQ-019", "安装与启动", "验证 APK 可安装、可启动",
     "车机开启 USB 调试；已准备 debug APK",
     "1. adb install -r -g；2. 启动 MainActivity；3. 观察主界面",
     "安装成功，主界面正常渲染",
     "旧包签名冲突，卸载后安装成功；测试时为 5.0，最终 6.0 已构建",
     "PASS", "最终 6.0 待设备恢复后重新安装回归"),
    ("INT-002", "REQ-001", "媒体扫描与列表", "验证 MediaStore 扫描与列表展示",
     "READ_MEDIA_AUDIO 已授权；/sdcard/music 有 8 首 MP3",
     "1. 推送 3 首测试曲；2. 触发 MediaScanner；3. 重启应用；4. 对比列表",
     "列表显示 8 首，标题/歌手/时长正确",
     "MediaStore 返回 8 首；主界面显示“共 8 首”，track1-3 元数据正确",
     "PASS", "截图 docs/cp6_real_device_main.png"),
    ("INT-003", "REQ-020", "副屏探测", "验证系统副屏能力与 displayId",
     "车机连接 HDMI 副屏",
     "1. dumpsys display；2. pm list features；3. 记录 displayId",
     "存在第二显示，系统支持 activities_on_secondary_displays",
     "displayId=2 HDMI 1920×720；feature=true；另有 displayId=3 仪表虚拟屏",
     "PASS", "副屏选择不写死 displayId"),
    ("INT-004", "REQ-016", "厂商硬按键", "验证厂商服务发现与 AIDL 绑定",
     "瑞特台架已安装 com.raite.car.hardkey；应用已启动",
     "1. dumpsys activity services com.raite.car.hardkey；2. 查 Client AppBindRecord",
     "服务存在，且与本应用建立连接",
     "输出 Client AppBindRecord ... com.example.automediaplayer；recentCallingPackage 为本应用",
     "PASS", "实际按键见 INT-019，仍待补测"),
    ("INT-005", "REQ-002", "副驾屏列表同步", "双屏列表与歌曲信息一致",
     "副屏 displayId=2；主屏已扫描 8 首；应用已启动",
     "1. 主屏打开副驾屏控制器；2. 对比两屏列表；3. 主屏切歌",
     "两屏列表一致；主屏更新后副屏 3 秒内同步",
     "未执行：设备在测试中途掉线，adb devices 为空",
     "NA", "待补测，不计入通过率"),
    ("INT-006", "REQ-003", "副驾屏点歌", "副驾屏点歌触发主屏播放",
     "双屏连接；列表同步完成；主屏无播放任务",
     "1. 副驾屏点击列表第 3 首；2. 观察主屏播放状态",
     "副屏点击后主屏 1 秒内播放目标歌曲",
     "未执行：设备掉线",
     "NA", "待补测"),
    ("INT-007", "REQ-004", "双屏播放控制", "播放/暂停、切歌双向同步",
     "主屏播放《男人海洋》；双屏连接正常",
     "1. 副屏暂停/播放；2. 副屏下一首；3. 主屏上一首；4. 互看状态",
     "对方 1 秒内响应并同步歌曲信息",
     "未执行：设备掉线",
     "NA", "待补测"),
    ("INT-008", "REQ-005", "双屏进度拖拽", "进度条拖拽同步",
     "主屏播放≥3 分钟歌曲；进度约 1 分钟",
     "1. 副屏拖到 2 分钟；2. 主屏拖到 1 分 30 秒；3. 对比时间",
     "对方 2 秒内同步，误差≤1 秒",
     "未执行：设备掉线",
     "NA", "待补测"),
    ("INT-009", "REQ-006", "双屏播放模式", "顺序/循环/随机模式同步",
     "主屏顺序模式播放第 2 首；双屏模式图标正常",
     "1. 副屏切模式；2. 主屏切回顺序；3. 对比图标与切歌",
     "对方 1 秒内同步模式与切歌行为",
     "未执行：设备掉线",
     "NA", "待补测"),
    ("INT-010", "REQ-007", "专辑封面", "双屏封面同步显示",
     "主屏播放带封面歌曲；副屏播放页打开",
     "1. 对比双屏封面；2. 主屏切歌；3. 观察副屏更新",
     "封面一致，切歌后 2 秒内更新",
     "未执行：设备掉线",
     "NA", "待补测"),
    ("INT-011", "REQ-008", "播放信息与进度", "歌名/歌手/时长/进度同步",
     "主屏播放《雨一直下》；双屏播放页正常",
     "1. 对比歌名/歌手/时长；2. 等 10 秒看进度；3. 快进到 2 分钟",
     "初始信息一致；10 秒后进度均 +10 秒；快进 2 秒内同步",
     "未执行：设备掉线",
     "NA", "待补测"),
    ("INT-012", "REQ-009", "MediaBrowserService", "副屏通过服务获取数据与控制",
     "主屏启动服务；副屏无缓存；双屏连接",
     "1. 副屏重启加载列表；2. 无缓存点歌；3. 关闭服务观察提示",
     "3 秒内加载；点歌 1 秒内播放；服务关闭 2 秒内提示断开",
     "未执行：设备掉线",
     "NA", "待补测"),
    ("INT-013", "REQ-010", "副驾屏界面", "PassengerActivity 功能与前后台切换",
     "主屏播放；副屏启动 PassengerActivity",
     "1. 副屏浏览/控制；2. 切后台再切回",
     "操作 1-2 秒内响应；状态不丢",
     "未执行：设备掉线",
     "NA", "待补测"),
    ("INT-014", "REQ-011", "后台播放", "Home 退出后音乐持续",
     "主屏播放中；通知权限已授予",
     "1. 按 Home；2. 等待 30 秒；3. 返回应用；4. 下拉通知栏",
     "音乐持续；返回后进度/按钮同步；通知栏可控制",
     "未执行：设备掉线",
     "NA", "待补测"),
    ("INT-015", "REQ-012", "强杀恢复", "force-stop 后恢复歌曲/进度/模式",
     "播放 5 秒以上；状态已保存",
     "1. am force-stop；2. 重新启动；3. 对比歌曲/进度/模式",
     "恢复上次歌曲、进度、模式与播放态",
     "未执行：设备掉线",
     "NA", "待补测"),
    ("INT-016", "REQ-013", "USB 插入", "插入 U 盘自动重新扫描",
     "U 盘含 MP3；应用运行中",
     "1. 插入 U 盘；2. 等待挂载；3. 观察列表与 Toast",
     "Toast 提示，列表出现 U 盘歌曲，双屏同步刷新",
     "未执行：设备掉线且无 U 盘操作",
     "NA", "待补测"),
    ("INT-017", "REQ-014", "USB 拔出", "播放中拔出 U 盘停止并清空",
     "播放 U 盘中的歌曲",
     "1. 直接拔出 U 盘；2. 观察播放、列表、Toast、持久化",
     "停止播放，清空列表与当前歌曲，Toast 提示，持久化清理",
     "未执行：设备掉线且无 U 盘操作",
     "NA", "待补测"),
    ("INT-018", "REQ-015", "音频焦点四态", "GAIN/LOSS/TRANSIENT/CAN_DUCK 行为",
     "导航、蓝牙电话、另一播放器已准备",
     "1. 导航播报；2. 来电；3. 其他播放器抢占；4. 播报结束",
     "CAN_DUCK 降音 30%；TRANSIENT 暂停；LOSS 停止；GAIN 恢复",
     "未执行：设备掉线",
     "NA", "待补测"),
    ("INT-019", "REQ-016", "硬按键实际按键", "方向盘 MODE/SEEK 控制播放",
     "瑞特台架；服务已绑定；应用已播放",
     "1. 按 MODE；2. 按 SEEK_UP；3. 按 SEEK_DOWN；4. 查 HardKey 日志",
     "播放/暂停、下一首、上一首；日志出现 onKeyEvent key=509/512/511",
     "未执行：设备掉线",
     "NA", "待补测"),
    ("INT-020", "REQ-017", "状态持久化链路", "切歌→保存→新实例恢复",
     "JVM + Robolectric 环境",
     "1. 运行 PlaybackFlowIntegrationTest；2. 查看断言结果",
     "歌曲/位置/进度/模式一致",
     "3/3 用例通过",
     "PASS", "自动化测试，非真机"),
    ("INT-021", "REQ-017", "循环模式持久化", "LOOP_ALL 绕回并持久化",
     "JVM + Robolectric 环境",
     "1. 运行 loopModeSelectionSurvivesStateReload",
     "绕回第 0 首，模式仍为 LOOP_ALL",
     "用例通过",
     "PASS", "自动化测试，非真机"),
    ("INT-022", "REQ-018", "状态清理", "USB 拔出语义：清理后不可恢复",
     "JVM + Robolectric 环境",
     "1. 保存有效状态；2. clear；3. 再读取",
     "loadValidPlaybackState() 返回 null",
     "用例通过",
     "PASS", "自动化测试，非真机"),
]

UNIT_ROWS = [
    ("PlaylistManagerTest", 13, 13, 0, "88.9%", "79.4%", "核心逻辑覆盖完整；边界/模式/随机均有断言"),
    ("AudioFocusManagerTest", 7, 7, 0, "未采集*", "未采集*", "7/7 行为通过，4/4 焦点状态覆盖"),
    ("StateManagerTest", 8, 8, 0, "79.5%", "72.2%", "六字段写入/读取、非法模式兜底、清理"),
    ("PlaybackFlowIntegrationTest", 3, 3, 0, "N/A", "N/A", "跨模块集成，覆盖已计入上面两类"),
    ("ExampleUnitTest", 1, 1, 0, "N/A", "N/A", "工程模板冒烟测试"),
]

INT_ANALYSIS = [
    ("安装与启动", "PASS", "2026-11-21", "-", "-", "卸载旧签名包后重装"),
    ("媒体扫描与列表", "PASS", "2026-11-21", "中文文件名被 adb 截断", "低", "改用 ASCII 文件名 track1-3"),
    ("副屏探测", "PASS", "2026-11-21", "-", "-", "记录 displayId=2 / 3"),
    ("硬按键服务绑定", "PASS", "2026-11-21", "-", "-", "dumpsys 确认 Client AppBindRecord"),
    ("双屏列表/控制/状态/焦点", "NA", "未执行", "设备掉线", "高", "设备恢复后补测"),
    ("状态持久化", "PASS", "2026-11-21", "-", "-", "3 个 JVM 集成用例通过"),
    ("硬按键实际按键", "NA", "未执行", "设备掉线", "中", "设备恢复后补测"),
]

# ---------------- 生成 Excel ----------------
wb = Workbook()
ws = wb.active
ws.title = "集成测试报告"
ws.sheet_view.showGridLines = False

title(ws, "AutoMediaPlayer 智能座舱本地音乐播放器 · 集成测试报告", 10)
info(ws, 2, [("项目名称", "AutoMediaPlayer", 2), ("报告版本", "CP6 初版", 2)])
info(ws, 3, [("测试日期", "2026-11-21", 2), ("测试人员", "（填写姓名/学号）", 2)])
info(ws, 4, [("测试设备", "瑞特 ITX-3588J / Android 12 / 1920×720", 2), ("包名", "com.example.automediaplayer", 2)])
info(ws, 5, [("APK 版本", "6.0（真机实测安装 5.0）", 2), ("报告状态", "初版：7 PASS / 0 FAIL / 15 NA", 2)])
ws.merge_cells("A6:J6")
note = ws["A6"]
note.value = "判定标准：PASS=实际结果符合预期；FAIL=不符合；NA=未执行/不适用（不计入通过率）。NA 项不得写成“通过”。"
note.font = Font(name="微软雅黑", size=9, color="7F6000")
note.fill = PatternFill("solid", fgColor="FFF2CC")
note.alignment = Alignment(vertical="center", wrap_text=True)
note.border = BORDER
ws.row_dimensions[6].height = 30

headers = ["测试用例ID", "关联需求ID", "测试模块", "测试目的", "前置条件", "测试步骤", "预期结果", "实际结果", "状态", "备注"]
header(ws, 8, headers)

for idx, case in enumerate(CASES, start=9):
    body(ws, idx, case, heights=72)
    status_style(ws, idx, 9)

last = 8 + len(CASES)
pass_n = sum(1 for c in CASES if c[8] == "PASS")
fail_n = sum(1 for c in CASES if c[8] == "FAIL")
na_n = sum(1 for c in CASES if c[8] == "NA")
executed = pass_n + fail_n
summary_row = last + 2
ws.merge_cells(start_row=summary_row, start_column=1, end_row=summary_row, end_column=2)
ws.cell(row=summary_row, column=1, value="统计").font = Font(name="微软雅黑", size=10, bold=True)
ws.cell(row=summary_row, column=3, value=f"PASS {pass_n} / FAIL {fail_n} / NA {na_n}").font = Font(name="微软雅黑", size=10, bold=True)
ws.cell(row=summary_row, column=5, value=f"已执行通过率：{pass_n}/{executed} = 100%" if executed else "已执行通过率：N/A").font = Font(name="微软雅黑", size=10, bold=True)
ws.cell(row=summary_row, column=8, value=f"执行覆盖率：{executed}/{len(CASES)} = {executed/len(CASES):.1%}").font = Font(name="微软雅黑", size=10, bold=True)
for col in range(1, 11):
    ws.cell(row=summary_row, column=col).fill = PatternFill("solid", fgColor=PURPLE_LIGHT)
    ws.cell(row=summary_row, column=col).border = BORDER

ws.freeze_panes = "A9"
ws.auto_filter.ref = f"A8:J{last}"
widths = [13, 13, 16, 24, 26, 38, 34, 38, 10, 24]
for i, w in enumerate(widths, start=1):
    ws.column_dimensions[get_column_letter(i)].width = w
ws.page_setup.orientation = "landscape"
ws.page_setup.paperSize = ws.PAPERSIZE_A3
ws.page_setup.fitToWidth = 1
ws.page_setup.fitToHeight = 0
ws.sheet_properties.pageSetUpPr.fitToPage = True
ws.print_title_rows = "8:8"
ws.print_area = f"A1:J{summary_row}"

# ---------------- 单元测试结果分析 ----------------
ws2 = wb.create_sheet("单元测试结果分析")
ws2.sheet_view.showGridLines = False
title(ws2, "5.2.1 单元测试结果分析", 7)
header(ws2, 3, ["测试类", "测试方法数", "通过数", "失败数", "行覆盖率", "指令覆盖率", "备注"])
for r, row in enumerate(UNIT_ROWS, start=4):
    body(ws2, r, row, heights=40)
    ws2.cell(row=r, column=3).alignment = Alignment(horizontal="center", vertical="center")
    ws2.cell(row=r, column=4).alignment = Alignment(horizontal="center", vertical="center")
    ws2.cell(row=r, column=5).alignment = Alignment(horizontal="center", vertical="center")
    ws2.cell(row=r, column=6).alignment = Alignment(horizontal="center", vertical="center")

total_row = 4 + len(UNIT_ROWS)
body(ws2, total_row, ["总计", 32, 32, 0, "全工程 5.5%", "全工程 6.4%", "核心两类（Playlist+State）行覆盖 85.6%"], heights=40)
for col in range(1, 8):
    ws2.cell(row=total_row, column=col).font = Font(name="微软雅黑", size=9, bold=True)
    ws2.cell(row=total_row, column=col).fill = PatternFill("solid", fgColor=PURPLE_LIGHT)

ws2.merge_cells(start_row=total_row + 2, start_column=1, end_row=total_row + 2, end_column=7)
ws2.cell(row=total_row + 2, column=1,
         value="* AudioFocusManager 的 7 例测试通过，但 JaCoCo 对 Robolectric 沙箱加载的类未记录行覆盖，"
               "因此行/指令覆盖率显示“未采集”，不能等同于未测试。").font = Font(name="微软雅黑", size=9, color="7F6000")
ws2.cell(row=total_row + 2, column=1).alignment = Alignment(wrap_text=True, vertical="top")
ws2.row_dimensions[total_row + 2].height = 64
ws2.merge_cells(start_row=total_row + 3, start_column=1, end_row=total_row + 3, end_column=7)
ws2.cell(row=total_row + 3, column=1,
         value="覆盖率命令：.\\gradlew.bat createDebugCoverageReport；报告：app/build/reports/coverage/test/debug/index.html。"
               "全工程覆盖率包含尚未做 JVM 测试的 UI/Service/Adapter，故整体数值偏低。").font = Font(name="微软雅黑", size=9)
ws2.cell(row=total_row + 3, column=1).alignment = Alignment(wrap_text=True, vertical="top")
ws2.row_dimensions[total_row + 3].height = 64
for i, w in enumerate([30, 12, 10, 10, 13, 13, 46], start=1):
    ws2.column_dimensions[get_column_letter(i)].width = w
ws2.page_setup.orientation = "landscape"
ws2.page_setup.paperSize = ws2.PAPERSIZE_A4
ws2.page_setup.fitToWidth = 1
ws2.page_setup.fitToHeight = 0
ws2.sheet_properties.pageSetUpPr.fitToPage = True
ws2.print_area = f"A1:G{total_row + 3}"

# ---------------- 集成测试结果分析 ----------------
ws3 = wb.create_sheet("集成测试结果分析")
ws3.sheet_view.showGridLines = False
title(ws3, "5.2.2 集成测试结果分析", 6)
header(ws3, 3, ["测试场景", "测试状态", "执行时间", "问题描述", "严重程度", "解决方案"])
for r, row in enumerate(INT_ANALYSIS, start=4):
    body(ws3, r, row, heights=36)
    status_style(ws3, r, 2)
for i, w in enumerate([28, 12, 14, 26, 10, 30], start=1):
    ws3.column_dimensions[get_column_letter(i)].width = w
ws3.page_setup.orientation = "landscape"
ws3.page_setup.paperSize = ws3.PAPERSIZE_A4
ws3.page_setup.fitToWidth = 1
ws3.page_setup.fitToHeight = 0
ws3.sheet_properties.pageSetUpPr.fitToPage = True

r = 4 + len(INT_ANALYSIS) + 1
ws3.merge_cells(start_row=r, start_column=1, end_row=r, end_column=6)
ws3.cell(row=r, column=1, value="已执行场景全部通过；双屏/焦点/硬按键/后台/USB 等场景因设备掉线尚未执行，"
                               "不得计入通过率。").font = Font(name="微软雅黑", size=9, color="7F6000")
ws3.cell(row=r, column=1).alignment = Alignment(wrap_text=True, vertical="top")
ws3.row_dimensions[r].height = 36

# ---------------- 说明与证据 ----------------
ws4 = wb.create_sheet("说明与证据")
ws4.sheet_view.showGridLines = False
title(ws4, "测试环境、覆盖率与证据", 2)
lines = [
    ("测试设备", "瑞特 ITX-3588J（rk3588_s_raite），Android 12 / API 32，1920×720，density 213"),
    ("显示拓扑", "displayId=0 内置屏；displayId=2 HDMI 副屏；displayId=3 仪表虚拟屏"),
    ("测试包", "com.example.automediaplayer，versionName=6.0，versionCode=6"),
    ("单元测试", "32 例 / 0 失败；HTML：app/build/reports/tests/testDebugUnitTest/index.html"),
    ("覆盖率", "命令 createDebugCoverageReport；HTML：app/build/reports/coverage/test/debug/index.html"),
    ("集成证据", "docs/cp6_real_device_main.png；docs/cp6_test_run.log；docs/cp6_coverage_run.log"),
    ("设备状态", "测试中途 adb 掉线；PENDING/NA 场景需设备恢复后补测"),
    ("问题修复", "旧包签名不一致→卸载重装；中文文件名截断→ASCII track1-3；MediaProvider 小写 music 路径"),
    ("关联文档", "docs/CP6_Acceptance.md；docs/CP6_Unit_Test_Report.md；docs/CP6_Integration_Test_Report.md"),
]
r = 3
for k, v in lines:
    a = ws4.cell(row=r, column=1, value=k)
    a.font = Font(name="微软雅黑", size=10, bold=True)
    a.fill = PatternFill("solid", fgColor=PURPLE_LIGHT)
    a.border = BORDER
    a.alignment = Alignment(vertical="top")
    b = ws4.cell(row=r, column=2, value=v)
    b.font = Font(name="微软雅黑", size=10)
    b.border = BORDER
    b.alignment = Alignment(wrap_text=True, vertical="top")
    ws4.row_dimensions[r].height = 34
    r += 1
ws4.column_dimensions["A"].width = 16
ws4.column_dimensions["B"].width = 90
ws4.page_setup.orientation = "landscape"
ws4.page_setup.paperSize = ws4.PAPERSIZE_A4
ws4.page_setup.fitToWidth = 1
ws4.page_setup.fitToHeight = 0
ws4.sheet_properties.pageSetUpPr.fitToPage = True

OUT.parent.mkdir(parents=True, exist_ok=True)
wb.save(OUT)
print(f"saved: {OUT}")
print(f"cases={len(CASES)} pass={pass_n} fail={fail_n} na={na_n}")