# CP5 验收记录（第 5 周：音频焦点管理与座舱关键场景）

- 日期：2026-11-21
- 工程：`AutoMediaPlayer`（`D:\ASdemo\AutoMediaPlayer`）
- 包名：`com.example.automediaplayer`
- 版本：`versionCode=5`、`versionName=5.0`
- 代码基线：第 4 周 MediaBrowserService + MediaSession 播放内核
- 构建：`.\gradlew.bat :app:assembleDebug --offline --console=plain` → `BUILD SUCCESSFUL`
- APK：`app/build/outputs/apk/debug/app-debug.apk`

> 验收环境说明：教师明确要求 CP5 在真机车机上测试，模拟器不作为验收依据。
> 当前代码、APK、架构图和测试脚本已完成；CP5-②③④ 的真机现象需按
> `docs/CP5_RealDevice_Test_Guide.md` 在车机上复现并补录日志/截图。
> 应用内“座舱场景演示”只用于讲解与真实回调相同的业务分支，不能替代真机验收证据。

## 一、CP5 交付物与验收对照

| 编号 | 验收项 | 实现位置 | 结果 |
| --- | --- | --- | --- |
| CP5-① | 具备音频焦点管理与副驾屏控制器的 APK | `MusicPlayService.kt`、`AudioFocusManager.kt`、`PassengerActivity.kt`、`activity_passenger.xml`、`app/libs/hardkey.aar`；APK 已构建 | 通过（构建） |
| CP5-② | 三类焦点场景与降音恢复 | `AudioFocusListener` 四回调 + 座舱场景演示入口；CAN_DUCK 0.3 → GAIN 1.0 | 代码完成，真机待测 |
| CP5-③ | USB 插拔全流程 | `USBStorageObserver.kt` + `MusicPlayService.handleUsbConnected/Disconnected` | 代码完成，真机 U 盘待测 |
| CP5-④ | 双屏协同（台架） | `PassengerActivity` 共享 `MusicPlayService/MediaSession`；动态查询副屏 displayId | 代码完成，真机副驾屏待测 |

配套材料：

- `docs/week5_component.png`：组件图（MainActivity、PassengerActivity、MusicPlayService、MediaSession、AudioFocusManager、USBStorageObserver、PlaylistManager、StateManager）。
- `docs/week5_focus_sequence.png`：音频焦点四态与主/副驾屏同步时序图。
- `tools/generate_week5_diagrams.py`：两张图的生成脚本。
- `docs/CP5_RealDevice_Test_Guide.md`：真机车机安装、焦点、USB、双屏测试与取证步骤。

构建与自动化验证结果：

| 项目 | 结果 |
| --- | --- |
| `:app:assembleDebug` | `BUILD SUCCESSFUL` |
| `:app:testDebugUnitTest` | `BUILD SUCCESSFUL` |
| APK 大小 | `13,085,798` 字节 |
| APK 生成时间 | `2026-11-21 09:39:38` |
| APK SHA-256 | `C08C959B9AF2E89B974D1F68F77E470EE8D0B2A560451A1F66540AC55C545943` |

## 二、音频焦点管理

### 1. 四态与业务动作

| 系统回调 | 含义 | 本工程业务动作 | 是否自动恢复 |
| --- | --- | --- | --- |
| `AUDIOFOCUS_GAIN` | 重新拿到焦点（导航播报结束/电话挂断） | `setVolume(1.0f, 1.0f)`；仅当 `wasPlayingBeforeFocusLoss=true` 时 `play()` | 仅恢复“丢失前确实在播放”的场景 |
| `AUDIOFOCUS_LOSS_TRANSIENT` | 短暂丢失（来电等） | 记录 `wasPlayingBeforeFocusLoss=isPlaying()`，然后 `pause()` | 焦点回来后由 GAIN 续播 |
| `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK` | 可降音（导航播报） | 不停播，`setVolume(0.3f, 0.3f)` | GAIN 时恢复 1.0 |
| `AUDIOFOCUS_LOSS` | 永久丢失（其他播放器接管） | 清除续播标记，`stopPlayback()` + `abandonAudioFocus()` | 不自动恢复，必须用户重新播放 |

关键实现：

- `AudioFocusManager.requestAudioFocus()` 使用 `AudioFocusRequest`、`USAGE_MEDIA`、`CONTENT_TYPE_MUSIC`。
- `setWillPauseWhenDucked(false)`：系统不直接暂停，由本工程自己降音到 30%，便于展示“降音不中断”。
- 普通 `pause()` **不释放焦点**；只有 `stopPlayback()`、永久丢失和 `onDestroy()` 才 `abandonAudioFocus()`。
  否则暂停后收不到后续 GAIN，无法区分“用户主动暂停”和“被电话临时打断”。
- `play()` 与 `playMusicAt()` 播放前都经过 `requestAudioFocusBeforePlay()`；硬按键、通知栏、副驾屏、状态恢复最终都走同一入口。
- `PlaybackState.extras` 携带 `FOCUS_STATE`，主屏与副驾屏的焦点标签同步更新：
  `NORMAL`、`DUCKED`、`TRANSIENT_PAUSED`、`LOST`。

### 2. 课堂模拟入口

主界面右上角“座舱场景演示”提供四个按钮：

1. 模拟导航播报（降音 30%）→ `SIMULATE_AUDIO_FOCUS` + `LOSS_TRANSIENT_CAN_DUCK`
2. 模拟来电（瞬时暂停）→ `LOSS_TRANSIENT`
3. 模拟播报结束（GAIN 恢复）→ `GAIN`
4. 模拟其他播放器抢占（永久丢失）→ `LOSS`

实现位置：`MainActivity.showCockpitDemo()` → `MediaController.sendCommand()` →
`MusicPlayService.onCommand()` → `simulateAudioFocus()` → 与真实回调相同的
`audioFocusListener` 方法。日志会先打印“仅模拟业务分支”，再打印对应焦点状态。

> 重要：模拟命令不会调用系统 `AudioManager`，不会改变设备真实焦点；验收时不能把
> 模拟日志说成“系统真的发生了导航播报”。真实台架演示时直接播放导航/来电，观察同样日志。

### 3. 焦点演示步骤

1. 播放任意歌曲，确认主屏焦点标签为“焦点：正常”，通知栏显示暂停按钮。
2. 点击“模拟导航播报（降音 30%）”，确认焦点标签变为“焦点：降音 30%”，音乐不中断、音量明显降低。
3. 点击“模拟播报结束（GAIN 恢复）”，确认标签恢复“焦点：正常”、音量恢复 100%，且不会重新开始一首歌。
4. 点击“模拟来电（瞬时暂停）”，确认标签“焦点：已暂停”、播放按钮变为播放态。
5. 点击“模拟播报结束（GAIN 恢复）”，确认自动续播（因为丢失前确实在播）。
6. 先手动暂停，再模拟“来电 → GAIN”，确认不会莫名自动播放。
7. 点击“模拟其他播放器抢占（永久丢失）”，确认停止、焦点标签“焦点：已丢失”，再次点击播放才恢复。

期望 logcat：

```text
MusicPlayService: simulateAudioFocus: LOSS_TRANSIENT_CAN_DUCK (仅模拟业务分支)
MusicPlayService: focus CAN_DUCK: 音量降到 30%，继续播放
MusicPlayService: simulateAudioFocus: GAIN (仅模拟业务分支)
MusicPlayService: focus GAIN: 恢复音量，wasPlaying=false
```

## 三、厂商硬按键（T5-3）

`app/libs/hardkey.aar` 已作为 `implementation(files("libs/hardkey.aar"))` 接入，包名为
`raite.car.hardkey`。服务在 `onCreate()` 中通过 `HardKeyPolicyManager.getInstance(applicationContext)`
取得进程级单例，并注册到媒体中心场景：

```kotlin
hardKeyPolicyManager?.addKeyEventCallBack(
    hardKeyEventCallback,
    HardKeyPolicyManager.SCENE_MEDIA_CENTER_CTRL // 550
)
```

按键映射：

| AAR 常量 | 值 | 业务动作 |
| --- | --- | --- |
| `KeyEventExt.KEYCODE_MODE` | 509 | 短按切换播放/暂停 |
| `KeyEventExt.KEYCODE_SEEK_UP` | 512 | 下一首 |
| `KeyEventExt.KEYCODE_SEEK_DOWN` | 511 | 上一首 |
| 标准 `KEYCODE_MEDIA_PLAY_PAUSE/NEXT/PREVIOUS` | Android 标准值 | 兼容系统媒体键 |

设计要点：

- 按键回调复用 `togglePlayPause()/playNext()/playPrevious()`，与 UI、MediaSession、通知栏共用同一套播放逻辑；
- 硬按键触发播放时同样经过 `requestAudioFocusBeforePlay()`，不会绕过音频焦点；
- 非该厂商设备（模拟器/普通手机）没有 `com.raite.car.hardkey` 包，`initHardKeyPolicy()` 会直接跳过并打日志，
  不会因绑定不到厂商服务而崩溃或反复重连；
- Android 11+ 已在 Manifest 声明 `<queries><package android:name="com.raite.car.hardkey"/></queries>`；
  否则包可见性会让 `getPackageInfo()` 抛 `NameNotFoundException`，真机上会误判为“厂商服务不存在”而跳过 T5-3。
- `onDestroy()` 调用 `removeKeyEventCallBack()`，避免进程级单例继续强引用已销毁的 Service；
- 长按回调已接入但暂只记日志，可按车型扩展为快退/语音唤醒。

真机验证（仅瑞特台架可实测；先确认厂商包存在）：

```powershell
adb shell pm list packages | Select-String "raite|hardkey"
adb shell dumpsys activity services com.raite.car.hardkey
adb logcat -v time -s HardKey HardKeyPolicyManager MusicPlayService AudioFocusManager
```

按下方向盘静音/MODE 键，预期日志包含 `onKeyEvent key=509`，音乐在播放/暂停之间切换；
`dumpsys media_session` 的 state 同步变化。若车型键值与教材不同，按 `KeyEventExt` 或实际
`keyEvent.keyCode` 调整映射，不要修改 AAR。

## 四、USB 插拔全流程

### 1. 三层结构

| 层 | 类/方法 | 职责 |
| --- | --- | --- |
| 广播监听层 | `USBStorageObserver.start()` | 注册 `ACTION_MEDIA_MOUNTED/UNMOUNTED/REMOVED/BAD_REMOVAL`，并添加 `file` scheme |
| 事件归一化层 | `USBStatusCallback` | 把多个系统广播归一成 `onUSBConnected/onUSBDisconnected` |
| 业务联动层 | `MusicPlayService` | 插入重扫；拔出停播、清队列、清元数据、清持久化状态、通知客户端 |

`IntentFilter.addDataScheme("file")` 不能漏：存储广播的 data 是 `file:///storage/...`，
IntentFilter 默认不匹配 scheme，漏掉会完全收不到插拔事件。

### 2. 插入流程

1. 系统发送 `ACTION_MEDIA_MOUNTED`，data 为 U 盘挂载点。
2. `USBStorageObserver` 过滤主存储 `/storage/emulated/`，只接受可移动卷。
3. 回调 `onUSBConnected()`，服务 Toast“U 盘已连接，正在重新扫描”。
4. `reloadPlaylist()` 在 IO 线程调用 `MusicScanner` 扫描 MediaStore。
5. 主线程 `PlaylistManager.setPlaylist()`，`notifyChildrenChanged()` + `COMMAND_PLAYLIST_UPDATED`。
6. 主屏与副驾屏重新订阅，列表出现新歌；不会自动替用户开始播放。

### 3. 拔出流程（顺序不可乱）

1. `ACTION_MEDIA_UNMOUNTED/REMOVED/BAD_REMOVAL` → `onUSBDisconnected()`。
2. `mediaPlayer.stop()` + `reset()`，先让播放器脱离已经失效的文件 URI。
3. `isPrepared=false`、`playbackStarted=false`，清 `pending*`、`pendingRestoreState`。
4. `playlistManager.setPlaylist(emptyList(), 0)` 清空队列。
5. `abandonAudioFocus()`，焦点标签回到正常。
6. `stateManager.clearPlaybackState()`，避免下次启动恢复已拔掉 U 盘的歌曲。
7. 停止前台通知，`setMetadata(null)`、`STATE_NONE`、`notifyChildrenChanged`、`COMMAND_PLAYLIST_UPDATED`。
8. 主屏/副驾屏列表清空、当前歌曲信息清空；Toast“U 盘已断开，播放列表已清空”。

### 4. 演示方式

**真实 U 盘（优先）：** 插入含 MP3 的 U 盘，等待系统挂载完成；播放中直接拔出，
观察停播、列表清空和 Toast。再次插入，观察自动重扫。

**无 U 盘时的应用内模拟（需注明）：** 点击“座舱场景演示 → 模拟拔出 U 盘”，观察同一业务分支；
点击“模拟插入 U 盘”重新扫描。模拟命令不改变系统真实挂载状态，只用于课堂演示。

可选 adb 证据：

```powershell
adb shell dumpsys media_session
adb shell logcat -d -s MusicPlayService USBStorageObserver AudioFocusManager
```

## 五、副驾屏与双屏协同

### 1. 组件关系

```text
MainActivity ──setLaunchDisplayId──> PassengerActivity
      │                                  │
      └──── MediaBrowser/MediaController ─┴────> MusicPlayService
                                                    │
                                              MediaSession
                                                    │
                                         PlaybackState / Metadata
```

`PassengerActivity` 的关键约束：

- 不持有 `MediaPlayer`，不自己扫描媒体库；
- `MediaBrowser` 订阅同一个 `MusicPlayService` 的媒体树；
- `MediaController` 由同一个 `sessionToken` 创建，所有播放动作走 `transportControls`；
- 主屏切歌/暂停/拖动进度/切模式后，副驾屏通过 `MediaController.Callback` 自动跟随；
- 副驾屏点歌通过 `playFromMediaId()` 回到服务端，不存在第二个播放内核。

主界面“座舱场景演示 → 在副驾屏打开控制器”会：

1. 读取 `DisplayManager.displays`，动态选择 `displayId != DEFAULT_DISPLAY` 的副屏；
2. 检查 `FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS`；
3. 使用 `ActivityOptions.makeBasic().setLaunchDisplayId(displayId)` 启动 `PassengerActivity`；
4. 没有副屏时降级到主屏打开控制器并 Toast 提示，便于录屏说明逻辑。

副屏选择顺序：优先显示名包含 `passenger`/“副驾”的屏幕，其次第一个处于 ON 的副屏，
最后回退到第一个副屏。量产车型的 displayId 顺序因厂商而异，不应写死为 2。

### 2. 真机双屏取证（CP5-④）

CP5 要求真机车机验收，`overlay_display_devices` 虚拟显示只属于开发期调试手段，
**不能作为 CP5-④ 的验收证据**，本节记录在真实台架/副驾屏上的取证命令：

```powershell
# 1. 确认真机已连接，并查看车机上报的屏幕
adb devices -l
adb shell dumpsys display | Select-String "mDisplayId|DisplayDeviceInfo|FLAG_SECURE"

# 2. 安装并启动应用，在主屏点击“座舱场景演示 → 在副驾屏打开控制器”
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3. 主屏、副驾屏分别截图（displayId 以 dumpsys 实际输出为准）
adb shell screencap -d 0 -p /sdcard/cp5_main.png
adb shell screencap -d <副屏displayId> -p /sdcard/cp5_passenger.png
adb pull /sdcard/cp5_main.png docs/cp5_main.png
adb pull /sdcard/cp5_passenger.png docs/cp5_passenger.png

# 4. 抓取同一个 MediaSession 的状态，证明两屏共用一个播放内核
adb shell dumpsys media_session | Select-String "com.example.automediaplayer|state=|metadata"
```

说明：`PassengerActivity` 按教材要求声明 `android:exported="false"`，因此不能直接用
`adb shell am start -n ...` 从 shell 启动；请通过应用内入口启动。adb 用于查询 displayId、
截图和抓取 MediaSession 日志。若车机只暴露一个逻辑显示（部分车型副驾屏由独立系统进程渲染），
应在验收记录中注明实际拓扑，不能伪造双屏截图。
### 3. 双屏验收步骤

1. 主屏播放“男人海洋”，打开副驾屏控制器。
2. 副驾屏应显示同一首歌、同一进度、同一模式、同一焦点标签。
3. 主屏点“下一首”，副驾屏标题、列表高亮、进度同步变化。
4. 副驾屏点“暂停”，主屏按钮、通知栏、`dumpsys media_session` 同步变为 PAUSED。
5. 主屏切“单曲循环”，副驾屏模式文字同步。
6. 主屏模拟 CAN_DUCK，两个屏都显示“焦点：降音 30%”。

## 六、现场演示脚本（约 3 分钟）

1. 安装 `app-debug.apk`，授予音频读取与通知权限。
2. 播放一首歌，展示主屏焦点标签“正常”和通知栏控制。
3. 打开“座舱场景演示”，依次演示：降音 30% → GAIN 恢复 → 来电瞬时暂停 → GAIN 续播 → 永久丢失停止。
4. 真实 U 盘演示插入重扫、播放中拔出停播清列表。
5. 按方向盘 MODE/静音键，展示短按切换播放/暂停，并用 logcat 证明走 `HardKey` 回调。
6. 在主屏点击“在副驾屏打开控制器”，展示双屏同歌、主屏切歌副屏跟随、副屏暂停主屏同步。
7. 执行 `adb shell dumpsys media_session`，指出 `active=true`、`state=PLAYING/PAUSED` 与同一 metadata。
8. 打开 `docs/week5_component.png` 与 `docs/week5_focus_sequence.png` 说明组件关系与焦点时序。

## 七、抽问准备

**问：四种音频焦点状态有什么区别？**

答：`GAIN` 是重新获得；`LOSS_TRANSIENT` 是短暂丢失，通常来电，应暂停并记住播放态；
`LOSS_TRANSIENT_CAN_DUCK` 是允许降音继续播放，通常导航播报；`LOSS` 是永久丢失，
其他播放器接管，应停止且不自动恢复。

**问：为什么 CAN_DUCK 后要恢复 1.0，而不是继续 0.3？**

答：0.3 只是让路期间的临时音量；GAIN 表示播报结束，若不恢复，用户会一直觉得音乐声音小。
本工程在 GAIN 回调里统一 `setVolume(1.0f,1.0f)`。

**问：为什么普通暂停不能 abandonAudioFocus？**

答：abandon 后系统不会再向本应用发送 GAIN，来电结束就无法区分“用户主动暂停”与“被临时打断”。
本工程只在 `stopPlayback()`、永久丢失和 `onDestroy()` 释放焦点。

**问：为什么要记录 `wasPlayingBeforeFocusLoss`？**

答：如果用户本来就是暂停状态，来电结束后 GAIN 不应自动播放。只有丢失前确实在播，
才在 GAIN 时续播。

**问：USB 广播为什么要加 `file` scheme？**

答：`ACTION_MEDIA_*` 的 data 是 `file:///storage/...`；IntentFilter 不声明 scheme 时，
默认不匹配任何带 scheme 的 Intent，监听器会收不到广播。

**问：为什么拔出 U 盘要先 stop 再清列表？**

答：MediaPlayer 仍持有已移除文件的 URI；先清队列再停，可能出现读文件失败或状态错乱。
先停并 reset，再清队列和元数据，最后通知客户端刷新。

**问：副驾屏为什么不直接 new MediaPlayer？**

答：会产生两个解码器、两份进度和两套通知，主屏与副驾屏各放各的。副驾屏只做
`MediaBrowser + MediaController` 客户端，共享同一个 `MusicPlayService` 与 `MediaSession`，
才能保证状态一致。

**问：副驾屏如何拿到主屏的状态？**

答：`MediaController.registerCallback()` 订阅 `onPlaybackStateChanged/onMetadataChanged/onSessionEvent`；
服务端每次 `setPlaybackState()`、`setMetadata()`、`sendSessionEvent()` 都会推送给所有客户端。

## 八、已知限制

- 没有真实车机台架时，焦点场景与 USB 插拔用应用内模拟命令演示，文档和界面均标注“模拟”。
- `overlay_display_devices` 虚拟显示仅用于开发期调试，不作为 CP5-④ 真机验收证据。
- `PassengerActivity` 按教材设置为 `exported=false`，必须从主界面入口启动。
- USB 拔出会清空当前播放状态；再次插入后不会自动恢复拔出前的歌曲，需用户重新点歌。
- 硬按键依赖厂商 `com.raite.car.hardkey` 服务；非该厂商设备会自动跳过，adb 媒体键仍可用。
