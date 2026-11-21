# CP6 测试报告（单元测试 + 集成测试）

- 项目：AutoMediaPlayer 智能座舱本地音乐播放器
- 日期：2026-11-21
- 版本：`versionName=6.0`、`versionCode=6`
- 设备：瑞特 `ITX-3588J`，Android 12 / API 32，1920×720
- 测试命令：`.\gradlew.bat test --console=plain`
- Excel 报告：`docs/integration_test_report.xlsx`
- 单元测试 HTML：`app/build/reports/tests/testDebugUnitTest/index.html`
- 覆盖率 HTML：`app/build/reports/coverage/test/debug/index.html`

## 一、结论

| 项目 | 结果 |
| --- | --- |
| 单元/组件测试 | 32 例，32 通过，0 失败 |
| 真机已执行集成用例 | 7 项 PASS，0 项 FAIL |
| 未执行/待补测 | 15 项 NA（设备中途掉线） |
| 已执行通过率 | 7/7 = 100% |
| 全用例执行覆盖率 | 7/22 = 31.8% |
| 核心两类行覆盖率 | PlaylistManager + StateManager = 85.6% |
| 全工程行覆盖率 | 5.5%（含未做 JVM 测试的 UI/Service/Adapter） |

> 本报告是 CP6 初版。NA 仅表示“未执行/不适用”，不计入通过率，也不写成通过。

## 二、单元测试结果分析（教材 5.2.1）

| 测试类 | 方法数 | 通过 | 失败 | 行覆盖率 | 指令覆盖率 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| PlaylistManagerTest | 13 | 13 | 0 | 88.9% | 79.4% | 初始化、三种模式、边界、随机、空列表 |
| AudioFocusManagerTest | 7 | 7 | 0 | 未采集* | 未采集* | 7/7 行为通过，四类焦点状态覆盖 |
| StateManagerTest | 8 | 8 | 0 | 79.5% | 72.2% | 六字段、非法模式兜底、清理、有效期 |
| PlaybackFlowIntegrationTest | 3 | 3 | 0 | N/A | N/A | 切歌→保存→恢复的跨模块链路 |
| ExampleUnitTest | 1 | 1 | 0 | N/A | N/A | 模板冒烟 |
| **总计** | **32** | **32** | **0** | **全工程 5.5%** | **全工程 6.4%** | 核心两类行覆盖 85.6% |

`*` AudioFocusManager 的 7 例测试全部通过，但 JaCoCo 对 Robolectric 沙箱类加载器加载的类未记录
行覆盖，因此显示“未采集”；这不是“未测试”。其行为覆盖为四类焦点回调各至少 1 例。

覆盖率命令与报告：

```powershell
.\gradlew.bat createDebugCoverageReport
start app\build\reports\coverage\test\debug\index.html
```

说明：教材示例写的是 `app/build/reports/coverage/debug/`，当前 AGP 9.3 实际输出路径为
`app/build/reports/coverage/test/debug/`，内容一致。

## 三、集成测试结果分析（教材 5.2.2）

| 测试场景 | 测试状态 | 执行时间 | 问题描述 | 严重程度 | 解决方案 |
| --- | --- | --- | --- | --- | --- |
| 安装与启动 | PASS | 2026-11-21 | 旧包签名不一致 | 中 | 卸载旧版后重装 |
| 媒体扫描与列表 | PASS | 2026-11-21 | 中文文件名被 adb 截断 | 低 | 改用 track1-3.mp3 |
| 副屏探测 | PASS | 2026-11-21 | - | - | 记录 displayId=2 / 3 |
| 硬按键服务绑定 | PASS | 2026-11-21 | - | - | dumpsys 确认 Client AppBindRecord |
| 双屏列表/控制/状态/焦点 | NA | 未执行 | 设备掉线 | 高 | 设备恢复后补测 |
| 状态持久化（JVM 集成） | PASS | 2026-11-21 | - | - | 3 个用例通过 |
| 硬按键实际按键 | NA | 未执行 | 设备掉线 | 中 | 设备恢复后补测 |

## 四、集成测试用例摘要

完整 22 条用例、步骤、预期/实际结果见 Excel `docs/integration_test_report.xlsx` 的
“集成测试报告”工作表。已执行的 7 条：

| 用例 ID | 场景 | 状态 | 证据 |
| --- | --- | --- | --- |
| INT-001 | APK 安装与启动 | PASS | 安装成功、主界面渲染 |
| INT-002 | 媒体扫描与列表（8 首） | PASS | `docs/cp6_real_device_main.png` |
| INT-003 | 副屏探测（displayId=2） | PASS | `dumpsys display` |
| INT-004 | 厂商硬按键服务绑定 | PASS | `dumpsys activity services` |
| INT-020 | 切歌 → 持久化 → 恢复 | PASS | `PlaybackFlowIntegrationTest` |
| INT-021 | 循环模式持久化 | PASS | `PlaybackFlowIntegrationTest` |
| INT-022 | 状态清理（USB 拔出语义） | PASS | `PlaybackFlowIntegrationTest` |

未执行（NA）的场景：副驾屏列表/点歌/控制/进度/模式/封面/信息同步、MediaBrowserService、
副驾屏界面、后台播放、强杀恢复、USB 插入/拔出、音频焦点四态、硬按键实际按键。
原因均为测试中途 `adb devices` 掉线，需设备恢复后补测。

## 五、问题与修复

| 编号 | 问题 | 根因 | 处理 | 状态 |
| --- | --- | --- | --- | --- |
| ISS-01 | 安装失败 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 旧包与当前 debug 签名不一致 | 卸载旧版后重装 | 已解决 |
| ISS-02 | 新歌不进入 MediaStore | Windows adb 截断中文名；MediaProvider 将 Music 规范为小写 | 使用 ASCII 文件名 track1-3 | 已解决 |
| ISS-03 | 测试中途 adb 掉线 | 尚未确认，可能为 USB/休眠 | 重启 adb、等待 40 秒仍无设备 | 未关闭 |

## 六、交付物

- `docs/integration_test_report.xlsx`：教材附录模板要求的 Excel 集成测试报告。
- `docs/cp6_test_run.log`：`gradlew test` 原始日志。
- `docs/cp6_coverage_run.log`：覆盖率任务原始日志。
- `docs/cp6_real_device_main.png`：真机主界面（8 首歌曲）。
- `docs/CP6_Unit_Test_Report.md`、`docs/CP6_Integration_Test_Report.md`：分项报告。
- `app/build/outputs/apk/debug/app-debug.apk`：最终 APK（6.0）。