# CP6 单元测试报告

- 日期：2026-11-21
- 工程：`D:\ASdemo\AutoMediaPlayer`
- 包名：`com.example.automediaplayer`
- 版本：`versionCode=6`、`versionName=6.0`
- 测试命令：`.\gradlew.bat test --console=plain`（Linux/macOS 等价 `./gradlew test`）
- 原始日志：`docs/cp6_test_run.log`；覆盖率日志：`docs/cp6_coverage_run.log`
- 综合报告：`docs/CP6_Test_Report.md`；Excel：`docs/integration_test_report.xlsx`
- HTML 报告：`app/build/reports/tests/testDebugUnitTest/index.html`

## 一、总体结果

| 指标 | 结果 |
| --- | --- |
| 测试类 | 5 个 |
| 测试用例 | 32 例 |
| 失败 | 0 |
| 错误 | 0 |
| 跳过 | 0 |
| 构建结果 | `BUILD SUCCESSFUL` |

| 测试类 | 用例数 | 失败 | 说明 |
| --- | --- | --- | --- |
| `PlaylistManagerTest` | 13 | 0 | 队列初始化、三种模式、边界、随机、空列表 |
| `AudioFocusManagerTest` | 7 | 0 | 焦点申请/释放与 GAIN/LOSS/TRANSIENT/CAN_DUCK |
| `StateManagerTest` | 8 | 0 | 六字段保存、读取、无效模式兜底、清理、有效期 |
| `PlaybackFlowIntegrationTest` | 3 | 0 | 切歌 → 持久化 → 重建后恢复的跨模块链路 |
| `ExampleUnitTest` | 1 | 0 | 工程模板冒烟测试 |

> Robolectric 首次运行需要下载 `android-all` 运行时，`AudioFocusManagerTest` 首次耗时约 137 秒；
> 依赖预热后重复执行在秒级完成，属于正常现象，不是测试卡死。

## 二、覆盖率结果

教材 5.1.1 要求使用 `createDebugCoverageReport` 生成覆盖率报告。本工程已开启
`debug { enableUnitTestCoverage = true }`，执行：

```powershell
.\gradlew.bat createDebugCoverageReport
# 报告：app/build/reports/coverage/test/debug/index.html
```

| 被测类 | 行覆盖 | 指令覆盖 | 说明 |
| --- | --- | --- | --- |
| `PlaylistManager` | 88.9%（64/72） | 79.4%（390/491） | 队列主流程、模式、边界覆盖充分 |
| `StateManager` | 79.5%（31/39） | 72.2%（127/176） | 保存/读取/清理/过期判断 |
| `AudioFocusManager` | 未采集* | 未采集* | 7 例行为测试通过，四态覆盖 |
| `MyPlaybackState` | 100%（9/9） | 100%（87/87） | 数据模型与有效性判断 |
| `MusicData` | 71.4%（10/14） | 39.3%（46/117） | 时长格式化被间接覆盖 |
| 全工程 | 5.5%（123/2218） | 6.4%（722/11296） | 含未做 JVM 测试的 UI/Service/Adapter |

`*` JaCoCo 对 Robolectric 沙箱类加载器加载的类未记录探针，因此
`AudioFocusManager` 的行覆盖率显示为 0/未采集；这不代表没有测试。其四类焦点回调
（GAIN/LOSS/TRANSIENT/CAN_DUCK）均有行为断言，7 例全部通过。报告中不使用虚构的
95%/92%/90% 数字，避免把工具限制误写成质量结论。

核心两类（PlaylistManager + StateManager）合计行覆盖 85.6%（95/111），
是本周质量结论的主要依据。
## 三、测试框架与依赖

`app/build.gradle.kts` 增加：

```kotlin
testImplementation("io.mockk:mockk:1.13.8")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.1")
testImplementation("org.robolectric:robolectric:4.10.3")
testImplementation("androidx.test:core:1.5.0")
testImplementation("org.mockito:mockito-core:4.11.0")

testOptions {
    unitTests.isIncludeAndroidResources = true
}
```

分工：

| 框架 | 本工程用途 |
| --- | --- |
| JUnit4 | 测试基座、`@Before`、`@Test`、断言 |
| MockK | 模拟 `android.net.Uri`，让纯 Kotlin 队列逻辑脱离媒体文件 |
| Mockito | 模拟 `AudioManager`、`SharedPreferences`、`Editor` 链式调用 |
| Robolectric | 在 JVM 提供 Android 12（SDK 31）环境，跑 `AudioFocusRequest` 与真实 SharedPreferences |
| androidx.test:core | `ApplicationProvider` 获取 Robolectric Application Context |

## 四、PlaylistManagerTest（13 例）

| 用例 | 断言的行为/状态 |
| --- | --- |
| `setPlaylistWithoutIdStartsAtFirstSong` | 不传 id 时定位第 0 首，列表数量正确 |
| `setPlaylistWithValidIdKeepsThatSong` | 有效 id 定位到对应歌曲 |
| `setPlaylistWithInvalidIdFallsBackToFirstSong` | 无效 id 回退第 0 首，不越界 |
| `orderModeNextStopsAtLastSong` | 顺序模式最后一首返回 false 且索引不越界 |
| `orderModePreviousStopsAtFirstSong` | 顺序模式上一首停在 0 |
| `loopAllModeWrapsForwardAndBackward` | 列表循环首尾绕回 |
| `loopOneModeRepeatsNaturallyButManualNextAdvances` | 单曲循环只在自然播完重播，手动下一首仍换歌 |
| `shuffleModeKeepsContentAndCurrentSongWhenSwitching` | 切随机模式内容完整、当前曲不变、原始列表顺序不变 |
| `shuffleNextNeverStaysOnCurrentSong` | 随机下一首不会停在当前曲 |
| `shufflePreviousReturnsAlongHistory` | 随机上一首沿已播历史回退 |
| `moveToPositionReturnsSongAndUpdatesIndex` | 有效跳转返回目标歌曲并更新索引 |
| `moveToPositionRejectsInvalidIndex` | -1 与越界索引返回 null 且状态不变 |
| `emptyPlaylistIsSafe` | 空列表当前曲 null，前进/后退/跳转均安全 |

设计要点：固定 3 首测试歌曲，所有断言只依赖 id、位置、列表内容等公开行为，
不依赖 `Random` 的具体取值，因此可重复执行。

## 五、AudioFocusManagerTest（7 例）

| 用例 | 断言的行为/状态 |
| --- | --- |
| `requestFocusGrantedReturnsTrueAndHasFocus` | 系统授予焦点时返回 true，`hasFocus()` 为 true |
| `requestFocusDeniedReturnsFalseAndHasNoFocus` | 系统拒绝时返回 false，状态保持无焦点 |
| `abandonFocusClearsStateAndCallsSystem` | 释放后状态清除，并调用 `abandonAudioFocusRequest` |
| `gainCallbackRestoresFocusAndNotifiesListener` | TRANSIENT 后 GAIN 恢复焦点并回调业务 |
| `transientLossClearsFocusAndPausesListener` | 短暂丢失清焦点并触发暂停回调 |
| `canDuckKeepsFocusAndNotifiesListener` | CAN_DUCK 不丢失焦点，只触发降音回调 |
| `permanentLossClearsFocusAndNotifiesListener` | 永久丢失清焦点并触发停止回调 |

实现方式：Robolectric 提供 Android 环境；Mockito 模拟 `AudioManager`；
`ReflectionHelpers.setField` 注入私有 `audioManager`，`callInstanceMethod` 触发私有
`dispatchFocusChange`，从而在不接触真实音频设备的情况下验证四态。

## 六、StateManagerTest（8 例）

| 用例 | 断言的行为/状态 |
| --- | --- |
| `savePlaybackStateWritesSixFieldsAndApplies` | 六个键值全部写对，并调用 `apply()` |
| `loadPlaybackStateRestoresAllFields` | 六个字段从 SharedPreferences 正确还原 |
| `loadPlaybackStateFallsBackToOrderForUnknownMode` | 非法模式字符串回退 `ORDER` |
| `clearPlaybackStateRemovesAllKeysAndApplies` | 六个键全部 remove 并提交 |
| `timestampWithin24HoursIsValid` | 24 小时内状态有效 |
| `timestampOlderThan24HoursIsExpired` | 超过 24 小时过期 |
| `missingTimestampIsExpired` | 时间戳缺失视为过期 |
| `playbackStateRequiresPositiveMusicId` | 歌曲 id 必须为正数 |

## 七、PlaybackFlowIntegrationTest（3 例，跨模块）

| 用例 | 验证链路 |
| --- | --- |
| `playNextThenPersistAndRestoreKeepsSongAndProgress` | `PlaylistManager` 切歌 → `StateManager` 写入 → 新实例读取，歌曲/位置/进度/模式全部一致 |
| `loopModeSelectionSurvivesStateReload` | 列表循环切歌绕回第 0 首，模式持久化后仍为 `LOOP_ALL` |
| `clearingStatePreventsRestoreAfterUsbRemoval` | 保存有效状态后清理，`loadValidPlaybackState()` 返回 null，对应 USB 拔出清状态 |

这组测试使用 Robolectric 的真实 SharedPreferences，不 Mock 存储层，属于组件间集成验证。

## 八、运行与查看报告

```powershell
.\gradlew.bat test --console=plain
start app\build\reports\tests\testDebugUnitTest\index.html
```

现场验收时建议先执行 `.\gradlew.bat clean test`，展示从零构建到全部测试通过；
若担心网络，可提前执行一次预热 Robolectric 依赖，再使用 `--offline`。