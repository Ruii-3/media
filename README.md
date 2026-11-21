# AutoMediaPlayer（智能座舱本地音乐播放器）

第 5 周：音频焦点管理与座舱关键场景。应用在
“MediaBrowserService 提供内容 + MediaController 发送控制 + MediaSession 统一广播状态”的架构上，
增加音频焦点四态处理、USB 热插拔、副驾屏控制器、厂商方向盘硬按键与座舱场景模拟入口。

## 当前进度

| 周次 | 检查点 | 状态 |
| --- | --- | --- |
| 第 1 周 | CP1：权限 → 扫描 → 列表 → 单曲播放 | 已完成 |
| 第 2 周 | CP2：后台播放、播放队列、四模式、通知栏、UI 优化 | 已完成 |
| 第 3 周 | CP3：MediaBrowserService、MediaSession、adb 媒体键、多屏消费基础 | 已完成 |
| 第 4 周 | CP4：车机 UI/UX、设计草图、强杀恢复、状态持久化清单 | 已完成 |
| 第 5 周 | CP5：音频焦点、USB 插拔、副驾屏、硬按键、座舱场景演示 | 代码/APK 完成，真机待验收 |

第 5 周验收材料见 `docs/CP5_Acceptance.md`、
`docs/week5_component.png` 与 `docs/week5_focus_sequence.png`；
第 4、3、2、1 周记录分别保留在 `docs/CP4_Acceptance.md`、`docs/CP3_Acceptance.md`、
`docs/CP2_Acceptance.md`、`docs/CP1_Acceptance.md`。

## 技术基线

- Kotlin + ViewBinding + RecyclerView + 协程
- 包名：`com.example.automediaplayer`
- minSdk 31 / targetSdk 37 / compileSdk 37
- Gradle 9.5.0 + AGP 9.3.0 + JDK 17
- 分层：`ui` / `adapter` / `manager` / `service` / `model` / `util`

## 已实现功能

### 播放与后台

- `MusicPlayService` 继承 `MediaBrowserService`，持有 `MediaPlayer`、播放队列与 `MediaSession`。
- `MediaBrowser` 负责连接服务并用 `onGetRoot / onLoadChildren` 订阅媒体树。
- `MediaController` 由 `sessionToken` 创建，通过 `transportControls` 与 `sendCommand` 控制播放。
- 播放时调用 `startForeground()`，通知类型为 `mediaPlayback`。
- `MediaSession` 通过 `PlaybackState` / `MediaMetadata` 同步播放态、进度、标题、歌手、专辑与时长。
- `MediaSession.Callback` 统一处理 `MediaController`、系统媒体中心、蓝牙/方控与 adb 媒体键的命令；
  自绘通知的三个按钮通过 `PendingIntent.getService()` 进入服务，最终复用同一组播放方法。
- `MediaStyle` 通知绑定 `sessionToken`，提供上一首、暂停/播放、下一首；点击通知回到播放界面。
- Home 退出后继续播放，Activity 解绑不影响服务端会话。

### 媒体协议与多屏基础

- Manifest 声明 `android.media.browse.MediaBrowserService`，服务 `exported=true`。
- `onLoadChildren()` 先返回当前媒体树；扫描未完成时返回空列表，完成后 `notifyChildrenChanged()`。
- 客户端通过 `SubscriptionCallback` 把 `MediaItem` 映射为 `MusicData`。
- 播放模式通过自定义命令 `SET_PLAY_MODE` 进入 `MediaSession.Callback.onCommand`。
- 重新扫描通过 `RELOAD_PLAYLIST` 命令触发，权限授予后可即时刷新 U 盘歌曲。
- 主屏、通知栏、系统媒体中心、方控/蓝牙和副驾屏消费同一个 `MediaSession`。
- adb 验证：`input keyevent 85/87/88` 与 `cmd media_session dispatch play|pause|next|previous`。

### 音频焦点（第 5 周）

- `AudioFocusManager` 使用 `AudioFocusRequest` 申请 `AUDIOFOCUS_GAIN`，屏蔽 API 细节。
- `GAIN` 恢复音量；只有焦点丢失前确实在播放，才自动续播。
- `LOSS_TRANSIENT` 记录播放态后暂停；`LOSS_TRANSIENT_CAN_DUCK` 降音到 30% 不暂停；
  `LOSS` 永久丢失后停止并释放焦点，不自动恢复。
- 普通 `pause()` 不释放焦点，避免来电结束后收不到 `GAIN`；`stopPlayback()`/`onDestroy()` 才释放。
- `PlaybackState.extras` 携带焦点状态，主屏与副驾屏显示“正常 / 降音 30% / 已暂停 / 已丢失”。
- 主界面“座舱场景演示”可模拟四类焦点事件；模拟不改变系统真实焦点，界面和文档均注明。

### USB 热插拔（第 5 周）

- `USBStorageObserver` 注册 `MOUNTED/UNMOUNTED/REMOVED/BAD_REMOVAL`，并添加 `file` scheme。
- 插入：重新扫描 MediaStore，`notifyChildrenChanged()` + `COMMAND_PLAYLIST_UPDATED`。
- 拔出：先停并 reset `MediaPlayer`，再清队列、清元数据、清持久化状态，最后通知所有客户端。
- 主存储 `/storage/emulated/` 被过滤，只识别可移动卷，避免开机误触发。
- 无 U 盘时，可用主界面“模拟插入/拔出 U 盘”复用同一业务分支；真实插拔仍由系统广播驱动。

### 副驾屏（第 5 周）

- `PassengerActivity` 是独立第二屏 Activity，不持有 `MediaPlayer`，不重复扫描媒体库。
- 通过 `MediaBrowser` 订阅同一个 `MusicPlayService`，通过 `MediaController` 控制播放。
- `MainActivity` 动态查询 `DisplayManager`，使用 `setLaunchDisplayId()` 在副屏启动；
  无副屏时降级到主屏打开控制器并 Toast 提示，仅用于开发期调试。
- 主屏切歌/暂停/进度/模式/焦点状态通过 `MediaSession` 自动同步到副驾屏。

### 厂商硬按键（第 5 周 T5-3）

- `app/libs/hardkey.aar`（包名 `raite.car.hardkey`）已通过 `implementation(files("libs/hardkey.aar"))` 接入。
- `MusicPlayService` 在 `onCreate()` 注册 `HardKeyPolicyManager`，场景 `SCENE_MEDIA_CENTER_CTRL = 550`。
- `KEYCODE_MODE(509)` 切换播放/暂停，`KEYCODE_SEEK_UP(512)` 下一首，`KEYCODE_SEEK_DOWN(511)` 上一首。
- 硬按键播放同样先申请音频焦点；服务销毁时注销回调。
- 非该厂商设备没有对应系统服务时自动跳过并打日志，不会崩溃；adb 媒体键仍可用。

### 播放队列与模式

- `PlaylistManager` 负责原始列表、当前队列、索引与切歌算法，不持有播放器。
- 四种模式：顺序播放、列表循环、单曲循环、随机播放。
- 随机模式使用已播历史栈，上一首沿历史回退，而不是重新随机。
- 单曲循环仅自然播完时原地重播；手动“下一首”仍换歌。
- 顺序模式最后一首播完后回到 0 秒并暂停；列表循环自动绕回第一首。

### 播放状态持久化

- `StateManager` 使用 `SharedPreferences` 保存最小状态集合：歌曲 id、列表位置、进度、模式、播放态、时间戳。
- 保存策略：操作后 1 秒防抖、播放中每 5 秒保底、`onDestroy()` 再兜底。
- 状态有效期 24 小时；过期、歌曲 id 非法或文件为空时不会恢复，并清理旧值。
- 服务先等待歌单扫描完成，再按歌曲 id 恢复；id 失效时用列表位置兜底。
- `prepareAsync()` 成功后 `seekTo()`，再按保存值决定继续播放或保持暂停。
- 用户手动点歌优先于旧状态恢复，避免启动后被旧歌曲覆盖。

### 本地扫描与权限

- Android 13+ 使用 `READ_MEDIA_AUDIO`，Android 12 及以下使用 `READ_EXTERNAL_STORAGE`。
- 通过 `ContentResolver` 查询 `MediaStore`，扫描所有外部存储卷（含 U 盘）。
- 过滤时长小于 45 秒的铃声/提示音，按标题升序展示。
- 权限拒绝、空列表、损坏文件均不崩溃，并给出日志与界面提示。
- Android 13+ 运行时申请 `POST_NOTIFICATIONS`；拒绝后播放仍可用，但通知栏控制不可见。

### 界面（紫藤色 + 白色）

- 左侧播放卡片：封面、标题、歌手、专辑、可拖动进度条、当前/总时长、收藏。
- 中间控制区：模式按钮、上一首、播放/暂停、下一首、当前模式文字。
- 右侧列表：序号、标题、歌手、专辑、时长、行内收藏爱心、当前曲紫色高亮。
- 播放设置弹窗：四种模式单选、模式说明、只看收藏、完成按钮。
- 收藏筛选：右上角“全部/收藏”切换，计数显示“共 N 首 · 收藏 M 首”。
- 收藏通过 `SharedPreferences` 持久化，只保存 MediaStore id。
- 短屏适配：1920×720 下将当前时间、SeekBar、总时长放在同一行，并压缩卡片内边距与封面尺寸，80/88dp 控制按钮仍完整显示。

## 目录结构

```text
app/src/main/java/com/example/automediaplayer/
├── adapter/MusicAdapter.kt          列表展示、当前曲高亮、行内收藏
├── manager/FavoriteManager.kt       收藏 id 的 SharedPreferences 持久化
├── manager/PlaylistManager.kt       队列、索引、四种切歌算法
├── manager/StateManager.kt          播放状态读写、24 小时有效期判断
├── model/MusicData.kt               音乐元数据 + albumId
├── model/PlayMode.kt                四种播放模式与循环顺序
├── model/MyPlaybackState.kt         可恢复播放状态的最小数据模型
├── service/MusicPlayService.kt      MediaBrowserService / MediaPlayer / MediaSession / 通知
├── ui/MainActivity.kt               MediaBrowser + MediaController 客户端、权限、UI、收藏
└── util/MusicScanner.kt             MediaStore 扫描（Dispatchers.IO）
```

单元测试：`app/src/test/java/com/example/automediaplayer/manager/StateManagerTest.kt`。

布局：`activity_main.xml`、`item_music.xml`、`dialog_play_settings.xml`；
紫藤色色板：`res/values/colors.xml`。

## 构建与运行

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

安装到真机车机（CP5 验收必须真机，`-s` 后的序列号以 `adb devices` 为准）：

```powershell
adb -s 设备序列号 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s 设备序列号 shell pm grant com.example.automediaplayer android.permission.READ_MEDIA_AUDIO
adb -s 设备序列号 shell pm grant com.example.automediaplayer android.permission.POST_NOTIFICATIONS
adb -s 设备序列号 shell am start -n com.example.automediaplayer/.ui.MainActivity
```

媒体键验证：

```powershell
adb -s 设备序列号 shell input keyevent 85   # 播放/暂停
adb -s 设备序列号 shell input keyevent 87   # 下一首
adb -s 设备序列号 shell input keyevent 88   # 上一首
adb -s 设备序列号 shell cmd media_session dispatch next
adb -s 设备序列号 shell dumpsys media_session
```

仓库 `testdata/` 提供 3 首测试曲（不打包进 APK，测试前推到车机 `/sdcard/Music/` 或 U 盘）：

- `周传雄 - 男人·海洋.mp3`（04:02）
- `雨一直下_华语群星.mp3`（04:54，元数据：雨一直下 / 张宇 / 大人的情歌）
- `贺一航 - 一个人的寂寞两个人的错 [mqms2].mp3`（03:32）

推送到车机：

```powershell
adb -s 设备序列号 shell mkdir -p /sdcard/Music
adb -s 设备序列号 push "testdata\周传雄 - 男人·海洋.mp3" /sdcard/Music/
adb -s 设备序列号 push "testdata\雨一直下_华语群星.mp3" /sdcard/Music/
adb -s 设备序列号 push "testdata\贺一航 - 一个人的寂寞两个人的错 [mqms2].mp3" /sdcard/Music/
```
## 演示路径

1. 启动应用，授权音频读取与通知，确认 MediaBrowser 返回 3 首歌曲。
2. 点击歌曲，确认标题、进度条、暂停图标和当前曲高亮。
3. 执行 adb 媒体键 85/87/88，确认暂停、恢复、下一首、上一首。
4. 按 Home，下拉通知栏，确认媒体卡片仍在并可通过同一会话控制。
5. 执行 `dumpsys media_session`，展示 `Media button session`、`active=true`、状态与元数据。
6. 打开右上角设置切换四种模式，验证自定义命令与 `PlaylistManager` 同步。
7. 点击列表爱心，验证收藏筛选与持久化。
8. 播放或暂停后等待至少 5 秒，查看 `AutoMediaPlayerPrefs.xml` 中的歌曲、进度、模式和时间戳。
9. 执行 `adb shell am force-stop com.example.automediaplayer`，重新启动，确认歌曲、进度、模式和播放/暂停态恢复。
10. 打开 `docs/UI_Design_Sketch.png`，说明 60/40 分栏、80/88dp 按钮、92dp 列表行和 4.5:1 对比度依据。
11. 打开“座舱场景演示”，依次模拟导航降音 30%、GAIN 恢复、来电暂停、GAIN 续播、永久丢失停止。
12. 真实 U 盘演示插入重扫与播放中拔出清列表；无 U 盘时使用“模拟插入/拔出”，并说明是模拟。
13. 点击“在副驾屏打开控制器”，展示主屏切歌/暂停/模式变化实时同步到副驾屏。
14. 执行 `adb shell dumpsys media_session`，确认主屏与副驾屏共用同一个 active 会话。
15. 打开 `docs/week5_component.png` 与 `docs/week5_focus_sequence.png` 说明组件关系和焦点时序。

## 验收与架构材料

- `docs/CP5_Acceptance.md`：CP5 四项验收清单、焦点四态、USB 流程、双屏记录与抽问准备
- `docs/week5_component.png`：第 5 周组件图（主屏、副驾屏、MusicPlayService、MediaSession、焦点、USB）
- `docs/week5_focus_sequence.png`：音频焦点四态与主/副驾屏同步时序图
- `tools/generate_week5_diagrams.py`：第 5 周架构图生成脚本
- `docs/CP5_RealDevice_Test_Guide.md`：真机车机安装、焦点、USB、双屏与硬按键测试取证步骤
- `app/libs/hardkey.aar`：厂商方向盘硬按键库（T5-3）
- `docs/CP4_Acceptance.md`：CP4 四项验收清单、车机 UI 规范、强杀恢复实测与抽问准备
- `docs/State_Persistence_Checklist.md`：CP4-④ 五项勾选结果、原始 SharedPreferences 与 MediaSession 快照
- `docs/UI_Design_Sketch.png`：带尺寸、字号、触控与对比度标注的 UI 设计草图
- `tools/generate_week4_ui_sketch.py`：设计草图生成脚本
- `docs/week4_ui_final.png`、`docs/cp4_settings_fixed.png`：第 4 周车机界面与播放设置截图
- `docs/button_fixed_clean_1920x720.png`：1920×720 短屏适配后的完整按钮截图
- `docs/cp4_before_kill.png`、`docs/cp4_after_restore.png`、`docs/cp4_restored_paused.png`：强杀恢复证据
- `docs/cp4_expired.png`、`docs/cp4_empty_state.png`：过期与空状态证据
- `docs/CP3_Acceptance.md`：CP3 验收清单、MediaSession 三角色、adb 实测记录与抽问准备
- `docs/week3_component.png`：第 3 周组件图（MediaSession / MainActivity / MusicPlayService）
- `docs/week3_sequence.png`：第 3 周浏览、播放与控制时序图
- `tools/generate_week3_diagrams.py`：第 3 周架构图生成脚本
- `docs/cp3_logcat.txt`、`docs/cp3_dispatch_log.txt`、`docs/cp3_media_session.txt`：MediaBrowser 与媒体会话实测证据
- `docs/week3_ui_playing.png`、`docs/week3_notification.png`：第 3 周界面与通知截图
- `docs/CP2_Acceptance.md`：CP2 三项验收清单、测试记录、模式差异表、抽问准备
- `docs/week2_component.png`：第 2 周组件图
- `docs/week2_sequence.png`：第 2 周播放时序图
- `tools/generate_week2_diagrams.py`：架构图生成脚本
- `docs/week2_ui_playing.png`、`docs/week2_ui_settings.png`、
  `docs/week2_ui_notification.png`、`docs/week2_ui_home_background.png`：演示截图
- `MVP_Requirements.txt`、`docs/project_structure.png`、`docs/CP1_Acceptance.md`：第 1 周交付物

## 已知限制与后续

- `force-stop` 后最多可能丢失约 5 秒进度，这是 1 秒防抖 + 5 秒保底策略的预期代价。
- 只恢复当前歌曲、列表位置、进度和模式，不保存完整播放历史、封面缓存与系统媒体中心队列。
- 跨设备拷贝文件后 MediaStore id 可能变化，此时按列表位置兜底。
- 已接入厂商 `hardkey.aar`；非 `com.raite.car.hardkey` 设备会自动跳过，T5-3 硬按键需在真机车机验证。
- `MediaSession` 尚未调用 `setQueue()`，系统媒体中心的队列视图仍为空。
- 无真实车机台架时，焦点与 USB 场景可用应用内模拟命令讲解；模拟事件不等于系统真实回调，CP5 验收必须真机。
- `PassengerActivity` 按教材设为 `exported=false`，只能从应用内入口启动，不能直接用 shell `am start`。
- 仓库 `testdata/` 的 3 首测试曲用于演示多曲切歌；更多曲目可直接追加到 `/sdcard/Music/` 后重启应用重新扫描。
