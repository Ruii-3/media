# CP6 集成测试报告（初版）

- 日期：2026-11-21
- 工程：`D:\ASdemo\AutoMediaPlayer`
- 包名：`com.example.automediaplayer`
- APK：`versionName=6.0`（最终包见 `docs/CP6_Acceptance.md`）
- 真机：瑞特 `ITX_3588J`，序列号 `C5203027DC6E1CE5`
- 系统：Android 12（API 32），1920×720，density 213
- 测试方式：真机黑盒 + adb 取证；跨模块链路补充 Robolectric 组件集成测试

> 设备状态说明：真机在完成“安装/媒体扫描/副屏探测/硬按键服务绑定”后从 adb 掉线，
> `adb devices` 为空。本文把已执行项和待补测项分开记录，未执行项不写成“通过”。
> 设备恢复后按第六节命令继续，即可把本报告升级为正式版。

## 一、测试环境

| 项目 | 取值 | 取证命令 |
| --- | --- | --- |
| 设备型号 | `ITX-3588J`（`rk3588_s_raite`） | `adb shell getprop ro.product.model` |
| 系统版本 | Android 12 / API 32 | `adb shell getprop ro.build.version.release` |
| 屏幕 | 1920×720，density 213 | `adb shell wm size` / `wm density` |
| 车机特性 | `android.hardware.type.automotive`、`activities_on_secondary_displays` | `adb shell pm list features` |
| 显示拓扑 | `displayId=0` 内置屏；`displayId=2` HDMI 副屏；`displayId=3` 仪表虚拟屏 | `adb shell dumpsys display` |
| 厂商硬按键 | `com.raite.car.hardkey/.InputCoreService` | `adb shell dumpsys activity services com.raite.car.hardkey` |
| 媒体库 | 8 首（原有 5 首 + 本次推送 3 首） | `adb shell content query --uri content://media/external/audio/media` |
| 权限 | `READ_MEDIA_AUDIO`、`POST_NOTIFICATIONS` 已授予 | `adb shell dumpsys package com.example.automediaplayer` |

## 二、判定标准

- **PASS**：实际结果与预期一致，且有命令输出、日志或截图。
- **FAIL**：实际结果与预期不一致，记录偏差与修复动作。
- **PENDING**：设备/外设条件不具备，尚未执行；不得计为通过。

## 三、真机执行记录

| 编号 | 场景 | 操作步骤 | 预期结果 | 实际结果 | 结论 | 证据 |
| --- | --- | --- | --- | --- | --- | --- |
| IT-01 | 安装与启动 | `adb install` 后启动 `MainActivity` | 安装成功，主界面正常渲染 | 首次因旧包签名不一致失败；卸载旧版后安装成功，主界面正常 | PASS | `docs/cp6_real_device_main.png` |
| IT-02 | 媒体库扫描 | 推送 3 首 MP3，触发 MediaScanner，重启应用 | 列表包含新歌，数量 8 | MediaStore 返回 8 首，主界面“共 8 首” | PASS | 截图与 `content query` 输出 |
| IT-03 | 副屏探测 | `dumpsys display`、`pm list features` | 存在第二显示且系统支持副屏 Activity | `displayId=2` HDMI 1920×720；`activities_on_secondary_displays=true` | PASS | 命令输出 |
| IT-04 | 硬按键服务绑定 | 启动应用后 `dumpsys activity services com.raite.car.hardkey` | 厂商服务存在且与本应用建立连接 | 输出 `Client AppBindRecord ... com.example.automediaplayer`，`recentCallingPackage` 为本应用 | PASS | 命令输出 |
| IT-05 | 单曲播放与通知栏 | 点击歌曲，观察进度、暂停图标、通知栏 | 起播、进度递增、通知栏可控制 | 设备掉线，未执行 | PENDING | 待补 |
| IT-06 | 后台播放 | 播放中按 Home，等待 30 秒再返回 | 音乐持续，返回后状态同步 | 设备掉线，未执行 | PENDING | 待补 |
| IT-07 | 三种模式 | 顺序/列表循环/单曲循环/随机连续切换 | 切歌行为与界面模式一致 | 设备掉线，未执行 | PENDING | 待补 |
| IT-08 | 强杀恢复 | 播放 5 秒后 `am force-stop`，重新启动 | 恢复歌曲、进度、模式与播放态 | 设备掉线，未执行 | PENDING | 待补 |
| IT-09 | USB 插拔 | 插入含 MP3 的 U 盘；播放中拔出 | 插入自动扫描；拔出停播、清列表、Toast | 设备掉线，未执行 | PENDING | 待补 |
| IT-10 | 副驾屏同步 | 主屏切歌/暂停/切模式，副屏反向控制 | 两屏状态一致，共用一个 MediaSession | 设备掉线，未执行 | PENDING | 待补 |
| IT-11 | 音频焦点四态 | 导航播报、来电、其他播放器抢占 | CAN_DUCK 降音、TRANSIENT 暂停、LOSS 停止、GAIN 恢复 | 设备掉线，未执行 | PENDING | 待补 |
| IT-12 | 方向盘硬按键 | 按 MODE / SEEK_UP / SEEK_DOWN | 播放暂停、下一首、上一首 | 设备掉线，未执行 | PENDING | 待补 |

已执行 4 项全部 PASS；待补测 8 项。

## 四、JVM 跨模块集成测试

| 编号 | 链路 | 预期 | 实际 | 结论 |
| --- | --- | --- | --- | --- |
| IT-J01 | `PlaylistManager` 切歌 → `StateManager` 保存 → 新实例恢复 | 歌曲/位置/进度/模式一致 | 一致 | PASS |
| IT-J02 | 列表循环切歌绕回 + 模式持久化 | 回到第 0 首，模式仍为 `LOOP_ALL` | 一致 | PASS |
| IT-J03 | 保存状态 → 清理 → 再读取 | 对应 USB 拔出清状态，读取返回 null | 一致 | PASS |

对应测试类：`app/src/test/java/com/example/automediaplayer/PlaybackFlowIntegrationTest.kt`，
使用 Robolectric 的真实 SharedPreferences，不 Mock 存储层。

## 五、问题与修复

| 编号 | 现象 | 根因 | 处理 | 状态 |
| --- | --- | --- | --- | --- |
| ISS-01 | `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 车机上旧 APK 与当前 debug 签名不一致 | 卸载旧版后重新安装，应用数据被清空 | 已解决 |
| ISS-02 | 三首新歌未进入 MediaStore | Windows `adb push` 中文文件名被截断；MediaProvider 把 `Music` 规范为小写 `music` | 改用 `track1.mp3`/`track2.mp3`/`track3.mp3` 纯 ASCII 文件名，标题仍由 MP3 元数据提供 | 已解决 |
| ISS-03 | 测试中途 `adb devices` 为空 | USB 连接/车机休眠中断，尚未确认具体原因 | 重启 adb 服务并等待 40 秒仍无设备；待重新插拔/唤醒后补测 | 未关闭 |

## 六、待补测步骤（设备恢复后执行）

```powershell
# 0. 确认设备与安装最终包
adb devices -l
adb install -r -g app\build\outputs\apk\debug\app-debug.apk

# 1. 单曲播放 + 通知栏
adb shell am start -n com.example.automediaplayer/.ui.MainActivity
adb shell dumpsys media_session | Select-String "state=|metadata|active="

# 2. 后台播放
adb shell input keyevent 3
Start-Sleep -Seconds 30
adb shell dumpsys media_session | Select-String "state=PLAYING"

# 3. 媒体键与模式
adb shell input keyevent 85
adb shell input keyevent 87
adb shell input keyevent 88

# 4. 强杀恢复
adb shell am force-stop com.example.automediaplayer
adb shell am start -n com.example.automediaplayer/.ui.MainActivity
adb shell run-as com.example.automediaplayer cat shared_prefs/AutoMediaPlayerPrefs.xml

# 5. USB 插拔（需人工插拔 U 盘）
adb logcat -c
adb logcat -v time -s USBStorageObserver MusicPlayService

# 6. 音频焦点（需导航/电话/其他播放器）
adb logcat -v time -s AudioFocusManager MusicPlayService

# 7. 副驾屏
adb shell dumpsys display | Select-String "mDisplayId|DisplayDeviceInfo"
adb shell dumpsys activity activities | Select-String "PassengerActivity|mDisplayId"
adb shell screencap -d 2 -p /sdcard/cp6_passenger.png

# 8. 硬按键
adb logcat -v time -s HardKey HardKeyPolicyManager MusicPlayService
```

## 七、证据清单

- `docs/cp6_real_device_main.png`：真机主界面，显示“共 8 首”和紫藤色 UI。
- `docs/cp6_test_run.log`：`gradlew test` 原始运行日志。
- `app/build/test-results/testDebugUnitTest/*.xml`：32 例测试的机器可读结果。
- `app/build/reports/tests/testDebugUnitTest/index.html`：HTML 测试报告。
- 设备信息、MediaStore、display、hardkey 服务输出：见本文第一、三节命令。

## 八、结论

- 单元测试与 JVM 跨模块集成测试全部通过（32/32）。
- 真机已完成安装、媒体扫描、副屏探测、硬按键服务绑定 4 项，均 PASS。
- 播放、后台、模式、强杀恢复、USB、双屏、焦点、实际硬按键 8 项因设备掉线标为 PENDING，
  不计入通过；设备恢复后按第六节继续即可完成正式版报告。