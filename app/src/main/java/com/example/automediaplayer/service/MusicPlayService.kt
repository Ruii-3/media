package com.example.automediaplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.ResultReceiver
import android.os.SystemClock
import android.service.media.MediaBrowserService
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import com.example.automediaplayer.R
import com.example.automediaplayer.manager.AudioFocusListener
import com.example.automediaplayer.manager.AudioFocusManager
import com.example.automediaplayer.manager.PlaylistManager
import com.example.automediaplayer.manager.StateManager
import com.example.automediaplayer.model.MusicData
import com.example.automediaplayer.model.MyPlaybackState
import com.example.automediaplayer.model.PlayMode
import com.example.automediaplayer.security.MediaBrowserAccessPolicy
import com.example.automediaplayer.ui.MainActivity
import com.example.automediaplayer.ui.USBStatusCallback
import com.example.automediaplayer.ui.USBStorageObserver
import com.example.automediaplayer.util.MusicScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import raite.car.hardkey.HardKeyPolicyManager
import raite.car.hardkey.KeyEventCallback
import raite.car.hardkey.KeyEventExt

/**
 * 第 3 周：MediaBrowserService 版播放服务。
 *
 * 同一套播放器现在通过标准媒体协议对外提供三种能力：
 * 1. 浏览：onGetRoot / onLoadChildren 把歌单以 MediaItem 媒体树暴露给 MediaBrowser；
 * 2. 控制：MediaSession.Callback 接收 MediaController、方控、蓝牙和 adb 媒体键的命令；
 * 3. 状态：MediaSession 的 PlaybackState / MediaMetadata 自动广播给所有客户端。
 *
 * 主界面不再通过自定义 Binder 直接调用本类，而是使用 MediaBrowser（数据）
 * 与 MediaController（控制）两条标准通道。这样主屏、副屏、系统媒体中心和
 * 方向盘媒体键看到的是同一个会话、同一份状态。
 */
class MusicPlayService : MediaBrowserService() {

    companion object {
        private const val TAG = "MusicPlayService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "music_playback_channel"
        const val MEDIA_ROOT_ID = "root_id"

        const val ACTION_TOGGLE = "com.example.automediaplayer.action.TOGGLE"
        const val ACTION_NEXT = "com.example.automediaplayer.action.NEXT"
        const val ACTION_PREVIOUS = "com.example.automediaplayer.action.PREVIOUS"
        const val ACTION_STOP = "com.example.automediaplayer.action.STOP"

        /** 客户端请求切换播放模式，extras 中通过 KEY_NEW_MODE 传入模式名。 */
        const val SET_PLAY_MODE = "SET_PLAY_MODE"
        /** 客户端请求服务重新扫描媒体库。 */
        const val COMMAND_RELOAD_PLAYLIST = "RELOAD_PLAYLIST"
        /** 服务通知客户端：播放模式已经变化。 */
        const val COMMAND_PLAY_MODE_CHANGED = "PLAY_MODE_CHANGED"
        /** 服务通知客户端：媒体树已经变化。 */
        const val COMMAND_PLAYLIST_UPDATED = "PLAYLIST_UPDATED"
        const val KEY_NEW_MODE = "NEW_MODE"
        /** PlaybackState.extras 中携带当前模式，客户端重连后也能恢复。 */
        const val KEY_PLAY_MODE = "PLAY_MODE"
        const val COMMAND_PLAYBACK_RESTORED = "PLAYBACK_RESTORED"
        const val KEY_RESTORED_TITLE = "RESTORED_TITLE"
        const val KEY_RESTORED_PROGRESS = "RESTORED_PROGRESS"

        /** 第 5 周：客户端请求模拟一次音频焦点事件，便于模拟器课堂演示。 */
        const val COMMAND_SIMULATE_AUDIO_FOCUS = "SIMULATE_AUDIO_FOCUS"
        /** 第 5 周：客户端请求模拟 U 盘插入/拔出，不影响系统真实挂载。 */
        const val COMMAND_SIMULATE_USB = "SIMULATE_USB"
        /** 服务通知客户端：音频焦点状态已变化。 */
        const val COMMAND_FOCUS_CHANGED = "FOCUS_CHANGED"
        const val KEY_FOCUS_EVENT = "FOCUS_EVENT"
        const val KEY_USB_CONNECTED = "USB_CONNECTED"
        const val KEY_FOCUS_STATE = "FOCUS_STATE"

        const val FOCUS_NORMAL = "NORMAL"
        const val FOCUS_DUCKED = "DUCKED"
        const val FOCUS_TRANSIENT_PAUSED = "TRANSIENT_PAUSED"
        const val FOCUS_LOST = "LOST"

        const val FOCUS_EVENT_GAIN = "GAIN"
        const val FOCUS_EVENT_LOSS_TRANSIENT = "LOSS_TRANSIENT"
        const val FOCUS_EVENT_CAN_DUCK = "LOSS_TRANSIENT_CAN_DUCK"
        const val FOCUS_EVENT_LOSS = "LOSS"

        const val EXTRA_DURATION = "duration"
        const val EXTRA_FILE_PATH = "filePath"
        const val EXTRA_ALBUM_ID = "albumId"

        private const val PROGRESS_INTERVAL_MS = 500L
        private const val SAVE_DEBOUNCE_MS = 1000L
        private const val AUTO_SAVE_INTERVAL_MS = 5000L
    }

    private val playlistManager = PlaylistManager()
    private val progressHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var mediaPlayer: MediaPlayer? = null
    private var isPrepared = false
    private var playbackStarted = false
    private var isForegroundStarted = false
    private var serviceStartedForPlayback = false
    private var pendingMusicId: Long? = null

    private lateinit var mediaSession: MediaSession
    private lateinit var notificationManager: NotificationManager

    private var playlistLoaded = false
    private var scanInProgress = false
    private var playlistGeneration = 0
    private var pendingRescan = false
    private var pendingPlayMediaId: String? = null
    private lateinit var stateManager: StateManager
    private var pendingRestoreState: MyPlaybackState? = null
    private var restoreAttempted = false
    private var pendingOnPrepared: (() -> Unit)? = null

    private lateinit var audioFocusManager: AudioFocusManager
    private var wasPlayingBeforeFocusLoss = false
    private var currentFocusState = FOCUS_NORMAL
    private var usbStorageObserver: USBStorageObserver? = null
    private var hardKeyPolicyManager: HardKeyPolicyManager? = null

    private val saveHandler = Handler(Looper.getMainLooper())
    private val saveStateRunnable = Runnable { savePlaybackState() }
    private val autoSaveRunnable = object : Runnable {
        override fun run() {
            savePlaybackState()
            saveHandler.postDelayed(this, AUTO_SAVE_INTERVAL_MS)
        }
    }

    private val progressRunnable = object : Runnable {
        override fun run() {
            if (isPrepared && isPlaying()) {
                updateSessionState()
            }
            progressHandler.postDelayed(this, PROGRESS_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: MediaBrowserService")
        stateManager = StateManager(this)
        pendingRestoreState = stateManager.loadValidPlaybackState()
        if (pendingRestoreState == null && stateManager.isStateExpired()) {
            stateManager.clearPlaybackState()
        }
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
        audioFocusManager = AudioFocusManager(this)
        initializeMediaSession()
        initializeMediaPlayer()
        // 先挂 USB 监听再扫描，避免“扫描过程中 U 盘插入”漏掉系统广播。
        initUSBObserver()
        initHardKeyPolicy()
        // 服务一创建就开始扫描；有权限时通常能在首次订阅前完成。
        loadPlaylistAsync(force = false)
        startAutoSave()
    }

    // ============ 第 5 周：音频焦点 ============

    /**
     * 系统音频焦点 → 播放业务语义。
     *
     * GAIN                    导航播报/电话结束，恢复音量；只有丢失前在播放才自动续播。
     * LOSS_TRANSIENT          来电等短暂占用，记住播放态后暂停，焦点仍会回来。
     * LOSS_TRANSIENT_CAN_DUCK 导航播报，不暂停，只把媒体音量降到 30%。
     * LOSS                    其他播放器永久接管，停止播放且不自动恢复。
     */
    private val audioFocusListener = object : AudioFocusListener {
        override fun onAudioFocusGained() {
            Log.i(TAG, "focus GAIN: 恢复音量，wasPlaying=$wasPlayingBeforeFocusLoss")
            val shouldResume = wasPlayingBeforeFocusLoss
            wasPlayingBeforeFocusLoss = false
            mediaPlayer?.setVolume(1.0f, 1.0f)
            setFocusState(FOCUS_NORMAL)
            if (shouldResume) play()
        }

        override fun onAudioFocusLostTransient() {
            Log.i(TAG, "focus LOSS_TRANSIENT: 记录播放态并暂停")
            wasPlayingBeforeFocusLoss = isPlaying()
            if (wasPlayingBeforeFocusLoss) pause()
            setFocusState(FOCUS_TRANSIENT_PAUSED)
        }

        override fun onAudioFocusLostTransientCanDuck() {
            Log.i(TAG, "focus CAN_DUCK: 音量降到 30%，继续播放")
            mediaPlayer?.setVolume(0.3f, 0.3f)
            setFocusState(FOCUS_DUCKED)
        }

        override fun onAudioFocusLostPermanent() {
            Log.i(TAG, "focus LOSS: 永久丢失，停止播放")
            wasPlayingBeforeFocusLoss = false
            stopPlayback()
            setFocusState(FOCUS_LOST)
        }
    }

    /** 已持有焦点直接放行；未持有时现场申请，避免未拿到焦点就抢占扬声器。 */
    private fun requestAudioFocusBeforePlay(): Boolean {
        if (audioFocusManager.hasFocus()) return true
        val granted = audioFocusManager.requestAudioFocus(audioFocusListener)
        if (granted) {
            setFocusState(FOCUS_NORMAL)
        } else {
            Log.e(TAG, "请求音频焦点失败，播放被拒绝")
            setFocusState(FOCUS_LOST)
            Toast.makeText(this, R.string.audio_focus_denied, Toast.LENGTH_SHORT).show()
        }
        return granted
    }

    /** 统一更新焦点状态：写入 PlaybackState.extras，并广播给主屏/副驾屏。 */
    private fun setFocusState(state: String) {
        currentFocusState = state
        if (!::mediaSession.isInitialized) return
        syncPlaybackState()
        val extras = Bundle().apply { putString(KEY_FOCUS_STATE, state) }
        mediaSession.sendSessionEvent(COMMAND_FOCUS_CHANGED, extras)
    }

    /**
     * 课堂模拟：不触碰系统真实 AudioFocus，只复用与真实回调完全相同的业务分支。
     * 文档与界面必须标注“模拟”，不能把模拟日志当成系统焦点真的发生了变化。
     */
    private fun simulateAudioFocus(event: String) {
        Log.i(TAG, "simulateAudioFocus: $event (仅模拟业务分支)")
        when (event) {
            FOCUS_EVENT_GAIN -> audioFocusListener.onAudioFocusGained()
            FOCUS_EVENT_LOSS_TRANSIENT -> audioFocusListener.onAudioFocusLostTransient()
            FOCUS_EVENT_CAN_DUCK -> audioFocusListener.onAudioFocusLostTransientCanDuck()
            FOCUS_EVENT_LOSS -> audioFocusListener.onAudioFocusLostPermanent()
            else -> Log.w(TAG, "unknown focus simulation: $event")
        }
    }

    // ============ 第 5 周：USB 热插拔 ============

    /** 广播监听层只报“连接/断开”，具体清队列还是重扫由服务决定。 */
    private fun initUSBObserver() {
        usbStorageObserver = USBStorageObserver(
            this,
            object : USBStatusCallback {
                override fun onUSBConnected() = handleUsbConnected()
                override fun onUSBDisconnected() = handleUsbDisconnected()
            }
        )
        usbStorageObserver?.start()
    }

    private fun handleUsbConnected() {
        Toast.makeText(this, R.string.usb_connected_rescan, Toast.LENGTH_SHORT).show()
        Log.i(TAG, "USB connected: rescan playlist")
        reloadPlaylist()
    }

    /**
     * 拔出顺序不能乱：先让 MediaPlayer 脱离已失效的数据源，再清队列、清元数据，
     * 最后通知所有客户端刷新。若先清队列再 stop，MediaPlayer 仍可能访问被移除的文件。
     */
    private fun handleUsbDisconnected() {
        Log.i(TAG, "USB disconnected: stop, clear queue, notify clients")
        // 让正在进行的扫描结果失效，避免扫描结束时又把已拔出 U 盘的文件写回列表。
        playlistGeneration++
        pendingRescan = false
        wasPlayingBeforeFocusLoss = false
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.reset() }
        isPrepared = false
        playbackStarted = false
        pendingMusicId = null
        pendingOnPrepared = null
        pendingPlayMediaId = null
        pendingRestoreState = null
        restoreAttempted = true
        playlistLoaded = true
        playlistManager.setPlaylist(emptyList(), 0)
        audioFocusManager.abandonAudioFocus()
        currentFocusState = FOCUS_NORMAL
        stateManager.clearPlaybackState()
        saveHandler.removeCallbacks(saveStateRunnable)
        if (isForegroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForegroundStarted = false
        }
        updateSessionMetadata()
        updateSessionState()
        notifyChildrenChanged(MEDIA_ROOT_ID, Bundle())
        mediaSession.sendSessionEvent(COMMAND_PLAYLIST_UPDATED, Bundle())
        Toast.makeText(this, R.string.usb_disconnected_cleared, Toast.LENGTH_SHORT).show()
    }

    // ============ 第 5 周：厂商硬按键（T5-3） ============

    /**
     * 方向盘/中控硬按键回调。
     *
     * 按键事件最终复用本服务已有的 togglePlayPause/playNext/playPrevious，
     * 与 UI、MediaSession、通知栏共用同一套播放逻辑；因此按键路径同样会先申请音频焦点。
     */
    private val hardKeyEventCallback = object : KeyEventCallback {
        override fun onKeyEvent(keyEvent: KeyEvent?, param1: Int, param2: Int) {
            val keyCode = keyEvent?.keyCode
            Log.i("HardKey", "onKeyEvent key=$keyCode param1=$param1 param2=$param2")
            when (keyCode) {
                KeyEventExt.KEYCODE_MODE,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> togglePlayPause()

                KeyEventExt.KEYCODE_SEEK_UP,
                KeyEvent.KEYCODE_MEDIA_NEXT -> playNext()

                KeyEventExt.KEYCODE_SEEK_DOWN,
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> playPrevious()

                else -> Log.d("HardKey", "未映射的硬按键: $keyCode")
            }
        }

        override fun onKeyLongPress(keyEvent: KeyEvent?, param1: Int, param2: Int) {
            // 预留：长按可扩展为快退、语音唤醒等车型自定义动作。
            Log.i("HardKey", "onKeyLongPress key=${keyEvent?.keyCode} param1=$param1 param2=$param2")
        }
    }

    /**
     * 硬按键库是厂商进程级单例（HardKeyPolicyManager.getInstance）。
     * 非该厂商车机上包/服务不存在，直接跳过，避免模拟器和普通手机反复重连刷日志。
     */
    private fun initHardKeyPolicy() {
        val vendorInstalled = runCatching {
            packageManager.getPackageInfo("com.raite.car.hardkey", 0)
        }.isSuccess
        if (!vendorInstalled) {
            Log.i(TAG, "hardkey vendor package not installed; skip hardkey registration")
            return
        }
        hardKeyPolicyManager = runCatching {
            HardKeyPolicyManager.getInstance(applicationContext)
        }.onFailure {
            Log.w(TAG, "HardKeyPolicyManager.getInstance failed", it)
        }.getOrNull() ?: return

        runCatching {
            hardKeyPolicyManager?.addKeyEventCallBack(
                hardKeyEventCallback,
                HardKeyPolicyManager.SCENE_MEDIA_CENTER_CTRL
            )
        }.onSuccess {
            Log.i(TAG, "hardkey callback registered: scene=SCENE_MEDIA_CENTER_CTRL")
        }.onFailure {
            Log.w(TAG, "hardkey callback registration failed", it)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.d(TAG, "onBind: ${intent.action}")
        // 父类返回 MediaBrowserService 的 BrowserServiceBinder，不能替换为自定义 Binder。
        return super.onBind(intent)
    }

    override fun onGetRoot(
        callingPackage: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        Log.d(TAG, "onGetRoot: pkg=$callingPackage uid=$clientUid")
        // 双层校验：Manifest 的 signature 权限先限制绑定，这里再拒绝伪造包名/UID。
        val trusted = MediaBrowserAccessPolicy.isTrustedClient(
            callingPackage = callingPackage,
            clientUid = clientUid,
            ownerPackage = packageName,
            ownerUid = Process.myUid()
        )
        if (!trusted) {
            Log.w(
                TAG,
                "onGetRoot denied: pkg=$callingPackage uid=$clientUid ownerUid=${Process.myUid()}"
            )
            return null
        }
        return BrowserRoot(MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: MediaBrowserService.Result<List<MediaBrowser.MediaItem>>
    ) {
        Log.d(TAG, "onLoadChildren: $parentId")
        if (parentId != MEDIA_ROOT_ID) {
            result.sendResult(emptyList())
            return
        }
        if (playlistLoaded) {
            result.sendResult(buildMediaItems())
        } else {
            // MediaBrowserService 要求 onLoadChildren 返回前必须 sendResult。
            // 扫描尚未完成时先返回空树，完成后通过 notifyChildrenChanged 再推送。
            result.sendResult(emptyList())
            loadPlaylistAsync(force = false)
        }
    }

    private fun buildMediaItems(): List<MediaBrowser.MediaItem> {
        return playlistManager.getAllMusic().map { music ->
            val extras = Bundle().apply {
                putLong(EXTRA_DURATION, music.duration)
                putString(EXTRA_FILE_PATH, music.filePath)
                putLong(EXTRA_ALBUM_ID, music.albumId)
            }
            val description = MediaDescription.Builder()
                .setMediaId(music.id.toString())
                .setTitle(music.title)
                .setSubtitle(music.artist)
                .setDescription(music.album)
                .setMediaUri(music.uri)
                .setExtras(extras)
                .build()
            MediaBrowser.MediaItem(description, MediaBrowser.MediaItem.FLAG_PLAYABLE)
        }
    }

    /**
     * 异步扫描媒体库。onLoadChildren 不能挂起等待，因此扫描未完成时先返回空树，
     * 完成后通过 notifyChildrenChanged 让已订阅客户端重新收到列表。
     */
    private fun loadPlaylistAsync(force: Boolean) {
        if (scanInProgress) {
            // 扫描中又来了一次 USB 插入/强制重扫：等本轮结束再启动新一轮，
            // 否则新 U 盘的歌曲可能要等到下次启动才会出现。
            if (force) pendingRescan = true
            return
        }
        if (playlistLoaded && !force) return
        scanInProgress = true
        val generation = playlistGeneration
        ioScope.launch {
            val musicList = runCatching {
                MusicScanner.scanMusicFiles(this@MusicPlayService)
            }.onFailure {
                Log.e(TAG, "扫描媒体库失败", it)
            }.getOrElse { emptyList() }

            withContext(Dispatchers.Main) {
                if (generation != playlistGeneration) {
                    // USB 在扫描期间被拔出/换盘：旧扫描结果已经失效，丢弃。
                    scanInProgress = false
                    Log.i(TAG, "discard stale scan result: generation=$generation")
                    if (pendingRescan) {
                        pendingRescan = false
                        loadPlaylistAsync(force = true)
                    }
                    return@withContext
                }
                val currentId = getCurrentMusic()?.id
                playlistManager.setPlaylist(musicList, currentId ?: 0L)
                playlistLoaded = true
                scanInProgress = false

                val items = buildMediaItems()
                // 已订阅的客户端会因 notifyChildrenChanged 重新收到列表。
                notifyChildrenChanged(MEDIA_ROOT_ID, Bundle())
                mediaSession.sendSessionEvent(COMMAND_PLAYLIST_UPDATED, Bundle())
                Log.i(TAG, "Media tree loaded: ${items.size} items")

                if (pendingRescan) {
                    pendingRescan = false
                    loadPlaylistAsync(force = true)
                    return@withContext
                }

                val pendingId = pendingPlayMediaId
                if (pendingId != null) {
                    pendingPlayMediaId = null
                    restoreAttempted = true
                    playFromMediaId(pendingId)
                } else {
                    tryRestorePlaybackState()
                }
            }
        }
    }

    /**
     * 歌单就绪后再恢复，避免扫描尚未完成时找不到歌曲。
     * 用户如果已经手点歌曲，pending 播放优先，不会再被恢复逻辑覆盖。
     */
    private fun tryRestorePlaybackState() {
        if (restoreAttempted || !playlistLoaded) return
        val saved = pendingRestoreState ?: return
        val queue = playlistManager.getPlaylist()
        if (queue.isEmpty()) return

        restoreAttempted = true
        pendingRestoreState = null
        setPlayMode(saved.playMode)

        val byId = queue.indexOfFirst { it.id == saved.currentMusicId }
        val index = if (byId >= 0) {
            byId
        } else {
            saved.currentPosition.coerceIn(0, queue.lastIndex)
        }
        val music = queue.getOrNull(index) ?: return
        Log.i(
            TAG,
            "restore playback: id=${music.id}, index=$index, " +
                "progress=${saved.playbackProgress}, isPlaying=${saved.isPlaying}"
        )

        playMusicAt(
            position = index,
            requestFocus = saved.isPlaying,
            onPrepared = {
                val duration = getDuration()
                val progress = if (duration > 0) {
                    saved.playbackProgress.coerceIn(0, duration)
                } else {
                    saved.playbackProgress.coerceAtLeast(0)
                }
                if (progress > 0) seekTo(progress)
                if (saved.isPlaying) play() else syncPlaybackState()
                val extras = Bundle().apply {
                    putString(KEY_RESTORED_TITLE, music.title)
                    putInt(KEY_RESTORED_PROGRESS, progress)
                }
                mediaSession.sendSessionEvent(COMMAND_PLAYBACK_RESTORED, extras)
            }
        )
    }

    fun reloadPlaylist() {
        playlistGeneration++
        playlistLoaded = false
        loadPlaylistAsync(force = true)
    }

    private fun initializeMediaSession() {
        mediaSession = MediaSession(this, "AutoMediaPlayer").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    Log.d(TAG, "MediaSession.onPlay")
                    play()
                }

                override fun onPause() {
                    Log.d(TAG, "MediaSession.onPause")
                    pause()
                }

                override fun onStop() {
                    Log.d(TAG, "MediaSession.onStop")
                    stopPlayback()
                }

                override fun onSkipToNext() {
                    Log.d(TAG, "MediaSession.onSkipToNext")
                    playNext()
                }

                override fun onSkipToPrevious() {
                    Log.d(TAG, "MediaSession.onSkipToPrevious")
                    playPrevious()
                }

                override fun onSeekTo(pos: Long) {
                    Log.d(TAG, "MediaSession.onSeekTo: $pos")
                    seekTo(pos.toInt())
                }

                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    Log.d(TAG, "MediaSession.onPlayFromMediaId: $mediaId")
                    mediaId?.let { playFromMediaId(it) }
                }

                override fun onCommand(command: String, extras: Bundle?, cb: ResultReceiver?) {
                    super.onCommand(command, extras, cb)
                    when (command) {
                        SET_PLAY_MODE -> {
                            val requested = extras?.getString(KEY_NEW_MODE)
                                ?.let { runCatching { PlayMode.valueOf(it) }.getOrNull() }
                            setPlayMode(requested ?: getPlayMode().nextMode())
                        }
                        COMMAND_RELOAD_PLAYLIST -> reloadPlaylist()
                        COMMAND_SIMULATE_AUDIO_FOCUS -> {
                            val event = extras?.getString(KEY_FOCUS_EVENT)
                            event?.let { simulateAudioFocus(it) }
                        }
                        COMMAND_SIMULATE_USB -> {
                            if (extras?.getBoolean(KEY_USB_CONNECTED, false) == true) {
                                handleUsbConnected()
                            } else {
                                handleUsbDisconnected()
                            }
                        }
                    }
                }
            })
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setSessionActivity(contentPendingIntent())
            isActive = true
        }
        // MediaBrowser 客户端通过这个 token 创建 MediaController。
        sessionToken = mediaSession.sessionToken
        Log.d(TAG, "MediaSession token ready: ${mediaSession.sessionToken != null}")
    }

    private fun initializeMediaPlayer() {
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setOnPreparedListener { prepared ->
                if (prepared !== mediaPlayer || pendingMusicId != getCurrentMusic()?.id) {
                    return@setOnPreparedListener
                }
                isPrepared = true
                playbackStarted = true
                val callback = pendingOnPrepared
                pendingOnPrepared = null
                if (callback != null) {
                    callback()
                } else {
                    prepared.start()
                    ensureForeground()
                    syncPlaybackState()
                    startProgressUpdates()
                }
                scheduleStateSave()
                Log.i(TAG, "Playback started: ${getCurrentMusic()?.title}")
            }
            setOnCompletionListener { onTrackCompleted() }
            setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                isPrepared = false
                notifyPlaybackError("播放错误：$what / $extra")
                true
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> togglePlayPause()
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_STOP -> {
                stopPlayback()
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForegroundStarted = false
                stopSelf()
            }
        }
        return START_STICKY
    }

    // ============ 播放控制 ============

    fun play() {
        if (isPrepared) {
            if (!requestAudioFocusBeforePlay()) return
            mediaPlayer?.start()
            ensureForeground()
            syncPlaybackState()
            startProgressUpdates()
            scheduleStateSave()
        } else {
            val index = playlistManager.getCurrentIndex()
            if (index >= 0) playMusicAt(index)
        }
    }

    fun pause() {
        if (isPrepared && isPlaying()) {
            mediaPlayer?.pause()
            syncPlaybackState()
            scheduleStateSave()
        }
    }

    fun togglePlayPause() {
        if (isPlaying()) pause() else play()
    }

    fun playNext() {
        if (playlistManager.moveToNext(manual = true)) {
            playMusicAt(playlistManager.getCurrentIndex())
        } else {
            pauseAtEnd()
        }
    }

    fun playPrevious() {
        if (playlistManager.moveToPrevious()) {
            playMusicAt(playlistManager.getCurrentIndex())
        }
    }

    fun playFromMediaId(mediaId: String, onPrepared: (() -> Unit)? = null) {
        if (!playlistLoaded) {
            pendingPlayMediaId = mediaId
            loadPlaylistAsync(force = false)
            return
        }
        restoreAttempted = true
        pendingRestoreState = null
        val id = mediaId.toLongOrNull() ?: return
        val index = playlistManager.getPlaylist().indexOfFirst { it.id == id }
        if (index >= 0) {
            playMusicAt(index, onPrepared)
        } else {
            Log.w(TAG, "Media id not found in queue: $mediaId")
        }
    }

    fun playMusicAt(
        position: Int,
        onPrepared: (() -> Unit)? = null,
        requestFocus: Boolean = true
    ) {
        restoreAttempted = true
        pendingRestoreState = null
        if (playlistManager.getPlaylist().getOrNull(position) == null) return
        // 恢复为“暂停态”时只 prepare，不抢占焦点；用户真正点播放时再申请。
        if (requestFocus && !requestAudioFocusBeforePlay()) return
        val music = playlistManager.moveToPosition(position) ?: return
        pendingMusicId = music.id
        pendingOnPrepared = onPrepared
        isPrepared = false
        try {
            mediaPlayer?.reset()
            // CAN_DUCK 期间换歌保持 30% 音量；正常状态下新歌恢复到 100%。
            if (currentFocusState != FOCUS_DUCKED) {
                mediaPlayer?.setVolume(1.0f, 1.0f)
            }
            mediaPlayer?.setDataSource(applicationContext, music.uri)
            // 先同步元数据，让客户端在 prepare 期间就能显示歌曲信息。
            updateSessionMetadata()
            syncPlaybackState()
            ensureForeground()
            mediaPlayer?.prepareAsync()
            scheduleStateSave()
        } catch (e: Exception) {
            Log.e(TAG, "playMusicAt failed: ${music.uri}", e)
            pendingOnPrepared = null
            notifyPlaybackError("播放失败：${e.message}")
        }
    }

    fun seekTo(positionMs: Int) {
        if (!isPrepared) return
        val safePosition = positionMs.coerceIn(0, getDuration())
        mediaPlayer?.seekTo(safePosition)
        syncPlaybackState()
        scheduleStateSave()
    }

    /** 停止但不销毁 MediaPlayer，保留当前曲目，便于再次 play()。 */
    fun stopPlayback() {
        if (isPrepared) mediaPlayer?.pause()
        wasPlayingBeforeFocusLoss = false
        // 显式停止/系统 onStop 才释放焦点；普通 pause() 必须保留，否则收不到 GAIN。
        if (::audioFocusManager.isInitialized) audioFocusManager.abandonAudioFocus()
        setFocusState(FOCUS_NORMAL)
        scheduleStateSave()
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    fun getCurrentMusic(): MusicData? = playlistManager.getCurrentMusic()

    fun getDuration(): Int = runCatching { mediaPlayer?.duration ?: 0 }.getOrDefault(0)

    fun getCurrentPosition(): Int = runCatching { mediaPlayer?.currentPosition ?: 0 }.getOrDefault(0)

    private fun onTrackCompleted() {
        Log.i(TAG, "Track completed: ${getCurrentMusic()?.title}, mode=${getPlayMode()}")
        if (playlistManager.getPlayMode() == PlayMode.LOOP_ONE) {
            runCatching {
                mediaPlayer?.seekTo(0)
                mediaPlayer?.start()
            }
            syncPlaybackState()
            scheduleStateSave()
            return
        }
        if (playlistManager.moveToNext(manual = false)) {
            playMusicAt(playlistManager.getCurrentIndex())
        } else {
            pauseAtEnd()
        }
        scheduleStateSave()
    }

    private fun pauseAtEnd() {
        if (!isPrepared) return
        runCatching { mediaPlayer?.seekTo(0) }
        runCatching { mediaPlayer?.pause() }
        syncPlaybackState()
        scheduleStateSave()
    }

    // ============ 播放模式 ============

    fun setPlayMode(mode: PlayMode) {
        if (mode != getPlayMode()) {
            playlistManager.setPlayMode(mode)
        }
        syncPlaybackState()
        sendPlayModeEvent(mode)
        scheduleStateSave()
    }

    fun getPlayMode(): PlayMode = playlistManager.getPlayMode()

    private fun sendPlayModeEvent(mode: PlayMode) {
        val extras = Bundle().apply { putString(KEY_NEW_MODE, mode.name) }
        mediaSession.sendSessionEvent(COMMAND_PLAY_MODE_CHANGED, extras)
    }

    // ============ MediaSession 状态同步 ============

    private fun syncPlaybackState() {
        updateSessionState()
        updateSessionMetadata()
    }

    private fun updateSessionState() {
        val current = getCurrentMusic()
        val state = when {
            current == null -> PlaybackState.STATE_NONE
            isPlaying() -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }
        val extras = Bundle().apply {
            putString(KEY_PLAY_MODE, getPlayMode().name)
            putString(KEY_FOCUS_STATE, currentFocusState)
        }
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_STOP or
                        PlaybackState.ACTION_SEEK_TO or
                        PlaybackState.ACTION_PLAY_FROM_MEDIA_ID
                )
                .setState(
                    state,
                    getCurrentPosition().toLong(),
                    if (isPlaying()) 1f else 0f,
                    SystemClock.elapsedRealtime()
                )
                .setExtras(extras)
                .build()
        )
        updateNotification()
    }

    private fun updateSessionMetadata() {
        val music = getCurrentMusic()
        if (music == null) {
            mediaSession.setMetadata(null)
            return
        }
        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, music.id.toString())
                .putString(MediaMetadata.METADATA_KEY_TITLE, music.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, music.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, music.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, music.duration)
                .build()
        )
    }

    private fun notifyPlaybackError(error: String) {
        Log.e(TAG, error)
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY)
                .setErrorMessage(error)
                .setState(
                    PlaybackState.STATE_ERROR,
                    getCurrentPosition().toLong(),
                    0f,
                    SystemClock.elapsedRealtime()
                )
                .build()
        )
        updateNotification()
    }

    private fun startProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable)
        progressHandler.post(progressRunnable)
    }

    // ============ 播放状态持久化 ============

    /** 防抖保存：1 秒内连续拖动进度条或切模式时，只落盘最后一次。 */
    private fun scheduleStateSave() {
        if (!::stateManager.isInitialized) return
        saveHandler.removeCallbacks(saveStateRunnable)
        saveHandler.postDelayed(saveStateRunnable, SAVE_DEBOUNCE_MS)
    }

    /** 5 秒保底保存，避免长时间播放时进度从未落盘。 */
    private fun startAutoSave() {
        saveHandler.removeCallbacks(autoSaveRunnable)
        saveHandler.postDelayed(autoSaveRunnable, AUTO_SAVE_INTERVAL_MS)
    }

    /**
     * 只有当前确实有歌曲时才保存，防止服务刚创建、歌单尚未扫描完成时
     * 用 -1 覆盖掉上一次的有效状态。
     */
    private fun savePlaybackState() {
        if (!::stateManager.isInitialized || !playlistLoaded || !playbackStarted) return
        val music = getCurrentMusic() ?: return
        val state = MyPlaybackState(
            currentMusicId = music.id,
            currentPosition = playlistManager.getCurrentPosition(),
            playbackProgress = getCurrentPosition().coerceAtLeast(0),
            playMode = getPlayMode(),
            isPlaying = isPlaying(),
            timestamp = System.currentTimeMillis()
        )
        stateManager.savePlaybackState(state)
        Log.d(
            TAG,
            "state saved: id=${state.currentMusicId}, progress=${state.playbackProgress}, " +
                "mode=${state.playMode}, isPlaying=${state.isPlaying}"
        )
    }

    // ============ 前台通知 ============

    private fun ensureForeground() {
        if (!serviceStartedForPlayback) {
            serviceStartedForPlayback = true
            // 让服务进入 started 状态，Activity 解绑后仍可继续播放。
            startService(Intent(this, MusicPlayService::class.java))
        }
        val notification = buildNotification()
        if (!isForegroundStarted) {
            runCatching {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
                isForegroundStarted = true
            }.onFailure { Log.e(TAG, "startForeground failed", it) }
        } else {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        if (isForegroundStarted) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val music = getCurrentMusic()
        val playPauseIcon = if (isPlaying()) R.drawable.ic_notification_pause else R.drawable.ic_notification_play
        val playPauseTitle = getString(if (isPlaying()) R.string.pause else R.string.play)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(music?.title ?: getString(R.string.notification_idle))
            .setContentText(music?.artist ?: getString(R.string.notification_idle_hint))
            .setSubText(music?.album)
            .setSmallIcon(R.drawable.ic_notification_music)
            .setContentIntent(contentPendingIntent())
            .setOngoing(isPlaying())
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(notificationAction(R.drawable.ic_notification_prev, getString(R.string.previous), ACTION_PREVIOUS, 1))
            .addAction(notificationAction(playPauseIcon, playPauseTitle, ACTION_TOGGLE, 2))
            .addAction(notificationAction(R.drawable.ic_notification_next, getString(R.string.next), ACTION_NEXT, 3))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun notificationAction(iconRes: Int, title: String, action: String, requestCode: Int) =
        Notification.Action.Builder(
            Icon.createWithResource(this, iconRes),
            title,
            PendingIntent.getService(
                this,
                requestCode,
                Intent(this, MusicPlayService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        ).build()

    private fun contentPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        // 先保存，再释放 MediaPlayer；强杀不会走这里，但正常销毁可兜底。
        savePlaybackState()
        if (::audioFocusManager.isInitialized) audioFocusManager.abandonAudioFocus()
        usbStorageObserver?.stop()
        usbStorageObserver = null
        // 硬按键库是进程级单例，必须显式移除回调，避免它继续强引用已销毁的 Service。
        hardKeyPolicyManager?.let { manager ->
            runCatching { manager.removeKeyEventCallBack(hardKeyEventCallback) }
                .onFailure { Log.w(TAG, "remove hardkey callback failed", it) }
        }
        hardKeyPolicyManager = null
        saveHandler.removeCallbacksAndMessages(null)
        progressHandler.removeCallbacksAndMessages(null)
        ioScope.cancel()
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        if (::mediaSession.isInitialized) {
            mediaSession.isActive = false
            mediaSession.release()
        }
        if (isForegroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForegroundStarted = false
        }
        if (::notificationManager.isInitialized) {
            notificationManager.cancel(NOTIFICATION_ID)
        }
        super.onDestroy()
    }
}
