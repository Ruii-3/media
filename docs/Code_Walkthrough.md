# AutoMediaPlayer 代码结构与实现讲解

本文用于课程验收前的复习。核心记忆点只有一句：

> `MainActivity` 是 MediaBrowser/MediaController 客户端，`MusicPlayService` 是
> MediaBrowserService 服务端；`MediaPlayer` 负责真正出声，`MediaSession` 负责把状态广播给所有控制端。

## 一、整体分层

| 层 | 文件 | 职责 |
| --- | --- | --- |
| UI | `ui/MainActivity.kt` | 连接媒体浏览器、申请权限、展示列表与播放状态、收藏筛选、进度条交互 |
| 列表 | `adapter/MusicAdapter.kt` | ViewHolder 复用、当前曲高亮、行内收藏按钮 |
| 服务 | `service/MusicPlayService.kt` | 媒体树、MediaPlayer、MediaSession、前台通知、播放控制 |
| 队列 | `manager/PlaylistManager.kt` | 原始列表、当前队列、索引、四种切歌算法 |
| 收藏 | `manager/FavoriteManager.kt` | SharedPreferences 持久化收藏 id |
| 模型 | `model/MusicData.kt`、`model/PlayMode.kt` | 歌曲元数据、四种播放模式 |
| 工具 | `util/MusicScanner.kt`、`util/AlbumArtLoader.kt` | MediaStore 扫描、专辑封面异步加载 |

依赖方向是单向的：UI -> 标准媒体接口 -> Service -> Manager/Model/Util。
`PlaylistManager` 不持有 `MediaPlayer`，`MusicAdapter` 不直接读写 SharedPreferences，
这样职责比较清晰，答辩时也容易说明“为什么这样分层”。

## 二、应用启动与媒体树加载

`MainActivity.onCreate()` 的顺序是：

1. ViewBinding 加载布局；
2. 初始化 `FavoriteManager` 和列表 Adapter；
3. `setupMediaBrowser()` 创建 `MediaBrowser` 并 `connect()`；
4. `checkPermissionAndScan()` 检查音频读取权限。

连接成功后的完整链路：

```text
MediaBrowser.connect()
  -> MusicPlayService.onGetRoot()          返回 BrowserRoot("root_id")
  -> MediaBrowser 获得 sessionToken
  -> MainActivity 用 token 创建 MediaController
  -> browser.subscribe("root_id", callback)
  -> MusicPlayService.onLoadChildren()     返回 MediaItem 列表
  -> SubscriptionCallback.onChildrenLoaded()
  -> MediaItem 映射回 MusicData
  -> MusicAdapter 展示
```

`onLoadChildren()` 有一个必须掌握的细节：它不能在方法里挂起等待扫描完成，必须在返回前调用
`sendResult()`。所以当前实现是：

- 已扫描完成：直接 `sendResult(buildMediaItems())`；
- 还没扫描完成：先 `sendResult(emptyList())`，同时启动异步扫描；
- 扫描完成后调用 `notifyChildrenChanged(MEDIA_ROOT_ID, Bundle())`，已订阅客户端会再次收到
  `onChildrenLoaded()`，列表随即刷新。

这也是为什么首次进入时可能短暂看到“空列表/扫描中”，随后歌曲出现。

## 三、权限模型

Manifest 中声明了三类权限：

- Android 13+：`READ_MEDIA_AUDIO`；
- Android 12 及以下：`READ_EXTERNAL_STORAGE`，并用 `maxSdkVersion="32"` 限制生效版本；
- Android 13+ 通知：`POST_NOTIFICATIONS`。

另外还有前台服务权限：

- `FOREGROUND_SERVICE`；
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK`；
- Service 声明 `android:foregroundServiceType="mediaPlayback"`。

运行时流程：

```text
checkPermissionAndScan()
  -> 无权限：requestPermissions(READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE)
  -> onRequestPermissionsResult() 授权成功
  -> requestPlaylistReload()
  -> MediaController.sendCommand(RELOAD_PLAYLIST)
  -> MusicPlayService.reloadPlaylist()
  -> 重新扫描并 notifyChildrenChanged()
```

这里有一个容易漏掉的点：Service 在 `onCreate()` 时就会先扫描一次，但那时用户可能还没授权，
扫描结果可能为空。因此授权成功后必须通过 `RELOAD_PLAYLIST` 再扫一次，权限闭环才完整。

如果 `MediaController` 还没连接好，`requestPlaylistReload()` 会把
`pendingReloadAfterPermission` 置为 true，等 `onConnected()` 后再补发命令。

## 四、U 盘与本地歌曲扫描

`MusicScanner.scanMusicFiles()` 使用：

```text
MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
```

不是只查 `EXTERNAL_CONTENT_URI`，所以能覆盖内置存储和挂载的 USB 外部卷。

查询条件：

- `IS_MUSIC != 0`：排除大部分非音乐音频；
- `DURATION >= 45000`：过滤铃声、提示音等短音频；
- 按标题 `COLLATE NOCASE ASC` 排序；
- 扫描在 `Dispatchers.IO` 执行，避免主线程查媒体库造成卡顿或 ANR。

每条记录封装为 `MusicData`，播放地址使用：

```text
ContentUris.withAppendedId(collection, id)
```

即 `content://` Uri，不依赖 `DATA` 绝对路径，符合分区存储要求。

## 五、点击歌曲到真正出声

点击列表项：

```text
MusicAdapter.onItemClick
  -> MediaController.transportControls.playFromMediaId(id, null)
  -> MediaSession.Callback.onPlayFromMediaId()
  -> MusicPlayService.playFromMediaId()
  -> PlaylistManager 找到索引
  -> playMusicAt(position)
```

`playMusicAt()` 是播放核心：

```text
pendingMusicId = 当前歌曲 id
isPrepared = false
MediaPlayer.reset()
setDataSource(applicationContext, contentUri)
updateSessionMetadata()      先让界面显示新歌信息
syncPlaybackState()          先发布 PAUSED/position=0
ensureForeground()           启动前台服务与通知
prepareAsync()               异步准备
  -> onPrepared()
  -> start()
  -> syncPlaybackState()     发布 PLAYING
  -> startProgressUpdates()  每 500ms 更新进度
```

使用 `prepareAsync()` 而不是 `prepare()`，是为了不阻塞主线程；网络或 U 盘较慢时界面仍可响应。

### MediaPlayer 状态机

```text
Idle
  -> setDataSource()          Initialized
  -> prepareAsync()           Preparing
  -> onPrepared()             Prepared
  -> start()                  Started
  -> pause()                  Paused
  -> start()                  Started
  -> 自然播完                  PlaybackCompleted
  -> reset()                  回到 Idle
```

应用层又维护了两个辅助状态：

- `isPrepared`：当前曲是否已经 prepare 完成，决定 `play()/pause()/seekTo()` 能不能直接操作；
- `pendingMusicId`：记录“这一次准备的是哪首歌”，防止快速连续点歌时旧的 `onPrepared()` 回调
  把上一首启动。

`onPrepared()` 里会检查：

```text
prepared === mediaPlayer && pendingMusicId == getCurrentMusic()?.id
```

这是老师很容易追问的并发保护点。

## 六、播放队列与四种模式

`PlaylistManager` 只做三件事：保存列表、维护当前索引、计算下一首/上一首。
它不调用 MediaPlayer，也不更新 UI。

四个关键成员：

- `originalPlaylist`：扫描得到的完整歌单；
- `currentPlaylist`：当前播放顺序，随机模式下是洗牌后的列表；
- `currentPosition`：当前索引；
- `playedIndices`：随机模式的历史栈。

| 模式 | 自然播完 | 手动下一首 | 上一首 |
| --- | --- | --- | --- |
| `ORDER` | 下一首；末尾停止 | 下一首；末尾停止 | 最多退到第 0 首 |
| `LOOP_ALL` | 下一首；末尾回到第 0 首 | 同左 | 第 0 首再上一首回到末尾 |
| `LOOP_ONE` | 原地 `seekTo(0)` 重播 | 仍按列表换歌 | 按列表上一首 |
| `SHUFFLE` | 从未播集合随机选 | 同左 | 弹历史栈回退 |

`moveToNext(manual)` 的 `manual` 参数就是为单曲循环准备的：自然播完时 `manual=false`，
原地重播；用户点“下一首”时 `manual=true`，仍然换歌。

随机模式的“上一首”不是重新随机，而是从 `playedIndices` 栈顶弹出，保证能沿原路径回退。

顺序模式最后一首自然播完后，`onTrackCompleted()` 调用 `pauseAtEnd()`：`seekTo(0)` 后暂停，
不会自动退出应用。

## 七、MediaSession 三角色数据流

### 1. 服务提供者

`MusicPlayService` 继承 `MediaBrowserService`：

- `onGetRoot()` 返回根节点 `root_id`；
- `onLoadChildren()` 返回可播放的 `MediaItem`；
- 服务端创建 `MediaSession`，并把自己的 `sessionToken` 暴露给客户端。

### 2. 会话状态中心

`MediaSession` 是唯一的状态事实源：

- `PlaybackState`：`PLAYING/PAUSED/STATE_NONE/STATE_ERROR`、position、speed、可用 actions；
- `MediaMetadata`：mediaId、标题、歌手、专辑、时长；
- `MediaSession.Callback`：接收 `onPlay/onPause/onSkipToNext/onSkipToPrevious/onSeekTo/onPlayFromMediaId`；
- 自定义命令：`SET_PLAY_MODE`、`RELOAD_PLAYLIST`；
- `setFlags()` 声明处理媒体键与传输控制，`isActive = true` 让系统把媒体键路由过来。

### 3. 控制消费者

- `MediaBrowser`：内容通道，负责拿歌单；
- `MediaController`：控制通道，负责播放、暂停、切歌、seek、发自定义命令；
- 系统媒体中心、蓝牙、方向盘/方控、adb 媒体键、副驾屏：都消费同一个会话。

数据流可以记成：

```text
浏览器拿内容：Browser -> onGetRoot/onLoadChildren -> MediaItem
控制端发命令：Controller/系统 -> MediaSession.Callback -> Service -> MediaPlayer
状态回传：    Service -> setPlaybackState/setMetadata -> 所有 Controller 刷新
```

播放模式切换不是标准 transport 方法，所以走自定义命令：

```text
MainActivity.requestPlayMode()
  -> MediaController.sendCommand(SET_PLAY_MODE, extras)
  -> MediaSession.Callback.onCommand()
  -> MusicPlayService.setPlayMode()
  -> PlaylistManager.setPlayMode()
  -> MediaSession.sendSessionEvent(PLAY_MODE_CHANGED)
  -> MainActivity.onSessionEvent() 更新图标和文字
```

模式还会写进 `PlaybackState.extras`，这样新连接的客户端也能从状态里恢复当前模式。

## 八、后台播放与通知栏

后台播放靠前台服务：

```text
ensureForeground()
  -> startService()                 让服务进入 started 状态
  -> startForeground(..., FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
```

`onStartCommand()` 返回 `START_STICKY`。Activity 进入后台或解绑后，服务仍在运行，MediaPlayer
继续播放。

通知使用 `Notification.MediaStyle`，并绑定 `mediaSession.sessionToken`，内容是标题、歌手、
专辑以及上一首/播放暂停/下一首。

控制入口要区分清楚，答辩时不要混为一谈：

- **系统媒体面板、蓝牙/方控、adb 媒体键**：进入 `MediaSession.Callback`；
- **自绘通知的三个 Action 按钮**：`PendingIntent.getService()` -> `onStartCommand()`；
- 两条路径最终都调用 `play()/pause()/playNext()/playPrevious()`，再统一由 MediaSession 广播状态。

## 九、界面、进度条与收藏

界面是紫藤色 + 白色主题，主要分三块：

- 左侧播放卡片：封面、标题、歌手、专辑、收藏、进度条、当前/总时长；
- 中间控制区：模式、上一首、播放/暂停、下一首；
- 右侧列表：序号、标题、歌手、专辑、时长、行内爱心、当前曲高亮。

进度条有两端配合：

- 服务端每 500ms 调 `updateSessionState()` 发布 position；
- 客户端每 500ms 调 `updateProgressUi()`，播放中用
  `position + (elapsedRealtime - lastPositionUpdateTime) * speed` 推算实时位置；
- 用户拖动时 `isTrackingTouch = true`，暂停自动刷新，松手后 `transportControls.seekTo()`。

收藏：

- `FavoriteManager` 用 SharedPreferences 的 StringSet 保存歌曲 id；
- 不复制歌曲文件，只保存 id，重启后按 id 重新匹配；
- Adapter 的收藏状态由 Activity 传入，保持单一数据源；
- “只看收藏”只过滤 UI 列表，不改变服务端播放队列。

## 十、线程模型

| 工作 | 线程 |
| --- | --- |
| Activity、Service 生命周期、MediaPlayer 回调 | 主线程 |
| MediaStore 扫描 | `Dispatchers.IO`，完成后切回 Main |
| 播放进度 | Main Handler，每 500ms |
| 专辑封面 | 固定线程池，加载后 `ImageView.post()` |

## 十一、老师抽查 Q&A

### Q1：为什么用 MediaBrowserService，不用普通 Service？

普通 Service 只能靠自定义 Binder 给自己应用用。MediaBrowserService 提供标准媒体协议：
内容可被 MediaBrowser 浏览，控制可被 MediaController/系统媒体中心消费，状态由 MediaSession
统一广播，天然支持多客户端和多屏。

### Q2：为什么 `onLoadChildren()` 先返回空列表？

框架要求该方法返回前必须 `sendResult()`，不能阻塞等待扫描。先返回空树，扫描完成后
`notifyChildrenChanged()` 再推送，客户端会再次收到 `onChildrenLoaded()`。

### Q3：权限流程怎么闭环？

Manifest 声明 -> 运行时申请 -> 授权后通过 `RELOAD_PLAYLIST` 命令让服务重新扫描。
Service 启动时可能还没权限，所以不能只依赖 `onCreate()` 的那次扫描。

### Q4：为什么能扫到 U 盘？

使用 `VOLUME_EXTERNAL` 查询所有外部卷，而不是只查内置存储；播放用 `content://` Uri，
不依赖绝对路径。

### Q5：MediaPlayer 有哪些状态？

Idle -> Initialized -> Preparing -> Prepared -> Started -> Paused/PlaybackCompleted。
`reset()` 回 Idle，`setDataSource()` 到 Initialized，`prepareAsync()` 到 Preparing，
`onPrepared()` 到 Prepared，`start()` 到 Started。

### Q6：为什么用 `prepareAsync()`？

准备媒体可能涉及磁盘/U 盘 IO，同步 `prepare()` 会阻塞主线程。异步准备完成后在
`onPrepared()` 里 `start()`。

### Q7：快速连续点歌为什么不会播错？

`pendingMusicId` 记录最新目标歌曲，`onPrepared()` 同时校验播放器实例和当前歌曲 id；
旧回调发现 id 不匹配就直接返回。

### Q8：四种模式的区别？

顺序：末尾停止；列表循环：末尾回第一首；单曲循环：自然播完重播、手动下一首仍换歌；
随机：从未播集合选，上一首按历史栈回退。

### Q9：Home 退出后为什么还在播？

播放时 `startService()` + `startForeground()`，服务是 started 状态，不依赖 Activity 的绑定；
`START_STICKY` 也提高了被系统回收后的恢复概率。

### Q10：通知栏按钮怎么控制播放？

系统媒体面板通过 `sessionToken` 把命令送回 `MediaSession.Callback`；自绘通知按钮通过
`PendingIntent.getService()` 进入 `onStartCommand()`。两条路径最终复用同一组播放方法。

### Q11：adb 媒体键为什么有效？

MediaSession 已激活并发布了 `PLAYING` 状态，系统把它当作当前媒体会话，把
`KEYCODE_MEDIA_PLAY_PAUSE/NEXT/PREVIOUS` 路由到对应回调。可用
`adb shell dumpsys media_session` 查看 active session。

### Q12：收藏保存在哪里？

SharedPreferences 的 StringSet，只存 MediaStore 歌曲 id；不复制音频文件。

### Q13：多屏协同目前做到哪一步？

做到“多个客户端消费同一个 MediaSession”的基础：主界面、通知、系统媒体中心、方控/adb
看到同一份状态。副驾屏独立 UI、独立队列还未实现。

## 十二、已知限制（主动说明）

- 播放模式、队列、进度没有持久化，进程被杀后恢复默认；
- 没有完整处理音频焦点、来电/导航压低音量、耳机拔出暂停；
- 没有监听 USB 热插拔，插 U 盘后通常要重新扫描或重启应用；
- 没有调用 `MediaSession.setQueue()`，系统队列视图为空；
- `onGetRoot()` 当前允许所有调用方，量产应做包名/Uid 白名单；
- 使用原生 MediaPlayer，不是 Media3/ExoPlayer；
- 随机模式的“上一首”在历史栈为空时是随机选曲，不保证严格逆序。

## 十三、现场讲解顺序

1. 先指分层：UI 客户端、Service 服务端、Manager 队列、Util 扫描；
2. 演示授权 -> 列表出现，说明 MediaStore 与 `RELOAD_PLAYLIST`；
3. 点歌，说明 `playFromMediaId -> playMusicAt -> prepareAsync -> onPrepared -> start`；
4. 按 Home，说明前台服务与 MediaStyle 通知；
5. 用通知/系统媒体面板/adb 媒体键控制，说明 MediaSession 状态回传；
6. 切四种模式，说明 `PlaylistManager` 的索引算法差异；
7. 最后主动说已知限制，避免被追问时被动。
