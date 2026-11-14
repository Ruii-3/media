# CP1 验收记录（第 1 周）

- 日期：2026-11-07
- 工程：`AutoMediaPlayer`
- 验收环境：Android Automotive 模拟器，API 35，1408×792 横屏，用户 0
- 构建环境：Gradle 9.5.0、AGP 9.3.0、JDK 17

## 一、交付物清单

| 交付物 | 位置 | 状态 |
| --- | --- | --- |
| MVP 需求规格（SWE.1） | `MVP_Requirements.txt` | 已提交 |
| 项目结构截图 | `docs/project_structure.png` | 已生成 |
| SWE.2 组件图（T1-2） | `docs/architecture_component.png` | 已生成 |
| 可播放单曲 APK | `app/build/outputs/apk/debug/app-debug.apk` | 已构建（约 12.3 MB） |
| 验收截图 | `docs/cp1_emulator_playing.png`、`docs/cp1_emulator_paused.png`、`docs/cp1_permission_denied.png` | 已生成 |

APK 校验结果：

- `aapt2 dump badging`：`package=com.example.automediaplayer`、`minSdk=31`、`targetSdk=37`、`launchable-activity=com.example.automediaplayer.ui.MainActivity`。
- 权限：`READ_MEDIA_AUDIO`、`READ_EXTERNAL_STORAGE (maxSdkVersion=32)`。
- `apksigner verify`：APK Signature Scheme v2 校验通过。

## 二、构建验证

| 检查项 | 命令/方式 | 结果 |
| --- | --- | --- |
| Debug APK 构建 | `.\gradlew.bat :app:assembleDebug` | 通过，BUILD SUCCESSFUL |
| APK 产物存在 | `app/build/outputs/apk/debug/app-debug.apk` | 通过 |
| APK 签名 | `apksigner verify --verbose` | 通过（v2） |
| 清单权限与 Activity | `aapt2 dump badging` | 通过 |

## 三、CP1 功能验收

模拟器测试素材：

- 第一轮：`TestSongA.wav`（60 秒）、`TestSongB.wav`（70 秒）、`ShortRing.wav`（10 秒），用于验证扫描与 45 秒过滤。
- 第二轮：`testdata/周传雄 - 男人·海洋.mp3`（4 分 02 秒，标题“男人海洋”、歌手“周传雄”），用于验证真实 MP3 元数据与播放。

在真机/台架上使用相同流程和 U 盘中的 MP3 文件即可复现。

| 编号 | 测试项 | 操作 | 预期结果 | 实际结果 | 结论 |
| --- | --- | --- | --- | --- | --- |
| CP1-01 | 启动扫描 | 授权后启动应用 | 扫描到音乐并显示列表 | 列表显示“共 2 首” | 通过 |
| CP1-02 | 运行时授权 | 首次启动点击 Allow | 授权后自动扫描 | 自动扫描并显示歌曲 | 通过 |
| CP1-03 | 拒绝权限 | 点击 Don’t allow | 不崩溃，给出提示与日志 | 界面显示“未授予音频读取权限，无法扫描音乐”，Logcat 输出警告 | 通过 |
| CP1-04 | 45 秒过滤 | 扫描 60s/70s/10s 三个文件 | 仅显示前两个 | ShortRing 未显示 | 通过 |
| CP1-05 | 点击播放 | 点击 TestSongA | 标题/歌手更新并开始播放 | 标题变为 TestSongA，Logcat：`Playback started: TestSongA` | 通过 |
| CP1-06 | 暂停/恢复 | 点击播放/暂停按钮 | 图标与状态同步切换 | 播放中按钮描述为“暂停”，暂停后为“播放” | 通过 |
| CP1-07 | 下一首 | 播放中点击下一首 | 切换到 TestSongB | 标题变为 TestSongB，Logcat：`Playback started: TestSongB` | 通过 |
| CP1-08 | 布局与可读性 | 查看横屏界面 | 左 70% 播放区、右 30% 列表，无重叠 | 与截图一致，按钮和文字清晰 | 通过 |
| CP1-09 | 真实 MP3 元数据 | 扫描并播放 `周传雄 - 男人·海洋.mp3` | 显示标题/歌手/专辑/04:02 并可播放 | 列表显示“共 1 首”，播放中标题“男人海洋”、歌手“周传雄” | 通过 |

关键 Logcat 证据：

```text
D/MusicScanner: query uri=content://media/external/audio/media, selection=is_music != 0 AND duration >= ?, args=45000
D/MusicScanner: query returned 2 rows
D/MusicScanner: scanMusicFiles: found 2 tracks
D/MainActivity: Playback started: TestSongA
D/MainActivity: Playback completed: TestSongA
D/MainActivity: Playback started: TestSongB
W/MainActivity: 音频读取权限被拒绝，无法扫描音乐
--- 第二轮真实 MP3 ---
D/MusicScanner: query returned 1 rows
D/MusicScanner: scanMusicFiles: found 1 tracks
D/MainActivity: Playback started: 男人海洋
```

## 四、教师抽问准备

### 1. MediaPlayer 状态机

教材要求掌握的主线：

```text
Idle
  └─ setDataSource() → Initialized
       └─ prepare()/prepareAsync() → Preparing
            └─ OnPrepared → Prepared
                 ├─ start() → Started
                 │    ├─ pause() → Paused ─ start() → Started
                 │    └─ 播放结束 → PlaybackCompleted ─ start() → Started
                 ├─ stop() → Stopped ─ prepare()/prepareAsync() → Prepared
                 └─ release() → Released
```

答问要点：

- `Idle` 是新建或 `reset()` 后的状态；未调用 `setDataSource()` 就 `start()` 会抛 `IllegalStateException`。
- 本周使用 `prepareAsync()`，在 `OnPreparedListener` 中再 `start()`，避免同步 `prepare()` 阻塞主线程造成 ANR。
- 切换歌曲时不能直接给同一个实例换数据源；先 `release()` 再新建实例，否则状态机非法。
- 播放结束进入 `PlaybackCompleted`，再次 `start()` 可从头播放；`stop()` 后必须重新准备才能播放。
- `release()` 必须调用，否则底层解码器、文件句柄泄漏；本周在切歌、播放器错误和 `Activity.onDestroy()` 中释放。
- 代码中用 `mediaPlayer === prepared` 判断回调是否属于当前实例，防止快速切歌时旧回调操作已释放的实例。

### 2. 权限流程

三步流程：

1. `ContextCompat.checkSelfPermission()` 检查是否已授权。
2. 未授权时 `ActivityCompat.requestPermissions()` 发起运行时申请，请求码为 `0x1001`。
3. `onRequestPermissionsResult()` 按请求码取回结果；授权则扫描，拒绝则输出日志并提示，不崩溃。

版本差异：

- Android 13（API 33）及以上：`android.permission.READ_MEDIA_AUDIO`。
- Android 12（API 32）及以下：`android.permission.READ_EXTERNAL_STORAGE`，清单中通过 `android:maxSdkVersion="32"` 限制。
- 分区存储（Android 10+）不允许直接遍历 `/sdcard/`；本周通过 `MediaStore` + `content://` Uri 访问，播放时 `setDataSource(context, uri)`。
- 清单声明只是“具备申请资格”，危险权限仍必须在运行时由用户确认。

### 3. 其他常见抽问

- 为什么过滤 45 秒以下音频？过滤铃声、通知音和提示音，避免污染音乐列表。
- 为什么查询要放在 `Dispatchers.IO`？`MediaStore` 底层是 SQLite/ContentProvider 查询，主线程执行可能卡顿或 ANR。
- `Cursor` 为什么用 `use {}`？保证正常与异常路径都能关闭游标，防止资源泄漏。
- 为什么用 `singleInstance`？车机场景保证只有一个播放界面实例，避免系统反复拉起导致界面叠加。
- 为什么播放 Uri 用 `ContentUris.withAppendedId()`？配合 `VOLUME_EXTERNAL` 覆盖内置存储与 U 盘，并符合分区存储的访问方式。

## 五、现场演示步骤

1. 将 FAT32 U 盘插入车机，U 盘内含若干时长不少于 45 秒的 MP3。
2. 安装 `app-debug.apk` 并启动 `AutoMediaPlayer`。
3. 首次启动点击 Allow 授予音频读取权限。
4. 确认右侧“歌曲列表”出现歌曲与“共 N 首”。
5. 点击一首歌曲，确认左侧标题/歌手更新并开始播放。
6. 点击播放/暂停按钮，确认图标与状态同步；点击下一首切换歌曲。
7. 如需演示拒绝路径：在系统设置中撤销权限后重新启动，确认界面提示且应用不崩溃。

## 六、已知限制与下周衔接

- 本周播放逻辑仍在 `MainActivity` 中，退到后台后进程被回收即停止；第 2 周迁移到 `MusicPlayService` 并加入前台通知。
- 尚未实现播放队列与顺序/循环/随机模式；当前上一首/下一首仅按列表索引切换。
- 尚未接入音频焦点、USB 热插拔监听、MediaSession、状态持久化与副驾屏。
- 本周要求“先插 U 盘再启动应用”；如果 MediaStore 尚未完成索引，列表可能为空，重新启动应用或触发媒体扫描即可。

## 七、模拟器黑屏排查记录

现象：模拟器主屏只有状态栏与底部车机控制栏，内容区域全黑；副屏显示 `Welcome, ClusterHome.`。

定位结果：Logcat 中 `com.android.car.carlauncher` 启动时抛出 `NullPointerException: Attempt to invoke virtual method 'android.car.Car.getCarManager(...)' on a null object reference`，即模拟器自带的 CarLauncher 崩溃，并非 AutoMediaPlayer 界面异常。冷启动模拟器（不加载损坏快照）后 CarLauncher 恢复，安装 APK 后 AutoMediaPlayer 正常显示白色播放界面。

处理建议：

1. Android Studio → Device Manager → 对应 AVD → Cold Boot Now。
2. 若仍黑屏，执行 Wipe Data 后重新启动模拟器。
3. 确认当前运行用户与安装用户一致（本 AVD 有 Driver 用户 0 和 10）；如提示 Activity 不存在，重新 Run 或执行 `adb install -r app/build/outputs/apk/debug/app-debug.apk`。
4. 应用本身启动后应为白色主题；若仍是黑屏，查看 Logcat 是否有 `AndroidRuntime` 崩溃或 `Activity class does not exist`。

补充记录（2026-11-07 10:50）：从 Android Studio 以 Debug 方式启动时，前两次进程在 `hwuiTask0` 线程触发 `FORTIFY: pthread_mutex_lock called on a destroyed mutex` 并产生 `Fatal signal 6 (SIGABRT)`；同一错误也出现在 `adb shell screencap` 进程，说明是模拟器图形后端（SwiftShader/HWUI）不稳定，不是应用 Java/Kotlin 异常。Android Studio 自动重启进程后，第三次启动正常显示界面并扫描到 1 首 MP3。若持续出现，优先将 AVD 图形模式改为 `Hardware - GLES 2.0` 或 `ANGLE (D3D11)`，避免软件渲染，并执行 Cold Boot；Debug 启动命令带 `--suspend`，看到 “Waiting For Debugger” 时等待调试器附加即可，也可先用 Run 方式验证。

补充记录（2026-11-07 11:03）：“未找到音乐文件”的另一个常见原因是同时运行了多个模拟器，而测试 MP3 只推送到了其中一个。实测当时 `emulator-5554`（车机）的 `/sdcard/Music` 有 `周传雄 - 男人·海洋.mp3`，但应用运行在 `emulator-5558`（Pixel Tablet），其 `/sdcard/Music` 为空，扫描结果为 0 行。执行 `adb -s emulator-5558 push ...` 并触发 `MEDIA_SCANNER_SCAN_FILE` 后，列表显示“共 1 首”，标题“男人海洋”、歌手“周传雄”、时长 04:02。多设备环境务必用 `-s <serial>` 指定目标模拟器。
