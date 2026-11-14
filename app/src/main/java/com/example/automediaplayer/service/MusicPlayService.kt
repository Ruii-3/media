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
import android.os.ResultReceiver
import android.os.SystemClock
import android.service.media.MediaBrowserService
import android.util.Log
import com.example.automediaplayer.R
import com.example.automediaplayer.manager.PlaylistManager
import com.example.automediaplayer.model.MusicData
import com.example.automediaplayer.model.PlayMode
import com.example.automediaplayer.ui.MainActivity
import com.example.automediaplayer.util.MusicScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        const val EXTRA_DURATION = "duration"
        const val EXTRA_FILE_PATH = "filePath"
        const val EXTRA_ALBUM_ID = "albumId"

        private const val PROGRESS_INTERVAL_MS = 500L
    }

    private val playlistManager = PlaylistManager()
    private val progressHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var mediaPlayer: MediaPlayer? = null
    private var isPrepared = false
    private var isForegroundStarted = false
    private var serviceStartedForPlayback = false
    private var pendingMusicId: Long? = null

    private lateinit var mediaSession: MediaSession
    private lateinit var notificationManager: NotificationManager

    private var playlistLoaded = false
    private var scanInProgress = false
    private var pendingPlayMediaId: String? = null

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
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
        initializeMediaSession()
        initializeMediaPlayer()
        // 服务一创建就开始扫描；有权限时通常能在首次订阅前完成。
        loadPlaylistAsync(force = false)
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
    ): BrowserRoot {
        Log.d(TAG, "onGetRoot: pkg=$callingPackage uid=$clientUid")
        // 课程阶段允许本应用和系统媒体框架浏览；量产项目可在此校验包名/Uid 白名单。
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
        if (scanInProgress || (playlistLoaded && !force)) return
        scanInProgress = true
        ioScope.launch {
            val musicList = runCatching {
                MusicScanner.scanMusicFiles(this@MusicPlayService)
            }.onFailure {
                Log.e(TAG, "扫描媒体库失败", it)
            }.getOrElse { emptyList() }

            withContext(Dispatchers.Main) {
                val currentId = getCurrentMusic()?.id
                playlistManager.setPlaylist(musicList, currentId ?: 0L)
                playlistLoaded = true
                scanInProgress = false

                val items = buildMediaItems()
                // 已订阅的客户端会因 notifyChildrenChanged 重新收到列表。
                notifyChildrenChanged(MEDIA_ROOT_ID, Bundle())
                mediaSession.sendSessionEvent(COMMAND_PLAYLIST_UPDATED, Bundle())
                Log.i(TAG, "Media tree loaded: ${items.size} items")

                pendingPlayMediaId?.let { mediaId ->
                    pendingPlayMediaId = null
                    playFromMediaId(mediaId)
                }
            }
        }
    }

    fun reloadPlaylist() {
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
                prepared.start()
                ensureForeground()
                syncPlaybackState()
                startProgressUpdates()
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
                pause()
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
            mediaPlayer?.start()
            ensureForeground()
            syncPlaybackState()
        } else {
            val index = playlistManager.getCurrentIndex()
            if (index >= 0) playMusicAt(index)
        }
    }

    fun pause() {
        if (isPrepared && isPlaying()) {
            mediaPlayer?.pause()
            syncPlaybackState()
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

    fun playFromMediaId(mediaId: String) {
        if (!playlistLoaded) {
            pendingPlayMediaId = mediaId
            loadPlaylistAsync(force = false)
            return
        }
        val id = mediaId.toLongOrNull() ?: return
        val index = playlistManager.getPlaylist().indexOfFirst { it.id == id }
        if (index >= 0) {
            playMusicAt(index)
        } else {
            Log.w(TAG, "Media id not found in queue: $mediaId")
        }
    }

    fun playMusicAt(position: Int) {
        val music = playlistManager.moveToPosition(position) ?: return
        pendingMusicId = music.id
        isPrepared = false
        try {
            mediaPlayer?.reset()
            mediaPlayer?.setDataSource(applicationContext, music.uri)
            // 先同步元数据，让客户端在 prepare 期间就能显示歌曲信息。
            updateSessionMetadata()
            syncPlaybackState()
            ensureForeground()
            mediaPlayer?.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "playMusicAt failed: ${music.uri}", e)
            notifyPlaybackError("播放失败：${e.message}")
        }
    }

    fun seekTo(positionMs: Int) {
        if (!isPrepared) return
        val safePosition = positionMs.coerceIn(0, getDuration())
        mediaPlayer?.seekTo(safePosition)
        syncPlaybackState()
    }

    /** 停止但不销毁 MediaPlayer，保留当前曲目，便于再次 play()。 */
    fun stopPlayback() {
        if (isPrepared) mediaPlayer?.pause()
        syncPlaybackState()
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
            return
        }
        if (playlistManager.moveToNext(manual = false)) {
            playMusicAt(playlistManager.getCurrentIndex())
        } else {
            pauseAtEnd()
        }
    }

    private fun pauseAtEnd() {
        if (!isPrepared) return
        runCatching { mediaPlayer?.seekTo(0) }
        runCatching { mediaPlayer?.pause() }
        syncPlaybackState()
    }

    // ============ 播放模式 ============

    fun setPlayMode(mode: PlayMode) {
        if (mode != getPlayMode()) {
            playlistManager.setPlayMode(mode)
        }
        syncPlaybackState()
        sendPlayModeEvent(mode)
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
        val extras = Bundle().apply { putString(KEY_PLAY_MODE, getPlayMode().name) }
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
