# CP4 验收记录（第 4 周：车机 UI/UX 优化与状态持久化）

- 日期：2026-11-14
- 工程：`AutoMediaPlayer`（`D:\ASdemo\AutoMediaPlayer`）
- 包名：`com.example.automediaplayer`
- 版本：`versionCode=4`、`versionName=4.0`
- 验收环境：Android Automotive 模拟器 `emulator-5554/5556`，Android 15（API 35），1408×792 与 1920×720，横屏
- 构建环境：Gradle 9.5.0、AGP 9.3.0、JDK 17
- 测试素材（3 首）：`男人海洋`、`雨一直下`、`一个人的寂寞两个人的错`，位于车机 `User 10` 的 `/data/media/10/Music`

> 结论：CP4 的 ①车机 UI 版 APK、②带尺寸与规范依据的 `UI_Design_Sketch`、
> ③强杀恢复现场演示材料、④状态持久化验证清单均已完成。
> 实测已验证“歌曲 + 进度 + 模式 + 播放/暂停态”在 `force-stop` 后恢复一致。

## 一、交付物清单

| 交付物 | 位置 | 状态 / 说明 |
| --- | --- | --- |
| 车机 UI 版 APK | `app/build/outputs/apk/debug/app-debug.apk` | 已构建，12,411,235 字节，2026-11-14 15:23:01 |
| APK SHA-256 | 同上 | `40E0FE7D9AD729A616F65877340480589198D8A48DE8CC556C38ED4C00CA1ED6` |
| UI 设计草图 | `docs/UI_Design_Sketch.png` | 标注 1408×792、72dp 顶部栏、60/40 分栏、80/88dp 主按钮、92dp 列表行、触摸与对比度规范 |
| 设计草图生成脚本 | `tools/generate_week4_ui_sketch.py` | 可重复生成 `UI_Design_Sketch.png` |
| 车机主界面截图 | `docs/week4_ui_final.png` | 紫藤白主题、播放卡片、进度条、控制区、收藏列表 |
| 短屏适配截图 | `docs/button_fixed_clean_1920x720.png` | 1920×720 下四个控制按钮完整显示 |
| 播放设置截图 | `docs/cp4_settings_fixed.png` | 四种模式横向单选、只看收藏、完成按钮完整可见 |
| 强杀前证据 | `docs/cp4_before_kill.png` | 播放中状态截图 |
| 强杀恢复证据 | `docs/cp4_restored_paused.png` | 恢复为“男人海洋 / 03:20 / 单曲循环 / 暂停” |
| 过期状态证据 | `docs/cp4_expired.png` | 25 小时前状态不恢复，界面回到“未知曲目” |
| 空状态证据 | `docs/cp4_empty_state.png` | 删除持久化文件后可正常启动，不崩溃、不自动播放 |
| 状态持久化验证清单 | `docs/State_Persistence_Checklist.md` | 五项必测项全部勾选并附实测值 |
| 持久化单元测试 | `app/src/test/java/com/example/automediaplayer/manager/StateManagerTest.kt` | 4 个用例覆盖有效期与非法歌曲 ID |

## 二、CP4 验收清单对照

| 编号 | 验收项 | 完成内容 | 结果 |
| --- | --- | --- | --- |
| CP4-① | 车机 UI 版 APK | 左右 60/40 分栏、紫藤白主题、进度条、收藏、四模式设置、92dp 列表行、48–88dp 触控目标 | 通过 |
| CP4-② | UI_Design_Sketch | `docs/UI_Design_Sketch.png` 标注尺寸、字号、对比度、触摸目标和教材/WCAG 依据 | 通过 |
| CP4-③ | 强杀恢复全流程 | `force-stop` 后重启，恢复歌曲、200703ms 进度、`LOOP_ONE` 模式与暂停态 | 通过 |
| CP4-④ | 状态持久化验证清单 | 五项全部勾选，附 SharedPreferences、MediaSession 与截图证据 | 通过 |

## 三、车机 UI/UX 优化

### 1. 结构与尺寸

| 项目 | 实现值 | 规范依据 |
| --- | --- | --- |
| 基准屏幕 | 1408×792 px，横屏，160 dpi | Automotive 模拟器主屏 |
| 顶部品牌栏 | 72dp 固定高度 | 车机顶部入口固定，降低寻找成本 |
| 页面安全边距 | 20dp；模块间距 ≥20dp | 教材第五章车机 HMI 间距规范 |
| 主分栏 | 左播放区 60% / 右列表区 40% | 驾驶位优先看播放控制，副驾可浏览列表 |
| 主控制按钮 | 普通 80dp，播放键 88dp | 教材推荐触摸目标 80dp×80dp |
| 封面与进度区 | 封面 88dp；当前时间、SeekBar、总时长同一行 | 为 1920×720 短屏节省一行垂直空间 |
| 歌曲列表行 | 最小高度 92dp | 远距离可读、行驶中可点 |
| 收藏按钮 | 详情 48dp，行内 56dp | 高于最小 40dp，低于主控制按钮以避免误触 |
| 进度条 | 8dp 轨道，32dp 触控热区 | 视觉轻量，热区仍可拖动 |

### 2. 1920×720 短屏适配

原布局在 1408×792 可完整显示，但在 1920×720（density 240）上可用高度只有约 316dp，
四行内容叠加后导致控制按钮底部被卡片裁切。现已做如下调整，主按钮仍保持 80dp / 88dp：

- 当前时间、SeekBar、总时长合并到同一行，去掉独立时间行；
- 播放卡片内边距由 16dp 压缩到 12dp；
- 封面由 96dp 调整为 88dp；
- 信息区间距由 6dp 调整为 4dp。

1920×720 实测截图：`docs/button_fixed_clean_1920x720.png`，四个按钮圆形边界完整显示。

### 3. 字号与对比度

| 文本 | 字号 | 颜色 | 对白底对比度 |
| --- | --- | --- | --- |
| 歌曲标题 | 32sp Bold | `#2C2140` | 15.01:1 |
| 页面标题 / 列表标题 | 24sp Bold | `#2C2140` | 15.01:1 |
| 列表歌名 | 22sp Bold | `#2C2140` | 15.01:1 |
| 歌手 / 正文 | 18sp | `#5B4C73` | 7.70:1 |
| 专辑 / 辅助信息 | 16sp | `#6E6184` | 5.67:1 |
| 模式文字 | 16sp | `#613F91` | 7.99:1 |

所有正文与辅助文字均超过 WCAG 2.1 AA 的 4.5:1 要求；界面最小字号为 14sp，
关键信息显著高于“最小 12sp”的底线。

### 4. 本轮修正的真实 UI 缺陷

首次实测播放设置弹窗时，四种模式纵向排列导致内容高度超过车机可用区域，
底部的“只看收藏”和“完成”被裁掉。现改为：

- 弹窗最小宽度 720dp，四个模式单选项横向排列；
- 每个选项仍保留 56dp 高度；
- 模式说明、只看收藏、完成按钮全部在 1408×792 主屏内可见；
- 实测截图见 `docs/cp4_settings_fixed.png`。

## 四、状态持久化设计

### 1. 保存的最小状态集合

`MyPlaybackState` 只保存恢复所需字段，不保存封面、完整歌单或 MediaPlayer 对象：

| 字段 | 作用 |
| --- | --- |
| `currentMusicId` | 通过 MediaStore id 反查歌曲，避免依赖列表位置 |
| `currentPosition` | 歌曲 id 不存在时，用原列表位置兜底 |
| `playbackProgress` | 恢复播放进度（毫秒） |
| `playMode` | 恢复顺序 / 列表循环 / 单曲循环 / 随机模式 |
| `isPlaying` | 恢复播放或暂停态 |
| `timestamp` | 判断状态是否超过 24 小时 |

存储位置：`SharedPreferences` 文件 `AutoMediaPlayerPrefs`，写入使用 `apply()` 异步落盘。

### 2. 保存策略

| 触发 | 行为 | 目的 |
| --- | --- | --- |
| 播放、暂停、拖动进度、切模式 | 1 秒防抖保存 | 连续操作只落盘最后一次，避免频繁 IO |
| 播放过程中 | 每 5 秒保底保存 | 即使进程被强杀，也最多丢失约 5 秒进度 |
| `onDestroy()` | 强制保存一次 | 正常销毁时兜底 |
| 服务刚创建 | 不保存 | 防止空歌单覆盖上一次有效状态 |

只有 `playlistLoaded && playbackStarted` 时才允许保存，避免服务创建阶段写入 `-1`。

### 3. 恢复流程

1. `MusicPlayService.onCreate()` 读取 `StateManager.loadValidPlaybackState()`。
2. 状态缺失、歌曲 id 非法或时间戳超过 24 小时，则不恢复；过期时清理旧文件。
3. 等待 `MusicScanner` 扫描完成，歌单进入 `PlaylistManager`。
4. 用户若已手动点歌，则优先响应用户操作，不执行旧状态恢复。
5. 否则按 `currentMusicId` 查找歌曲；找不到时使用 `currentPosition` 兜底。
6. `MediaPlayer.prepareAsync()` 成功后，先 `seekTo(progress)`，再按 `isPlaying` 决定播放或暂停。
7. 恢复完成发送 `COMMAND_PLAYBACK_RESTORED`，客户端可显示“已恢复上次播放”提示。

## 五、强杀恢复实测

### 1. 播放中强杀

实测命令：

```powershell
adb shell am force-stop --user 10 com.example.automediaplayer
adb shell am start --user 10 -n com.example.automediaplayer/.ui.MainActivity
```

| 阶段 | MediaSession / 持久化结果 |
| --- | --- |
| 强杀前 | `男人海洋`，`STATE_PLAYING`，进度约 27 秒，模式 `ORDER` |
| 强杀后重启 | 同一歌曲，`STATE_PLAYING`，进度从保存点继续增长，列表位置与模式一致 |
| 证据 | `docs/cp4_before_kill.png`、`docs/cp4_after_restore.png` |

### 2. 暂停 + 单曲循环强杀（最终验收样本）

保存前状态：

```xml
<int name="playback_progress" value="200703" />   <!-- 03:20 -->
<int name="current_position" value="1" />
<boolean name="is_playing" value="false" />
<long name="current_music_id" value="1000000018" />
<string name="play_mode">LOOP_ONE</string>
```

执行 `force-stop` 后重新启动，`dumpsys media_session` 实测：

```text
state=PlaybackState {state=PAUSED(2), position=200703, speed=0.0, ...}
metadata: size=5, description=男人海洋, 周传雄, 男人.海洋 (台湾版)
```

界面截图 `docs/cp4_restored_paused.png` 显示：

- 歌曲：男人海洋；
- 进度：03:20 / 04:02；
- 模式：单曲循环；
- 按钮：播放图标（暂停态）。

> 现场演示时要说明：`am force-stop` 不会回调 `onDestroy()`，因此真正兜底的是
> “每 5 秒保底保存”。演示时建议播放或暂停后等待至少 5 秒再强杀，以保证最新进度已落盘。

## 六、过期与空状态验证

### 1. 24 小时过期

将 `timestamp` 改为 `1700000000000`（2023 年，已超过 24 小时）后启动：

- 应用没有恢复旧歌曲、旧进度或旧模式；
- 旧状态被清理，SharedPreferences 变为 `<map />`；
- 界面显示“未知曲目 / 未知歌手 / 未知专辑”，不会自动播放；
- 证据：`docs/cp4_expired.png`。

### 2. 无历史状态

删除 `AutoMediaPlayerPrefs.xml` 后启动：

- 进程正常存活，无崩溃；
- 歌单仍能扫描并显示 3 首；
- 不自动播放，等待用户点击；
- 证据：`docs/cp4_empty_state.png`。

## 七、构建与测试

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest --offline --console=plain
```

结果：`BUILD SUCCESSFUL`。

`StateManagerTest` 共 4 个用例：

- 23 小时内状态有效；
- 25 小时前状态过期；
- 缺失时间戳视为过期；
- 歌曲 id 非正数视为无效。

## 八、现场演示脚本（约 2 分钟）

1. 安装并启动 APK，确认 3 首测试曲和紫藤白车机界面。
2. 点击列表中的“男人海洋”，拖动进度条到约 03:00。
3. 打开右上角设置，选择“单曲循环”，点击“完成”。
4. 点击暂停，等待至少 5 秒。
5. 执行 `adb shell cat /data/user/10/com.example.automediaplayer/shared_prefs/AutoMediaPlayerPrefs.xml`，展示歌曲 id、进度、模式、播放态、时间戳。
6. 执行 `adb shell am force-stop --user 10 com.example.automediaplayer`。
7. 重新启动 Activity，展示歌曲、03:00 左右进度、单曲循环和暂停态均恢复。
8. 打开 `UI_Design_Sketch.png`，说明 60/40 分栏、80/88dp 按钮、92dp 列表行和 4.5:1 对比度依据。

## 九、抽问准备

**问：为什么用 SharedPreferences，不用数据库？**

答：这里只保存 6 个标量字段，数据量极小、没有查询需求；SharedPreferences 足够轻量，
读取发生在服务创建阶段，使用 `apply()` 也不会阻塞主线程。完整歌单由 MediaStore 重新扫描得到，
不需要把播放器状态做成数据库。

**问：为什么是 1 秒防抖 + 5 秒保底？**

答：拖动进度条会高频触发保存，1 秒防抖避免频繁 IO；但进程被强杀不会走 `onDestroy()`，
所以再加 5 秒周期保存，把最坏情况下的进度损失限制在约 5 秒。

**问：为什么恢复到 03:20 而不是 00:00？**

答：`playMusicAt()` 的 `onPrepared` 回调里调用 `seekTo(savedProgress)`，
必须在 `prepareAsync()` 完成后 seek 才有效；随后按保存的 `isPlaying` 决定 `play()` 或保持暂停。

**问：如果歌曲被删除或 U 盘拔掉怎么办？**

答：先按 `currentMusicId` 查找；找不到时用保存的列表位置 `currentPosition` 兜底；
两个都不可用时清空媒体树并保持空状态，不会因为旧 id 崩溃。

**问：为什么保存模式、播放态，不保存完整队列？**

答：队列可由 MediaStore 扫描和 `PlaylistManager` 重建；保存完整队列会让状态文件膨胀，
并引入歌曲失效后的同步问题。当前只保存“恢复播放所需的最小状态集合”。

**问：24 小时有效期怎么判断？**

答：`StateManager.isExpired(timestamp, now)` 比较当前时间与保存时间，超过 24 小时返回 true；
服务启动时发现过期会调用 `clearPlaybackState()` 清理，避免几天前的进度突然恢复。

## 十、已知限制

- `force-stop` 后最多可能丢失 5 秒进度，这是周期保存策略的预期代价。
- 仅恢复当前歌曲与队列位置，不恢复完整播放历史、封面缓存或系统媒体中心的队列视图。
- 状态恢复依赖 MediaStore 能重新扫描到同一歌曲 id；跨设备拷贝文件后 id 可能变化，此时按列表位置兜底。
