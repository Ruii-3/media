package com.example.automediaplayer

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.automediaplayer.manager.PlaylistManager
import com.example.automediaplayer.manager.StateManager
import com.example.automediaplayer.model.MusicData
import com.example.automediaplayer.model.MyPlaybackState
import com.example.automediaplayer.model.PlayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 第 6 周 CP6：跨模块集成测试。
 *
 * 不使用 Mock 替换 SharedPreferences，而是让 PlaylistManager 与 StateManager
 * 在 Robolectric 提供的真实 Android 环境中协作，验证“切歌 -> 持久化 -> 重建后恢复”
 * 这条跨模块链路，而不是单个类的内部实现。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class PlaybackFlowIntegrationTest {

    private lateinit var context: Context
    private lateinit var playlistManager: PlaylistManager
    private lateinit var playlist: List<MusicData>

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        StateManager(context).clearPlaybackState()
        playlistManager = PlaylistManager()
        playlist = listOf(
            music(1L, "Song 1"),
            music(2L, "Song 2"),
            music(3L, "Song 3")
        )
        playlistManager.setPlaylist(playlist)
    }

    @Test
    fun playNextThenPersistAndRestoreKeepsSongAndProgress() {
        playlistManager.moveToNext()
        val current = playlistManager.getCurrentMusic()
        assertNotNull(current)

        val state = MyPlaybackState(
            currentMusicId = current!!.id,
            currentPosition = playlistManager.getCurrentPosition(),
            playbackProgress = 5_000,
            playMode = PlayMode.LOOP_ALL,
            isPlaying = true,
            timestamp = System.currentTimeMillis()
        )
        StateManager(context).savePlaybackState(state)

        val restored = StateManager(context).loadValidPlaybackState()

        assertNotNull(restored)
        assertEquals(2L, restored!!.currentMusicId)
        assertEquals(1, restored.currentPosition)
        assertEquals(5_000, restored.playbackProgress)
        assertEquals(PlayMode.LOOP_ALL, restored.playMode)
        assertEquals(true, restored.isPlaying)
    }

    @Test
    fun loopModeSelectionSurvivesStateReload() {
        playlistManager.setPlayMode(PlayMode.LOOP_ALL)
        playlistManager.moveToPosition(2)
        playlistManager.moveToNext()

        assertEquals(0, playlistManager.getCurrentPosition())
        assertEquals(1L, playlistManager.getCurrentMusic()?.id)

        StateManager(context).savePlaybackState(
            MyPlaybackState(
                currentMusicId = 1L,
                playMode = PlayMode.LOOP_ALL,
                timestamp = System.currentTimeMillis()
            )
        )

        val restored = StateManager(context).loadValidPlaybackState()
        assertEquals(PlayMode.LOOP_ALL, restored?.playMode)
    }

    @Test
    fun clearingStatePreventsRestoreAfterUsbRemoval() {
        StateManager(context).savePlaybackState(
            MyPlaybackState(
                currentMusicId = 2L,
                playbackProgress = 1_000,
                isPlaying = true,
                timestamp = System.currentTimeMillis()
            )
        )
        assertNotNull(StateManager(context).loadValidPlaybackState())

        StateManager(context).clearPlaybackState()

        assertNull(StateManager(context).loadValidPlaybackState())
    }

    private fun music(id: Long, title: String): MusicData = MusicData(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        duration = 180_000L,
        uri = Uri.parse("file:///test$id.mp3"),
        filePath = "/test/test$id.mp3"
    )
}