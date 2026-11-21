package com.example.automediaplayer.manager

import android.net.Uri
import com.example.automediaplayer.model.MusicData
import com.example.automediaplayer.model.PlayMode
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 第 6 周 CP6：PlaylistManager 单元测试。
 *
 * 被测类只包含纯 Kotlin 队列逻辑，仅 MusicData.uri 依赖 Android。
 * 使用 MockK 模拟 Uri，避免测试依赖真实媒体文件，保证可重复执行。
 */
class PlaylistManagerTest {

    private lateinit var playlistManager: PlaylistManager
    private lateinit var playlist: List<MusicData>

    @Before
    fun setUp() {
        val uri1 = mockk<Uri>()
        val uri2 = mockk<Uri>()
        val uri3 = mockk<Uri>()
        every { uri1.toString() } returns "file:///test1.mp3"
        every { uri2.toString() } returns "file:///test2.mp3"
        every { uri3.toString() } returns "file:///test3.mp3"

        playlist = listOf(
            MusicData(1L, "Test Song 1", "Artist 1", "Album 1", 180_000L, uri1, "/test/music1.mp3"),
            MusicData(2L, "Test Song 2", "Artist 2", "Album 2", 240_000L, uri2, "/test/music2.mp3"),
            MusicData(3L, "Test Song 3", "Artist 3", "Album 3", 300_000L, uri3, "/test/music3.mp3")
        )
        playlistManager = PlaylistManager()
    }

    @Test
    fun setPlaylistWithoutIdStartsAtFirstSong() {
        playlistManager.setPlaylist(playlist)

        assertEquals(3, playlistManager.getAllMusic().size)
        assertEquals(0, playlistManager.getCurrentPosition())
        assertEquals(1L, playlistManager.getCurrentMusic()?.id)
    }

    @Test
    fun setPlaylistWithValidIdKeepsThatSong() {
        playlistManager.setPlaylist(playlist, musicId = 2L)

        assertEquals(1, playlistManager.getCurrentPosition())
        assertEquals(2L, playlistManager.getCurrentMusic()?.id)
    }

    @Test
    fun setPlaylistWithInvalidIdFallsBackToFirstSong() {
        playlistManager.setPlaylist(playlist, musicId = 999L)

        assertEquals(0, playlistManager.getCurrentPosition())
        assertEquals(1L, playlistManager.getCurrentMusic()?.id)
    }

    @Test
    fun orderModeNextStopsAtLastSong() {
        playlistManager.setPlaylist(playlist)
        playlistManager.moveToPosition(2)

        assertFalse(playlistManager.moveToNext())
        assertEquals(2, playlistManager.getCurrentPosition())
        assertEquals(3L, playlistManager.getCurrentMusic()?.id)
    }

    @Test
    fun orderModePreviousStopsAtFirstSong() {
        playlistManager.setPlaylist(playlist)
        playlistManager.moveToPosition(1)

        assertTrue(playlistManager.moveToPrevious())
        assertEquals(0, playlistManager.getCurrentPosition())
        assertTrue(playlistManager.moveToPrevious())
        assertEquals(0, playlistManager.getCurrentPosition())
    }

    @Test
    fun loopAllModeWrapsForwardAndBackward() {
        playlistManager.setPlaylist(playlist)
        playlistManager.setPlayMode(PlayMode.LOOP_ALL)
        playlistManager.moveToPosition(2)

        assertTrue(playlistManager.moveToNext())
        assertEquals(0, playlistManager.getCurrentPosition())
        assertTrue(playlistManager.moveToPrevious())
        assertEquals(2, playlistManager.getCurrentPosition())
    }

    @Test
    fun loopOneModeRepeatsNaturallyButManualNextAdvances() {
        playlistManager.setPlaylist(playlist)
        playlistManager.setPlayMode(PlayMode.LOOP_ONE)
        playlistManager.moveToPosition(1)

        assertTrue(playlistManager.moveToNext(manual = false))
        assertEquals(1, playlistManager.getCurrentPosition())

        assertTrue(playlistManager.moveToNext(manual = true))
        assertEquals(2, playlistManager.getCurrentPosition())
    }

    @Test
    fun shuffleModeKeepsContentAndCurrentSongWhenSwitching() {
        playlistManager.setPlaylist(playlist, musicId = 2L)

        playlistManager.setPlayMode(PlayMode.SHUFFLE)

        assertEquals(3, playlistManager.getPlaylist().size)
        assertEquals(playlist.map { it.id }.toSet(), playlistManager.getPlaylist().map { it.id }.toSet())
        assertEquals(2L, playlistManager.getCurrentMusic()?.id)
        assertEquals(listOf(1L, 2L, 3L), playlistManager.getAllMusic().map { it.id })
    }

    @Test
    fun shuffleNextNeverStaysOnCurrentSong() {
        playlistManager.setPlaylist(playlist)
        playlistManager.setPlayMode(PlayMode.SHUFFLE)
        val currentBefore = playlistManager.getCurrentMusic()?.id

        assertTrue(playlistManager.moveToNext())

        assertNotNull(playlistManager.getCurrentMusic())
        assertNotEquals(currentBefore, playlistManager.getCurrentMusic()?.id)
    }

    @Test
    fun shufflePreviousReturnsAlongHistory() {
        playlistManager.setPlaylist(playlist)
        playlistManager.setPlayMode(PlayMode.SHUFFLE)
        val first = playlistManager.getCurrentMusic()?.id
        playlistManager.moveToNext()
        val second = playlistManager.getCurrentMusic()?.id

        assertTrue(playlistManager.moveToPrevious())

        assertEquals(first, playlistManager.getCurrentMusic()?.id)
        assertNotEquals(first, second)
    }

    @Test
    fun moveToPositionReturnsSongAndUpdatesIndex() {
        playlistManager.setPlaylist(playlist)

        val song = playlistManager.moveToPosition(2)

        assertEquals(3L, song?.id)
        assertEquals(2, playlistManager.getCurrentPosition())
    }

    @Test
    fun moveToPositionRejectsInvalidIndex() {
        playlistManager.setPlaylist(playlist)

        assertNull(playlistManager.moveToPosition(-1))
        assertNull(playlistManager.moveToPosition(3))
        assertEquals(0, playlistManager.getCurrentPosition())
    }

    @Test
    fun emptyPlaylistIsSafe() {
        playlistManager.setPlaylist(emptyList())

        assertEquals(-1, playlistManager.getCurrentPosition())
        assertNull(playlistManager.getCurrentMusic())
        assertFalse(playlistManager.moveToNext())
        assertFalse(playlistManager.moveToPrevious())
        assertNull(playlistManager.moveToPosition(0))
    }
}