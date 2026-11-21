package com.example.automediaplayer.ui

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.automediaplayer.R
import com.example.automediaplayer.adapter.MusicAdapter
import com.example.automediaplayer.databinding.ActivityPassengerBinding
import com.example.automediaplayer.model.MusicData
import com.example.automediaplayer.model.PlayMode
import com.example.automediaplayer.service.MusicPlayService
import com.example.automediaplayer.util.AlbumArtLoader
import java.util.Locale

/**
 * 第 5 周：副驾屏控制器。
 *
 * 本类不持有 MediaPlayer，也不自己扫描媒体库：
 * - MediaBrowser 连接同一个 MusicPlayService，订阅同一棵媒体树；
 * - MediaController 基于同一个 sessionToken 下发 play/pause/skip/seek；
 * - PlaybackState / MediaMetadata / SessionEvent 把主屏的操作实时同步到副驾屏。
 *
 * 因此主屏、副驾屏、通知栏和方控操作的是同一个播放内核，不会出现“两个播放器各放各的”。
 */
class PassengerActivity : AppCompatActivity() {

    private companion object {
        const val TAG = "PassengerActivity"
        const val PROGRESS_INTERVAL_MS = 500L
    }

    private lateinit var binding: ActivityPassengerBinding
    private lateinit var musicAdapter: MusicAdapter

    private var mediaBrowser: MediaBrowser? = null
    private var mediaController: MediaController? = null
    private var playbackState: PlaybackState? = null
    private var mediaMetadata: MediaMetadata? = null
    private var allMusic: List<MusicData> = emptyList()
    private var currentPlayMode = PlayMode.ORDER
    private var isTrackingTouch = false
    private var subscribedRootId: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val progressTicker = object : Runnable {
        override fun run() {
            updateProgressUi()
            mainHandler.postDelayed(this, PROGRESS_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPassengerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initViews()
        setupRecyclerView()
        setupMediaBrowser()
    }

    private fun initViews() {
        binding.btnPlayPause.setOnClickListener { togglePlayPause() }
        binding.btnPrev.setOnClickListener {
            mediaController?.transportControls?.skipToPrevious() ?: showServiceNotReady()
        }
        binding.btnNext.setOnClickListener {
            mediaController?.transportControls?.skipToNext() ?: showServiceNotReady()
        }
        binding.btnMode.setOnClickListener {
            val controller = mediaController ?: return@setOnClickListener showServiceNotReady()
            controller.sendCommand(MusicPlayService.SET_PLAY_MODE, null, null)
        }
        binding.playerProgressBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        binding.tvProgressCurrent.text = formatDuration(progress.toLong())
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isTrackingTouch = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    isTrackingTouch = false
                    seekBar?.let {
                        mediaController?.transportControls?.seekTo(it.progress.toLong())
                    }
                }
            }
        )
        updateModeUi(currentPlayMode)
    }

    private fun setupRecyclerView() {
        musicAdapter = MusicAdapter(
            emptyList(),
            onItemClick = { music ->
                mediaController?.transportControls?.playFromMediaId(music.id.toString(), null)
                    ?: showServiceNotReady()
            },
            onFavoriteClick = {},
            showFavoriteAction = false
        )
        binding.rvMusic.apply {
            layoutManager = LinearLayoutManager(this@PassengerActivity)
            adapter = musicAdapter
        }
        musicAdapter.attachRecyclerView(binding.rvMusic)
    }

    private fun setupMediaBrowser() {
        val browser = MediaBrowser(
            this,
            ComponentName(this, MusicPlayService::class.java),
            connectionCallback,
            null
        )
        mediaBrowser = browser
        browser.connect()
    }

    private val connectionCallback = object : MediaBrowser.ConnectionCallback() {
        override fun onConnected() {
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val browser = mediaBrowser ?: return@runOnUiThread
                val token = browser.sessionToken ?: run {
                    Log.e(TAG, "sessionToken 为空")
                    return@runOnUiThread
                }
                Log.i(TAG, "已连接 MusicPlayService")
                val controller = MediaController(this@PassengerActivity, token)
                mediaController = controller
                controller.registerCallback(mediaControllerCallback, mainHandler)
                playbackState = controller.playbackState
                mediaMetadata = controller.metadata
                syncFromController()
                loadPlaylist()
            }
        }

        override fun onConnectionFailed() {
            Log.e(TAG, "MediaBrowser 连接失败")
            runOnUiThread { binding.tvListStatus.setText(R.string.service_not_ready) }
        }

        override fun onConnectionSuspended() {
            Log.w(TAG, "MediaBrowser 连接中断")
        }
    }

    private val subscriptionCallback = object : MediaBrowser.SubscriptionCallback() {
        override fun onChildrenLoaded(
            parentId: String,
            children: List<MediaBrowser.MediaItem>
        ) {
            handleChildrenLoaded(children)
        }

        override fun onChildrenLoaded(
            parentId: String,
            children: List<MediaBrowser.MediaItem>,
            options: Bundle
        ) {
            handleChildrenLoaded(children)
        }

        override fun onError(parentId: String) {
            Log.e(TAG, "副驾屏订阅失败: $parentId")
        }
    }

    private fun loadPlaylist() {
        val browser = mediaBrowser ?: return
        val rootId = browser.root ?: return
        subscribedRootId = rootId
        browser.subscribe(rootId, subscriptionCallback)
    }

    private fun reloadPlaylist() {
        val browser = mediaBrowser ?: return
        val rootId = subscribedRootId ?: browser.root ?: return
        mainHandler.postDelayed({
            runCatching {
                browser.unsubscribe(rootId)
                browser.subscribe(rootId, subscriptionCallback)
            }.onFailure { Log.w(TAG, "reloadPlaylist failed", it) }
        }, 150L)
    }

    private fun handleChildrenLoaded(children: List<MediaBrowser.MediaItem>) {
        runOnUiThread {
            allMusic = children.mapNotNull { it.toMusicData() }
            musicAdapter.updateData(allMusic)
            binding.tvListStatus.text = if (allMusic.isEmpty()) {
                getString(R.string.passenger_list_empty)
            } else {
                getString(R.string.passenger_songs, allMusic.size)
            }
            resolveCurrentMusic()
            if (allMusic.isEmpty()) showIdleMetadata()
            Log.i(TAG, "副驾屏歌单更新: ${allMusic.size} 首")
        }
    }

    private fun MediaBrowser.MediaItem.toMusicData(): MusicData? {
        val description = description ?: return null
        val id = description.mediaId?.toLongOrNull() ?: return null
        val extras = description.extras
        return MusicData(
            id = id,
            title = description.title?.toString().orEmpty().ifBlank { getString(R.string.unknown_title) },
            artist = description.subtitle?.toString().orEmpty().ifBlank { getString(R.string.unknown_artist) },
            album = description.description?.toString().orEmpty().ifBlank { getString(R.string.unknown_album) },
            duration = extras?.getLong(MusicPlayService.EXTRA_DURATION, 0L) ?: 0L,
            uri = description.mediaUri ?: Uri.EMPTY,
            filePath = extras?.getString(MusicPlayService.EXTRA_FILE_PATH).orEmpty(),
            albumId = extras?.getLong(MusicPlayService.EXTRA_ALBUM_ID, 0L) ?: 0L
        )
    }

    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            playbackState = state
            runOnUiThread { updatePlayState() }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            mediaMetadata = metadata
            runOnUiThread { updateCurrentMusicFromMetadata(metadata) }
        }

        override fun onSessionEvent(event: String, extras: Bundle?) {
            super.onSessionEvent(event, extras)
            when (event) {
                MusicPlayService.COMMAND_PLAY_MODE_CHANGED -> {
                    extras?.getString(MusicPlayService.KEY_NEW_MODE)?.let { modeName ->
                        runOnUiThread { applyPlayMode(modeName) }
                    }
                }
                MusicPlayService.COMMAND_FOCUS_CHANGED -> {
                    extras?.getString(MusicPlayService.KEY_FOCUS_STATE)?.let { state ->
                        runOnUiThread { updateFocusState(state) }
                    }
                }
                MusicPlayService.COMMAND_PLAYLIST_UPDATED -> {
                    Log.i(TAG, "服务端歌单已更新，副驾屏重新订阅")
                    reloadPlaylist()
                }
            }
        }
    }

    private fun syncFromController() {
        val controller = mediaController ?: return
        playbackState = controller.playbackState ?: playbackState
        mediaMetadata = controller.metadata ?: mediaMetadata
        mediaMetadata?.let { updateCurrentMusicFromMetadata(it) }
        updatePlayState()
    }

    private fun togglePlayPause() {
        val controller = mediaController ?: return showServiceNotReady()
        if (playbackState?.state == PlaybackState.STATE_PLAYING) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    private fun updatePlayState() {
        val state = playbackState
        val playing = state?.state == PlaybackState.STATE_PLAYING
        binding.btnPlayPause.setImageResource(
            if (playing) R.drawable.ic_pause_white else R.drawable.ic_play_white
        )
        binding.btnPlayPause.contentDescription = getString(if (playing) R.string.pause else R.string.play)
        state?.extras?.getString(MusicPlayService.KEY_PLAY_MODE)?.let { applyPlayMode(it) }
        updateFocusState(
            state?.extras?.getString(MusicPlayService.KEY_FOCUS_STATE)
                ?: MusicPlayService.FOCUS_NORMAL
        )
        updateProgressUi()
    }

    private fun updateProgressUi() {
        if (isTrackingTouch) return
        val state = playbackState
        val duration = mediaMetadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)
            ?.takeIf { it > 0L }
            ?: 0L
        var position = state?.position ?: 0L
        if (state?.state == PlaybackState.STATE_PLAYING && state.playbackSpeed > 0f) {
            val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            position += (elapsed * state.playbackSpeed).toLong()
        }
        position = position.coerceAtLeast(0L)
        if (duration > 0L) position = position.coerceAtMost(duration)
        binding.playerProgressBar.max = duration.toInt()
        binding.playerProgressBar.progress = position.toInt()
        binding.tvProgressCurrent.text = formatDuration(position)
        binding.tvProgressMax.text = formatDuration(duration)
    }

    private fun updateCurrentMusicFromMetadata(metadata: MediaMetadata?) {
        if (metadata == null) {
            showIdleMetadata()
            return
        }
        mediaMetadata = metadata
        val mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)?.toLongOrNull()
        val known = allMusic.firstOrNull { it.id == mediaId }
        binding.tvTitle.text = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: getString(R.string.unknown_title)
        binding.tvArtist.text = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: getString(R.string.unknown_artist)
        binding.tvAlbum.text = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
            ?: getString(R.string.unknown_album)
        val albumId = known?.albumId ?: 0L
        AlbumArtLoader.load(this, albumId, binding.imgCover, R.drawable.ic_album_placeholder)
        musicAdapter.setSelectedMusicId(mediaId)
        updateProgressUi()
    }

    private fun showIdleMetadata() {
        mediaMetadata = null
        binding.tvTitle.setText(R.string.no_song_selected)
        binding.tvArtist.setText(R.string.no_song_selected_hint)
        binding.tvAlbum.text = ""
        binding.imgCover.setImageResource(R.drawable.ic_album_placeholder)
        musicAdapter.setSelectedMusicId(null)
        binding.playerProgressBar.progress = 0
        binding.tvProgressCurrent.text = "00:00"
        binding.tvProgressMax.text = "00:00"
    }

    private fun resolveCurrentMusic() {
        val mediaId = mediaMetadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
            ?.toLongOrNull()
            ?: return
        if (allMusic.any { it.id == mediaId }) {
            musicAdapter.setSelectedMusicId(mediaId)
        }
    }

    private fun applyPlayMode(modeName: String) {
        val mode = runCatching { PlayMode.valueOf(modeName) }.getOrNull() ?: return
        updateModeUi(mode)
    }

    private fun updateModeUi(mode: PlayMode) {
        currentPlayMode = mode
        binding.btnMode.setImageResource(modeIconRes(mode))
        binding.tvModeLabel.text = getString(R.string.current_mode, getString(modeLabelRes(mode)))
    }

    private fun updateFocusState(state: String) {
        val (textRes, colorRes) = when (state) {
            MusicPlayService.FOCUS_DUCKED -> R.string.focus_ducked to R.color.wisteria_800
            MusicPlayService.FOCUS_TRANSIENT_PAUSED -> R.string.focus_transient to R.color.wisteria_600
            MusicPlayService.FOCUS_LOST -> R.string.focus_lost to R.color.accent_heart
            else -> R.string.focus_normal to R.color.wisteria_700
        }
        binding.tvFocusState.setText(textRes)
        binding.tvFocusState.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    private fun modeLabelRes(mode: PlayMode): Int = when (mode) {
        PlayMode.ORDER -> R.string.mode_order
        PlayMode.LOOP_ALL -> R.string.mode_loop_all
        PlayMode.LOOP_ONE -> R.string.mode_loop_one
        PlayMode.SHUFFLE -> R.string.mode_shuffle
    }

    private fun modeIconRes(mode: PlayMode): Int = when (mode) {
        PlayMode.ORDER -> R.drawable.ic_mode_order
        PlayMode.LOOP_ALL -> R.drawable.ic_mode_loop_all
        PlayMode.LOOP_ONE -> R.drawable.ic_mode_loop_one
        PlayMode.SHUFFLE -> R.drawable.ic_mode_shuffle
    }

    private fun showServiceNotReady() {
        Toast.makeText(this, R.string.service_not_ready, Toast.LENGTH_SHORT).show()
    }

    private fun formatDuration(timeMs: Long): String {
        val totalSeconds = (timeMs / 1000).coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    override fun onStart() {
        super.onStart()
        mainHandler.post(progressTicker)
    }

    override fun onResume() {
        super.onResume()
        syncFromController()
    }

    override fun onStop() {
        mainHandler.removeCallbacks(progressTicker)
        super.onStop()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(progressTicker)
        mediaController?.unregisterCallback(mediaControllerCallback)
        mediaController = null
        mediaBrowser?.disconnect()
        mediaBrowser = null
        super.onDestroy()
    }
}
