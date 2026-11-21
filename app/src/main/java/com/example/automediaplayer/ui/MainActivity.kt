package com.example.automediaplayer.ui

import android.Manifest
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.automediaplayer.R
import com.example.automediaplayer.adapter.MusicAdapter
import com.example.automediaplayer.databinding.ActivityMainBinding
import com.example.automediaplayer.databinding.DialogCockpitDemoBinding
import com.example.automediaplayer.databinding.DialogPlaySettingsBinding
import com.example.automediaplayer.manager.FavoriteManager
import com.example.automediaplayer.model.MusicData
import com.example.automediaplayer.model.PlayMode
import com.example.automediaplayer.service.MusicPlayService
import com.example.automediaplayer.util.AlbumArtLoader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

/**
 * 第 3 周主界面：标准 MediaBrowser 客户端。
 *
 * 界面不再绑定 MusicBinder，也不再直接扫描媒体库：
 * - MediaBrowser 负责连接 MediaBrowserService，并通过 subscribe 获取歌单；
 * - sessionToken 创建 MediaController，播放控制全部走 transportControls；
 * - PlaybackState / MediaMetadata / SessionEvent 回来后再刷新界面。
 *
 * 因此主屏、通知栏、系统媒体中心和 adb 媒体键操作的是同一个 MediaSession。
 */
class MainActivity : AppCompatActivity() {

    private companion object {
        const val TAG = "MainActivity"
        const val REQUEST_READ_AUDIO = 0x1001
        const val REQUEST_NOTIFICATION = 0x1002
        const val PROGRESS_INTERVAL_MS = 500L
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var musicAdapter: MusicAdapter
    private lateinit var favoriteManager: FavoriteManager

    private var mediaBrowser: MediaBrowser? = null
    private var mediaController: MediaController? = null
    private var playbackState: PlaybackState? = null
    private var mediaMetadata: MediaMetadata? = null

    private var allMusic: List<MusicData> = emptyList()
    private var favorites: MutableSet<Long> = mutableSetOf()
    private var showFavoritesOnly = false
    private var isTrackingTouch = false
    private var currentMusic: MusicData? = null
    private var currentPlayMode = PlayMode.ORDER
    private var pendingReloadAfterPermission = false
    private var lastPlaybackError: String? = null

    private var settingsDialog: AlertDialog? = null
    private var settingsBinding: DialogPlaySettingsBinding? = null
    private var cockpitDemoDialog: AlertDialog? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val progressTicker = object : Runnable {
        override fun run() {
            updateProgressUi()
            mainHandler.postDelayed(this, PROGRESS_INTERVAL_MS)
        }
    }

    private val audioReadPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        favoriteManager = FavoriteManager(this)
        favorites = favoriteManager.getFavoriteIds()

        initViews()
        setupRecyclerView()
        setupMediaBrowser()
        checkPermissionAndScan()
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun initViews() {
        binding.btnPlayPause.setOnClickListener {
            val controller = mediaController ?: return@setOnClickListener showServiceNotReady()
            if (mediaMetadata == null && playbackState?.state == PlaybackState.STATE_NONE) {
                Toast.makeText(this, R.string.select_song_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (playbackState?.state == PlaybackState.STATE_PLAYING) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
        }
        binding.btnPrev.setOnClickListener {
            mediaController?.transportControls?.skipToPrevious() ?: showServiceNotReady()
        }
        binding.btnNext.setOnClickListener {
            mediaController?.transportControls?.skipToNext() ?: showServiceNotReady()
        }
        binding.btnMode.setOnClickListener {
            if (mediaController == null) {
                showServiceNotReady()
                return@setOnClickListener
            }
            val nextMode = currentPlayMode.nextMode()
            requestPlayMode(nextMode)
            showModeToast(nextMode)
        }
        binding.btnFavorite.setOnClickListener {
            val music = currentMusic ?: return@setOnClickListener
            toggleFavorite(music)
        }
        binding.btnSettings.setOnClickListener { showPlaySettings() }
        binding.btnCockpitDemo.setOnClickListener { showCockpitDemo() }
        binding.btnFilterFavorites.setOnClickListener {
            showFavoritesOnly = !showFavoritesOnly
            applyFilter()
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
        updateFilterChip()
        updateModeUi(currentPlayMode)
    }

    private fun setupRecyclerView() {
        musicAdapter = MusicAdapter(
            emptyList(),
            onItemClick = { music ->
                mediaController?.transportControls?.playFromMediaId(music.id.toString(), null)
                    ?: showServiceNotReady()
            },
            onFavoriteClick = { music -> toggleFavorite(music) }
        )
        binding.rvMusic.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = musicAdapter
        }
        musicAdapter.attachRecyclerView(binding.rvMusic)
    }

    // ============ MediaBrowser / MediaController 连接 ============

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
                val token = browser.sessionToken
                if (token == null) {
                    Log.e(TAG, "MediaBrowser 已连接，但 sessionToken 为空")
                    showServiceNotReady()
                    return@runOnUiThread
                }
                Log.i(TAG, "MediaBrowser connected, sessionToken ready")
                mediaController = MediaController(this@MainActivity, token).also { controller ->
                    controller.registerCallback(mediaControllerCallback, mainHandler)
                }
                playbackState = mediaController?.playbackState
                mediaMetadata = mediaController?.metadata
                syncFromController()

                val rootId = browser.root ?: MusicPlayService.MEDIA_ROOT_ID
                browser.subscribe(rootId, subscriptionCallback)
                if (hasAudioPermission() && pendingReloadAfterPermission) {
                    requestPlaylistReload()
                }
            }
        }

        override fun onConnectionSuspended() {
            Log.w(TAG, "MediaBrowser connection suspended")
        }

        override fun onConnectionFailed() {
            Log.e(TAG, "MediaBrowser connection failed")
            runOnUiThread {
                binding.tvListStatus.text = getString(R.string.service_not_ready)
            }
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
            Log.e(TAG, "订阅媒体树失败: $parentId")
            runOnUiThread {
                binding.tvListStatus.text = getString(R.string.scan_failed, "MediaBrowser onError")
            }
        }
    }

    private fun handleChildrenLoaded(children: List<MediaBrowser.MediaItem>) {
        runOnUiThread {
            allMusic = children.mapNotNull { it.toMusicData() }
            favorites = favoriteManager.getFavoriteIds()
            musicAdapter.updateFavorites(favorites)
            applyFilter()
            resolveCurrentMusic()
            if (allMusic.isEmpty()) updateCurrentMusicInfo(null)
            Log.i(TAG, "MediaBrowser children loaded: ${allMusic.size}")
        }
    }

    /** USB 换盘后媒体树可能整体替换；重新订阅一次，保证列表与队列一致。 */
    private fun refreshPlaylistSubscription() {
        val browser = mediaBrowser ?: return
        val rootId = browser.root ?: return
        mainHandler.postDelayed({
            runCatching {
                browser.unsubscribe(rootId)
                browser.subscribe(rootId, subscriptionCallback)
            }.onFailure { Log.w(TAG, "refreshPlaylistSubscription failed", it) }
        }, 150L)
    }

    private fun MediaBrowser.MediaItem.toMusicData(): MusicData? {
        val description = description ?: return null
        val id = description.mediaId?.toLongOrNull() ?: return null
        val extras = description.extras
        return MusicData(
            id = id,
            title = description.title?.toString()?.takeIf { it.isNotBlank() }
                ?: getString(R.string.unknown_title),
            artist = description.subtitle?.toString()?.takeIf { it.isNotBlank() }
                ?: getString(R.string.unknown_artist),
            album = description.description?.toString()?.takeIf { it.isNotBlank() }
                ?: getString(R.string.unknown_album),
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
                MusicPlayService.COMMAND_PLAYLIST_UPDATED -> {
                    Log.i(TAG, "服务端媒体树已更新，重新订阅")
                    refreshPlaylistSubscription()
                }
                MusicPlayService.COMMAND_FOCUS_CHANGED -> {
                    extras?.getString(MusicPlayService.KEY_FOCUS_STATE)?.let { state ->
                        runOnUiThread { updateFocusState(state) }
                    }
                }
                MusicPlayService.COMMAND_PLAYBACK_RESTORED -> {
                    val title = extras?.getString(MusicPlayService.KEY_RESTORED_TITLE).orEmpty()
                    val progress = extras?.getInt(MusicPlayService.KEY_RESTORED_PROGRESS, 0) ?: 0
                    if (title.isNotBlank()) {
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                this@MainActivity.getString(
                                    R.string.playback_restored,
                                    title,
                                    formatDuration(progress.toLong())
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
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

    // ============ 权限与重新扫描 ============

    private fun checkPermissionAndScan() {
        if (hasAudioPermission()) {
            requestNotificationPermissionIfNeeded()
        } else {
            binding.tvListStatus.text = getString(R.string.permission_requesting)
            ActivityCompat.requestPermissions(
                this,
                arrayOf(audioReadPermission),
                REQUEST_READ_AUDIO
            )
        }
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, audioReadPermission) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_NOTIFICATION
        )
    }

    private fun requestPlaylistReload() {
        val controller = mediaController
        if (!hasAudioPermission() || controller == null) {
            pendingReloadAfterPermission = true
            return
        }
        pendingReloadAfterPermission = false
        binding.tvListStatus.text = getString(R.string.scanning)
        controller.sendCommand(MusicPlayService.COMMAND_RELOAD_PLAYLIST, null, null)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_READ_AUDIO -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    pendingReloadAfterPermission = true
                    requestPlaylistReload()
                    requestNotificationPermissionIfNeeded()
                } else {
                    Log.w(TAG, "音频读取权限被拒绝，无法扫描音乐")
                    binding.tvListStatus.text = getString(R.string.permission_required)
                    Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
                }
            }
            REQUEST_NOTIFICATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.notification_permission_denied, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ============ 状态与界面 ============

    private fun updatePlayState() {
        val state = playbackState
        updatePlayButton(state?.state == PlaybackState.STATE_PLAYING)
        state?.extras?.getString(MusicPlayService.KEY_PLAY_MODE)?.let { applyPlayMode(it) }
        updateFocusState(
            state?.extras?.getString(MusicPlayService.KEY_FOCUS_STATE)
                ?: MusicPlayService.FOCUS_NORMAL
        )

        if (state?.state == PlaybackState.STATE_ERROR) {
            val error = state.errorMessage?.toString().orEmpty()
            if (error.isNotBlank() && error != lastPlaybackError) {
                lastPlaybackError = error
                showErrorMessage(error)
            }
        } else {
            lastPlaybackError = null
        }
        updateProgressUi()
    }

    private fun updateProgressUi() {
        if (isTrackingTouch) return
        val state = playbackState
        val duration = mediaMetadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)
            ?.takeIf { it > 0L }
            ?: currentMusic?.duration
            ?: 0L
        var position = state?.position ?: 0L
        if (state?.state == PlaybackState.STATE_PLAYING && state.playbackSpeed > 0f) {
            val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            position += (elapsed * state.playbackSpeed).toLong()
        }
        position = position.coerceAtLeast(0L)
        if (duration > 0L) position = position.coerceAtMost(duration)

        binding.playerProgressBar.max = duration.coerceAtLeast(0L).toInt()
        binding.playerProgressBar.progress = position.toInt()
        binding.tvProgressCurrent.text = formatDuration(position)
        binding.tvProgressMax.text = formatDuration(duration)
    }

    private fun updateCurrentMusicFromMetadata(metadata: MediaMetadata?) {
        if (metadata == null) {
            updateCurrentMusicInfo(null)
            return
        }
        mediaMetadata = metadata
        val mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)?.toLongOrNull()
        val known = allMusic.firstOrNull { it.id == mediaId }
        if (known != null) {
            updateCurrentMusicInfo(known)
            return
        }
        val music = mediaId?.let {
            MusicData(
                id = it,
                title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: getString(R.string.unknown_title),
                artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: getString(R.string.unknown_artist),
                album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
                    ?: getString(R.string.unknown_album),
                duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
                uri = Uri.EMPTY,
                filePath = "",
                albumId = 0L
            )
        }
        updateCurrentMusicInfo(music)
    }

    private fun resolveCurrentMusic() {
        val mediaId = mediaMetadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
            ?.toLongOrNull()
            ?: return
        allMusic.firstOrNull { it.id == mediaId }?.let(::updateCurrentMusicInfo)
    }

    private fun applyPlayMode(modeName: String) {
        val mode = runCatching { PlayMode.valueOf(modeName) }.getOrNull() ?: return
        currentPlayMode = mode
        updateModeUi(mode)
    }

    private fun requestPlayMode(mode: PlayMode) {
        val controller = mediaController ?: return showServiceNotReady()
        currentPlayMode = mode
        updateModeUi(mode)
        val extras = Bundle().apply { putString(MusicPlayService.KEY_NEW_MODE, mode.name) }
        controller.sendCommand(MusicPlayService.SET_PLAY_MODE, extras, null)
    }

    private fun applyFilter() {
        val visibleList = if (showFavoritesOnly) {
            allMusic.filter { favorites.contains(it.id) }
        } else {
            allMusic
        }
        musicAdapter.updateData(visibleList)
        musicAdapter.updateFavorites(favorites)
        updateFilterChip()
        updateListStatus()
    }

    private fun updateListStatus() {
        binding.tvListStatus.text = when {
            !hasAudioPermission() -> getString(R.string.permission_required)
            allMusic.isEmpty() -> getString(R.string.scan_empty)
            showFavoritesOnly && musicAdapter.itemCount == 0 ->
                "${getString(R.string.favorites_empty)} · ${getString(R.string.favorites_empty_hint)}"
            else -> getString(R.string.music_count_favorites, allMusic.size, favorites.size)
        }
    }

    private fun updateFilterChip() {
        binding.btnFilterFavorites.text =
            getString(if (showFavoritesOnly) R.string.favorites else R.string.all_music)
        binding.btnFilterFavorites.setBackgroundResource(
            if (showFavoritesOnly) R.drawable.bg_chip_selected else R.drawable.bg_chip_lavender
        )
        binding.btnFilterFavorites.setTextColor(
            ContextCompat.getColor(
                this,
                if (showFavoritesOnly) R.color.white else R.color.wisteria_700
            )
        )
    }

    private fun toggleFavorite(music: MusicData) {
        val nowFavorite = favoriteManager.toggleFavorite(music.id)
        if (nowFavorite) favorites.add(music.id) else favorites.remove(music.id)
        musicAdapter.updateFavorites(favorites)
        updateFavoriteButton()
        Toast.makeText(
            this,
            if (nowFavorite) R.string.favorite_added else R.string.favorite_removed,
            Toast.LENGTH_SHORT
        ).show()
        if (showFavoritesOnly) applyFilter() else updateListStatus()
    }

    private fun updateCurrentMusicInfo(music: MusicData?) {
        currentMusic = music
        if (music == null) {
            binding.tvTitle.text = getString(R.string.no_song_selected)
            binding.tvArtist.text = getString(R.string.no_song_selected_hint)
            binding.tvAlbum.text = ""
            binding.imgCover.setImageResource(R.drawable.ic_album_placeholder)
            musicAdapter.setSelectedMusicId(null)
        } else {
            binding.tvTitle.text = music.title
            binding.tvArtist.text = music.artist
            binding.tvAlbum.text = music.album
            AlbumArtLoader.load(this, music.albumId, binding.imgCover, R.drawable.ic_album_placeholder)
            musicAdapter.setSelectedMusicId(music.id)
        }
        updateFavoriteButton()
    }

    private fun updateFavoriteButton() {
        val favorite = currentMusic?.let { favorites.contains(it.id) } == true
        binding.btnFavorite.setImageResource(
            if (favorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
        )
        binding.btnFavorite.contentDescription =
            getString(if (favorite) R.string.unfavorite else R.string.favorite)
    }

    private fun updatePlayButton(isPlaying: Boolean) {
        binding.btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause_white else R.drawable.ic_play_white
        )
        binding.btnPlayPause.contentDescription =
            getString(if (isPlaying) R.string.pause else R.string.play)
    }

    private fun updateModeUi(mode: PlayMode) {
        binding.btnMode.setImageResource(modeIconRes(mode))
        binding.btnMode.contentDescription = getString(R.string.play_mode)
        binding.tvModeLabel.text = getString(R.string.current_mode, getString(modeLabelRes(mode)))
    }

    private fun showModeToast(mode: PlayMode) {
        Toast.makeText(
            this,
            getString(R.string.mode_changed, getString(modeLabelRes(mode))),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun modeLabelRes(mode: PlayMode): Int = when (mode) {
        PlayMode.ORDER -> R.string.mode_order
        PlayMode.LOOP_ALL -> R.string.mode_loop_all
        PlayMode.LOOP_ONE -> R.string.mode_loop_one
        PlayMode.SHUFFLE -> R.string.mode_shuffle
    }

    private fun modeDescRes(mode: PlayMode): Int = when (mode) {
        PlayMode.ORDER -> R.string.mode_order_desc
        PlayMode.LOOP_ALL -> R.string.mode_loop_all_desc
        PlayMode.LOOP_ONE -> R.string.mode_loop_one_desc
        PlayMode.SHUFFLE -> R.string.mode_shuffle_desc
    }

    private fun modeIconRes(mode: PlayMode): Int = when (mode) {
        PlayMode.ORDER -> R.drawable.ic_mode_order
        PlayMode.LOOP_ALL -> R.drawable.ic_mode_loop_all
        PlayMode.LOOP_ONE -> R.drawable.ic_mode_loop_one
        PlayMode.SHUFFLE -> R.drawable.ic_mode_shuffle
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

    /**
     * CP5 模拟入口：模拟器没有真实导航/来电流，这里直接调用服务端与真实
     * AudioFocus 回调相同的业务分支。界面与文档都明确标注“模拟”。
     */
    private fun showCockpitDemo() {
        if (cockpitDemoDialog?.isShowing == true) return
        val demoBinding = DialogCockpitDemoBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(demoBinding.root)
            .create()
        cockpitDemoDialog = dialog

        demoBinding.btnDemoDuck.setOnClickListener {
            sendFocusSimulation(MusicPlayService.FOCUS_EVENT_CAN_DUCK)
        }
        demoBinding.btnDemoTransient.setOnClickListener {
            sendFocusSimulation(MusicPlayService.FOCUS_EVENT_LOSS_TRANSIENT)
        }
        demoBinding.btnDemoGain.setOnClickListener {
            sendFocusSimulation(MusicPlayService.FOCUS_EVENT_GAIN)
        }
        demoBinding.btnDemoLoss.setOnClickListener {
            sendFocusSimulation(MusicPlayService.FOCUS_EVENT_LOSS)
        }
        demoBinding.btnDemoUsbInsert.setOnClickListener { sendUsbSimulation(connected = true) }
        demoBinding.btnDemoUsbRemove.setOnClickListener { sendUsbSimulation(connected = false) }
        demoBinding.btnDemoPassenger.setOnClickListener {
            dialog.dismiss()
            launchPassengerDisplay()
        }
        demoBinding.btnDemoClose.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener { cockpitDemoDialog = null }
        dialog.show()
    }

    private fun sendFocusSimulation(event: String) {
        val controller = mediaController ?: return showServiceNotReady()
        val extras = Bundle().apply { putString(MusicPlayService.KEY_FOCUS_EVENT, event) }
        controller.sendCommand(MusicPlayService.COMMAND_SIMULATE_AUDIO_FOCUS, extras, null)
    }

    private fun sendUsbSimulation(connected: Boolean) {
        val controller = mediaController ?: return showServiceNotReady()
        val extras = Bundle().apply {
            putBoolean(MusicPlayService.KEY_USB_CONNECTED, connected)
        }
        controller.sendCommand(MusicPlayService.COMMAND_SIMULATE_USB, extras, null)
    }

    /**
     * 动态查询副屏，避免教材示例中写死 displayId=2 在不同模拟器/车机上失效。
     * 没有副屏时降级到主屏打开，便于用同一份 APK 录屏说明控制器逻辑。
     */
    private fun launchPassengerDisplay() {
        val displayManager = getSystemService(DisplayManager::class.java)
        val secondaryDisplays = displayManager?.displays
            ?.filter { it.displayId != Display.DEFAULT_DISPLAY }
            .orEmpty()
        // 优先按显示名识别副驾屏；不同车型的 displayId 顺序不一致，不能写死 2。
        val secondary = secondaryDisplays.firstOrNull {
            it.name.contains("passenger", ignoreCase = true) || it.name.contains("副驾")
        } ?: secondaryDisplays.firstOrNull { it.state == Display.STATE_ON }
            ?: secondaryDisplays.firstOrNull()
        val supported = packageManager.hasSystemFeature(
            PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS
        )
        val intent = Intent(this, PassengerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (!supported || secondary == null) {
            startActivity(intent)
            Toast.makeText(
                this,
                if (!supported) R.string.passenger_unsupported else R.string.passenger_no_display,
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(secondary.displayId)
        startActivity(intent, options.toBundle())
        Toast.makeText(
            this,
            getString(R.string.passenger_launched, secondary.displayId),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showPlaySettings() {
        if (settingsDialog?.isShowing == true) return
        val dialogBinding = DialogPlaySettingsBinding.inflate(layoutInflater)
        settingsBinding = dialogBinding

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()
        settingsDialog = dialog

        val checkedId = when (currentPlayMode) {
            PlayMode.ORDER -> R.id.rb_order
            PlayMode.LOOP_ALL -> R.id.rb_loop_all
            PlayMode.LOOP_ONE -> R.id.rb_loop_one
            PlayMode.SHUFFLE -> R.id.rb_shuffle
        }
        dialogBinding.rgMode.check(checkedId)
        dialogBinding.tvModeDesc.setText(modeDescRes(currentPlayMode))
        dialogBinding.rgMode.setOnCheckedChangeListener { _, id ->
            val mode = when (id) {
                R.id.rb_order -> PlayMode.ORDER
                R.id.rb_loop_all -> PlayMode.LOOP_ALL
                R.id.rb_loop_one -> PlayMode.LOOP_ONE
                R.id.rb_shuffle -> PlayMode.SHUFFLE
                else -> return@setOnCheckedChangeListener
            }
            requestPlayMode(mode)
            dialogBinding.tvModeDesc.setText(modeDescRes(mode))
        }

        dialogBinding.switchFavoritesOnly.isChecked = showFavoritesOnly
        dialogBinding.switchFavoritesOnly.setOnCheckedChangeListener { _, checked ->
            showFavoritesOnly = checked
            applyFilter()
        }
        dialogBinding.btnDone.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener {
            settingsDialog = null
            settingsBinding = null
        }
        dialog.show()
    }

    private fun showServiceNotReady() {
        Toast.makeText(this, R.string.service_not_ready, Toast.LENGTH_SHORT).show()
    }

    private fun showErrorMessage(message: String) {
        Log.e(TAG, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun formatDuration(timeMs: Long): String {
        val totalSeconds = (timeMs / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
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
        cockpitDemoDialog?.dismiss()
        cockpitDemoDialog = null
        mediaController?.unregisterCallback(mediaControllerCallback)
        mediaController = null
        mediaBrowser?.disconnect()
        mediaBrowser = null
        super.onDestroy()
    }
}
