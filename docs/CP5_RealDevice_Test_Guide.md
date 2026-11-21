# CP5 真机车机测试指南

教师要求 CP5 在真机车机上验收，模拟器不作为验收依据。本指南按“装 APK → 焦点 →
USB → 双屏 → 取证”的顺序执行；应用内的“座舱场景演示”只用于讲解业务分支，
不能替代真机上的导航/来电/USB/副屏现象。

## 0. 前置条件

| 项目 | 要求 |
| --- | --- |
| 车机系统 | Android 12（API 31）及以上；当前工程 `minSdk=31` |
| 连接方式 | USB adb，或车机开启网络 adb 后 `adb connect <车机IP>:5555` |
| U 盘 | FAT32/exFAT，内含 3 首 MP3；插入车机数据/媒体 USB 口 |
| 音频焦点 | 车机自带导航、蓝牙电话、另一个音乐 App（至少准备两种） |
| 双屏 | 车机有副驾屏/第二显示区；确认系统支持 `activities_on_secondary_displays` |
| 权限 | 音频读取、通知；部分车机还需在系统设置里允许自启动/后台运行 |

先确认设备与系统：

```powershell
adb devices -l
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell wm size
adb shell pm list features | Select-String "activities_on_secondary|automotive"
```

> 若车机低于 Android 12，APK 会因 `minSdk=31` 安装失败。此时需要先做兼容改造
> （降低 minSdk、为 `startForeground` 增加版本分支、确认存储权限），不要在课堂上直接改签名安装。

## 1. 安装与启动

```powershell
adb install -r -g app\build\outputs\apk\debug\app-debug.apk
adb shell pm grant com.example.automediaplayer android.permission.READ_MEDIA_AUDIO
adb shell pm grant com.example.automediaplayer android.permission.POST_NOTIFICATIONS
adb shell am start -n com.example.automediaplayer/.ui.MainActivity
adb shell dumpsys package com.example.automediaplayer | Select-String "versionName|versionCode"
```

车机上确认：

- 首次启动弹出音频读取/通知权限；
- 列表出现 U 盘或 `/sdcard/Music` 中的歌曲；
- 点击歌曲能播放，进度条与通知栏同步。

如果列表为空，先确认 MediaStore 已索引 U 盘：

```powershell
adb shell content query --uri content://media/external/audio/media --projection _id:_display_name:duration
adb shell sm list-volumes all
adb shell dumpsys mount | Select-String "storage|media_rw"
```

必要时对单曲触发扫描（不同车机厂商命令可能不同）：

```powershell
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///storage/XXXX-XXXX/Music/test.mp3
adb shell cmd media scan /storage/XXXX-XXXX/Music
```

## 2. 音频焦点真机测试

开始前清空日志并只保留本应用标签：

```powershell
adb logcat -c
adb logcat -v time -s AudioFocusManager MusicPlayService
```

### 场景 A：导航播报（CAN_DUCK，降音 30%）

1. 播放音乐，记下当前音量与进度。
2. 在车机打开导航并触发语音播报（或导航提示音）。
3. 预期：音乐不暂停、声音降到约 30%，主屏焦点标签“焦点：降音 30%”。
4. 播报结束：音量恢复 100%，焦点标签“焦点：正常”，进度连续。

期望日志：

```text
AudioFocusManager: focus=LOSS_TRANSIENT_CAN_DUCK
MusicPlayService: focus CAN_DUCK: 音量降到 30%，继续播放
AudioFocusManager: focus=GAIN
MusicPlayService: focus GAIN: 恢复音量，wasPlaying=false
```

> `wasPlaying=false` 是正常的：CAN_DUCK 没有暂停，GAIN 只负责恢复音量，不需要续播。

### 场景 B：来电/语音助手（LOSS_TRANSIENT，瞬时暂停）

1. 播放中触发蓝牙电话或车机语音助手占用音频。
2. 预期：音乐暂停，焦点标签“焦点：已暂停”，记录 `wasPlaying=true`。
3. 通话/语音结束：自动续播，进度从暂停点继续。
4. 再测试“先手动暂停，再触发来电 → 结束后”，预期不会自动播放。

期望日志：

```text
AudioFocusManager: focus=LOSS_TRANSIENT
MusicPlayService: focus LOSS_TRANSIENT: 记录播放态并暂停
AudioFocusManager: focus=GAIN
MusicPlayService: focus GAIN: 恢复音量，wasPlaying=true
```

### 场景 C：其他播放器抢占（LOSS，永久丢失）

1. 播放音乐，打开另一个音乐 App 并开始播放。
2. 预期：本应用停止，焦点标签“焦点：已丢失”，通知栏不再显示暂停态。
3. 回到本应用点播放：重新申请焦点后可以继续，不能自动恢复。

期望日志：

```text
AudioFocusManager: focus=LOSS
MusicPlayService: focus LOSS: 永久丢失，停止播放
AudioFocusManager: abandonAudioFocus
```

采证：

```powershell
adb shell dumpsys audio | Select-String "Audio Focus stack|focus"
adb shell dumpsys media_session
adb exec-out screencap -p > docs\cp5_focus_duck_real.png
```

## 3. USB 真机插拔测试

### 插入

1. 车机启动应用，确认当前列表。
2. 插入含 MP3 的 U 盘，等待系统挂载完成（约 2–10 秒）。
3. 预期：Toast“U 盘已连接，正在重新扫描”，列表出现 U 盘歌曲，主屏/副驾屏同步刷新；
   不会自动替用户开始播放。

```powershell
adb logcat -v time -s USBStorageObserver MusicPlayService
adb shell sm list-volumes all
```

期望日志：

```text
USBStorageObserver: USB mounted: /storage/XXXX-XXXX
MusicPlayService: USB connected: rescan playlist
MusicPlayService: Media tree loaded: N items
```

### 播放中拔出

1. 播放 U 盘中的歌曲。
2. 直接拔出 U 盘（或先在系统里卸载）。
3. 预期顺序：音乐停止 → 当前歌曲信息清空 → 列表清空 → Toast“U 盘已断开，播放列表已清空”；
   再次启动应用不会恢复已拔掉 U 盘的歌曲。

```powershell
adb logcat -v time -s USBStorageObserver MusicPlayService
adb shell dumpsys media_session
adb shell run-as com.example.automediaplayer cat shared_prefs/AutoMediaPlayerPrefs.xml
```

采证：

- 插入后列表截图；
- 拔出后空列表截图；
- 上述 logcat 原始文本；
- `AutoMediaPlayerPrefs.xml` 已清空或不存在。

## 4. 副驾屏真机测试

### 确认副屏

```powershell
adb shell dumpsys display | Select-String "mDisplayId|DisplayDeviceInfo|DisplayInfo"
adb shell dumpsys activity activities | Select-String "PassengerActivity|mDisplayId"
```

记录 displayId、分辨率与名称。应用的 `launchPassengerDisplay()` 会优先选择名称含
`passenger`/“副驾”的副屏，其次选第一个 ON 的副屏，不再写死 `displayId=2`。

### 演示

1. 主屏播放一首歌。
2. 点击主屏右上角“座舱场景演示 → 在副驾屏打开控制器”。
3. 预期副驾屏出现 `PassengerActivity`，显示同一首歌、进度、模式和焦点标签。
4. 主屏切歌/暂停/拖动进度/切模式，副驾屏同步；副驾屏点歌/暂停，主屏与通知栏同步。

采证：

```powershell
adb shell dumpsys media_session
adb shell dumpsys activity activities | Select-String "PassengerActivity|mDisplayId"
adb shell screencap -d <副屏displayId> -p /sdcard/cp5_passenger_real.png
adb pull /sdcard/cp5_passenger_real.png docs\cp5_passenger_real.png
adb exec-out screencap -p > docs\cp5_main_real.png
```

> `PassengerActivity` 按教材要求 `exported=false`，必须从应用内入口启动；
> 用 `adb shell am start -n ...` 直接启动会因未导出被拒绝，这是安全设计，不是故障。

如果车机没有 `FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS`，记录该事实；这属于硬件/系统不支持，
不能用主屏降级画面冒充双屏验收。可改用厂商提供的副屏投屏/多屏 API，或向教师申请台架。

## 5. 方向盘硬按键（T5-3）

工程已接入 `app/libs/hardkey.aar`（包名 `raite.car.hardkey`），由
`MusicPlayService.initHardKeyPolicy()` 注册 `SCENE_MEDIA_CENTER_CTRL = 550`。
该库通过 AIDL 绑定厂商系统服务 `com.raite.car.hardkey/.InputCoreService`，
只有瑞特（raite）车机台架才有；普通手机/模拟器会自动跳过，不影响其他功能。

### 前置确认

```powershell
adb shell pm list packages | Select-String "raite|hardkey"
adb shell dumpsys activity services com.raite.car.hardkey
```

第一条命令应能看到 `package:com.raite.car.hardkey`。若看不到，说明该车机不是
该厂商台架，T5-3 无法实测，应在验收记录中注明，不要用 adb 媒体键冒充硬按键。

### 测试步骤

1. 安装并启动应用，确认服务已创建：
   `adb shell dumpsys activity services com.example.automediaplayer | Select-String MusicPlayService`
2. 清日志并过滤硬按键标签：

```powershell
adb logcat -c
adb logcat -v time -s HardKey HardKeyPolicyManager MusicPlayService AudioFocusManager
```

3. 启动播放后按方向盘 MODE（`KEYCODE_MODE=509`）：预期播放/暂停切换。
4. 按 SEEK_UP（`512`）：预期下一首；按 SEEK_DOWN（`511`）：预期上一首。
5. 每次按键都应先走 `requestAudioFocusBeforePlay()`；从暂停态按键播放后，
   `dumpsys audio` 中能看到本应用重新持有焦点。

期望日志：

```text
MusicPlayService: hardkey callback registered: scene=SCENE_MEDIA_CENTER_CTRL
HardKey: onKeyEvent key=509 param1=0 param2=0
MusicPlayService: togglePlayPause
```

### 常见问题

**没有 `hardkey callback registered`：** 先确认车机是否安装 `com.raite.car.hardkey`；
再确认 APK 已包含 `<queries><package android:name="com.raite.car.hardkey"/></queries>`
（Android 11+ 包可见性），最后看 `HardKeyPolicyManager` 是否打印 `tryConnectService -> reconnect`。
若服务存在但一直重连，检查厂商服务是否被系统冻结或需要系统签名权限。

**有回调但按键无动作：** 核对 `onKeyEvent key=` 的实际键值，不同车型可能映射不同；
当前只映射 509/511/512 与标准 `KEYCODE_MEDIA_*`，其余键值只打日志不处理。

采证：硬按键 logcat 原始文本 + 按键前后主屏播放状态截图。
## 6. 现场取证清单

| 证据 | 命令/文件 | 必填 |
| --- | --- | --- |
| 设备信息 | `adb devices -l`、型号/系统版本 | 是 |
| APK 安装 | `adb install` 成功输出、`dumpsys package` 版本 5.0 | 是 |
| 焦点降音/恢复 | logcat + 主屏焦点标签截图 | 是 |
| 焦点瞬时暂停/续播 | logcat + 截图 | 是 |
| 焦点永久丢失 | logcat + 截图 | 是 |
| USB 插入 | logcat + 列表截图 | 是 |
| USB 拔出 | logcat + 空列表截图 + 持久化清空 | 是 |
| 双屏同歌 | 主屏/副屏截图各一张 | 是 |
| 双屏反向控制 | 副屏暂停后主屏/通知栏截图 | 是 |
| 会话一致性 | `dumpsys media_session` 原始文本 | 是 |
| 硬按键 T5-3 | 厂商包存在 + HardKey logcat + 状态截图 | 有台架时必填 |

## 7. 常见问题

**列表为空：** 检查 `READ_MEDIA_AUDIO`、U 盘挂载路径、MediaStore 是否索引；部分车机只允许
系统文件管理器读取 U 盘，需在系统设置中授权应用访问。

**USB 广播收不到：** 确认服务已启动（`dumpsys activity services | Select-String MusicPlayService`）、
`USBStorageObserver` 已注册、IntentFilter 含 `file` scheme；部分厂商会拦截第三方应用接收媒体挂载广播。

**焦点没有变化：** 确认导航/电话确实申请了音频焦点；部分车机导航走独立音频通道，不会触发
第三方 App 的焦点回调，此时用另一个音乐 App 验证 LOSS，并在验收记录中说明车机差异。

**副驾屏不出现：** 检查 `dumpsys display` 是否有第二块 display、系统 feature 是否为 true、
Activity 是否被厂商限制在默认屏；`PassengerActivity` 的 `exported=false` 是预期配置。

**APK 装不上：** 车机 Android 版本低于 12，或签名/安装权限受限；不要用 `adb install` 绕过系统限制，
先与教师确认车机版本和调试权限。
