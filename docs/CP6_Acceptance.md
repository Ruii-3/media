# CP6 验收记录（第 6 周：质量保证——单元测试与集成测试）

- 日期：2026-11-21
- 工程：`D:\ASdemo\AutoMediaPlayer`
- 包名：`com.example.automediaplayer`
- 版本：`versionCode=6`、`versionName=6.0`
- 测试命令：`.\gradlew.bat test --console=plain`
- 原始日志：`docs/cp6_test_run.log`；覆盖率日志：`docs/cp6_coverage_run.log`
- 覆盖率报告：`app/build/reports/coverage/test/debug/index.html`

## 一、CP6 清单对照

| 编号 | 验收项 | 交付物 | 结果 |
| --- | --- | --- | --- |
| CP6-① | 全部单元测试通过 | 32 例 / 0 失败；HTML 报告 `app/build/reports/tests/testDebugUnitTest/index.html` | 通过 |
| CP6-② | 集成测试报告初版 | `docs/integration_test_report.xlsx`（教材附录模板）；`docs/CP6_Test_Report.md`；`docs/CP6_Integration_Test_Report.md` | 初版完成（7 PASS / 15 NA，真机待补测） |
| CP6-③ | 最终 APK | `app/build/outputs/apk/debug/app-debug.apk`（6.0） | 通过（构建） |
| CP6-④ | 抽问测试设计与断言原则 | 本文第三节 | 已准备 |

APK 指纹：

| 项目 | 值 |
| --- | --- |
| 路径 | `app/build/outputs/apk/debug/app-debug.apk` |
| 大小 | `12,522,022` 字节 |
| 生成时间 | `2026-11-21 14:43:59` |
| SHA-256 | `0FDC8B0DDA7288C2C902D87A9721365AC16C55053519D9AD63996ECAA418EEBF` |

## 二、单元测试结果

| 测试类 | 用例数 | 失败 | 覆盖重点 |
| --- | --- | --- | --- |
| `PlaylistManagerTest` | 13 | 0 | 初始化、有效/无效 id、ORDER/LOOP_ALL/LOOP_ONE/SHUFFLE、边界、跳转、空列表 |
| `AudioFocusManagerTest` | 7 | 0 | 申请成功/失败、释放、GAIN/LOSS/TRANSIENT/CAN_DUCK、hasFocus 一致性 |
| `StateManagerTest` | 8 | 0 | 六字段写入 + apply、读取、非法模式兜底、清理、24 小时有效期 |
| `PlaybackFlowIntegrationTest` | 3 | 0 | 切歌 → 持久化 → 重建恢复；循环模式；清理后不可恢复 |
| `ExampleUnitTest` | 1 | 0 | 模板冒烟 |
| **合计** | **32** | **0** | 无失败、无错误、无跳过 |

覆盖率（`createDebugCoverageReport`）：PlaylistManager 行覆盖 88.9%、StateManager 79.5%、
两者合计 85.6%；全工程行覆盖 5.5%（UI/Service/Adapter 尚未纳入 JVM 测试）。
AudioFocusManager 的 7 例行为测试全部通过，但 JaCoCo 对 Robolectric 沙箱类未记录行覆盖，
报告中标注为“未采集”，不能等同于未测试。

任务覆盖：

- T6-1 测试依赖：MockK / Mockito / Robolectric / coroutines-test / androidx.test:core 已配置，`gradlew test` 成功。
- T6-2 PlaylistManagerTest：13 例 ≥ 9 例。
- T6-3 AudioFocusManagerTest：7 例 ≥ 4 例，四态均有断言。
- T6-4 StateManagerTest：8 例 ≥ 2 例，含六字段与 `apply()`、非法模式兜底。
- T6-5 集成测试与报告初版：`docs/CP6_Integration_Test_Report.md`。
- T6-6 扩展：增加跨模块集成测试与无效索引/空列表/清理状态等边界用例。

## 三、测试设计与断言原则（抽问准备）

**问：测试金字塔 70/20/10 在本项目怎么落地？**

答：70% 是 JUnit/MockK/Mockito 的 JVM 单元测试，覆盖 PlaylistManager、StateManager、
AudioFocusManager 的算法与协作边界；20% 是 PlaybackFlowIntegrationTest 这类跨模块测试，
以及真机上的服务/MediaSession 集成；10% 是 UI/端到端手工验收（安装、播放、双屏、USB）。
本项目的单元测试最多、执行最快，真机测试只验证“模块拼起来后”的行为。

**问：为什么单元测试要 Mock 掉 Uri 和 AudioManager？**

答：单元测试要隔离外部依赖。`Uri` 是 Android 框架对象，真实构造会让纯 Kotlin 队列逻辑
依赖设备；`AudioManager` 需要真实音频通道和系统焦点。Mock 后测试只验证“队列怎么切歌”
和“焦点回调怎么驱动业务”，不依赖媒体文件、车机音频策略，因此可在 CI/JVM 上稳定重复执行。

**问：Robolectric 和模拟器测试有什么区别？**

答：Robolectric 在 JVM 上模拟 Android 框架（本工程固定 `@Config(sdk=[31])`），启动快、
无需安装 APK，适合验证 `AudioFocusRequest` 构造、Handler、SharedPreferences 等框架交互；
模拟器运行真实 Android 系统，能验证 MediaPlayer、MediaSession、通知栏和跨进程行为，但启动慢。
两者不是替代关系：前者覆盖逻辑与框架契约，后者覆盖系统集成。

**问：`verify(mockEditor).apply()` 验证的是什么？**

答：验证“保存动作最终提交”这一协作行为。`apply()` 是异步落盘，测试无法也不应断言
磁盘文件何时写完，但可以确认业务代码调用了 `apply()`，而不是漏提交或误用 `commit()`。
同时逐个 `verify` 六个 `put*` 键值，保证写入内容正确。

**问：为什么断言行为与状态，而不是实现细节？**

答：实现细节（私有字段、内部循环、随机数具体取值）会随重构变化，断言它们会让测试
“为改代码而改测试”。本工程的断言都落在公开 API 的可观察结果上：当前歌曲 id、索引、
列表内容、`hasFocus()`、回调次数、SharedPreferences 键值。这样重构内部算法时，
只要行为不变，测试仍然有效。

**问：随机模式如何避免测试不稳定？**

答：不预测随机序列，只断言不变量：队列内容完整、当前曲不被随机切走、下一首不等于当前曲、
上一首沿历史栈回退。固定 3 首歌曲保证候选集非空，因此这些不变量在任何随机结果下都成立。

**问：为什么 StateManagerTest 既用 Mockito 又有真实 SharedPreferences 的集成测试？**

答：Mockito 测试精确验证“写了哪些键、是否 apply”，执行快、隔离好；
`PlaybackFlowIntegrationTest` 用 Robolectric 的真实 SharedPreferences 验证
“切歌 → 保存 → 新实例恢复”的链路，能发现键名不一致、序列化/反序列化等协作问题。
两者分别对应单元层与组件集成层。

**问：当前测试还有什么没覆盖？**

答：MediaPlayer 异步状态机、真实 MediaSession 跨进程、通知栏、USB 广播、副驾屏
`PassengerActivity` 和厂商硬按键需要真机/台架验证；另外尚未接入 JaCoCo 行覆盖率报告，
后续可用 `jacoco` 插件补齐覆盖率数字。

## 四、现场演示脚本

1. 执行 `.\gradlew.bat clean test --console=plain`，展示 `BUILD SUCCESSFUL`。
2. 打开 `app/build/reports/tests/testDebugUnitTest/index.html`，指出 32 例、0 失败。
3. 打开 `docs/CP6_Unit_Test_Report.md`，说明 13/7/8/3 的分层与关键用例。
4. 打开 `docs/CP6_Integration_Test_Report.md`，说明真机已执行 4 项、待补测 8 项及原因。
5. 安装 `app/build/outputs/apk/debug/app-debug.apk`，确认版本 6.0 与核心功能可用。

## 五、已知限制

- 真机在集成测试中途掉线，播放/后台/模式/强杀/USB/双屏/焦点/硬按键 8 项标为 PENDING，
  未计入通过；设备恢复后按集成报告第六节补测。
- Robolectric 首次运行需下载 `android-all`，耗时约 137 秒；依赖预热后恢复正常。
- 硬按键实际按键仅在瑞特台架可验证；非该厂商设备会自动跳过。
- 尚未配置 JaCoCo 覆盖率门槛；当前以用例覆盖场景表作为质量依据。