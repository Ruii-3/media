package com.example.automediaplayer.manager

import android.content.Context
import android.content.SharedPreferences
import com.example.automediaplayer.model.MyPlaybackState
import com.example.automediaplayer.model.PlayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

/**
 * 第 6 周 CP6：StateManager 单元测试。
 *
 * 纯逻辑用 JUnit 直接断言；保存/读取用 Mockito 模拟
 * SharedPreferences 与 Editor 链式调用，验证“写了哪些键、是否 apply”。
 */
@RunWith(MockitoJUnitRunner.Silent::class)
class StateManagerTest {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var prefs: SharedPreferences

    @Mock
    private lateinit var editor: SharedPreferences.Editor

    private lateinit var stateManager: StateManager

    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        `when`(context.applicationContext).thenReturn(context)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        stateManager = StateManager(context)
    }

    // ---------- 纯逻辑：状态有效期 ----------

    @Test
    fun timestampWithin24HoursIsValid() {
        assertFalse(StateManager.isExpired(now - 23 * 60 * 60 * 1000L, now))
    }

    @Test
    fun timestampOlderThan24HoursIsExpired() {
        assertTrue(StateManager.isExpired(now - 25 * 60 * 60 * 1000L, now))
    }

    @Test
    fun missingTimestampIsExpired() {
        assertTrue(StateManager.isExpired(0L, now))
    }

    @Test
    fun playbackStateRequiresPositiveMusicId() {
        assertFalse(MyPlaybackState().isValid())
        assertTrue(MyPlaybackState(currentMusicId = 42L, playMode = PlayMode.LOOP_ALL).isValid())
    }

    // ---------- 协作行为：保存六字段并 apply ----------

    @Test
    fun savePlaybackStateWritesSixFieldsAndApplies() {
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putLong(anyString(), anyLong())).thenReturn(editor)
        `when`(editor.putInt(anyString(), anyInt())).thenReturn(editor)
        `when`(editor.putString(anyString(), anyString())).thenReturn(editor)
        `when`(editor.putBoolean(anyString(), anyBoolean())).thenReturn(editor)

        val state = MyPlaybackState(
            currentMusicId = 123L,
            currentPosition = 5,
            playbackProgress = 1000,
            playMode = PlayMode.SHUFFLE,
            isPlaying = true,
            timestamp = 1_000_000L
        )
        stateManager.savePlaybackState(state)

        verify(editor).putLong(StateManager.KEY_CURRENT_MUSIC_ID, 123L)
        verify(editor).putInt(StateManager.KEY_CURRENT_POSITION, 5)
        verify(editor).putInt(StateManager.KEY_PLAYBACK_PROGRESS, 1000)
        verify(editor).putString(StateManager.KEY_PLAY_MODE, "SHUFFLE")
        verify(editor).putBoolean(StateManager.KEY_IS_PLAYING, true)
        verify(editor).putLong(StateManager.KEY_TIMESTAMP, 1_000_000L)
        verify(editor).apply()
    }

    // ---------- 协作行为：读取并还原六字段 ----------

    @Test
    fun loadPlaybackStateRestoresAllFields() {
        `when`(prefs.getLong(StateManager.KEY_CURRENT_MUSIC_ID, -1L)).thenReturn(456L)
        `when`(prefs.getInt(StateManager.KEY_CURRENT_POSITION, 0)).thenReturn(10)
        `when`(prefs.getInt(StateManager.KEY_PLAYBACK_PROGRESS, 0)).thenReturn(2000)
        `when`(prefs.getString(StateManager.KEY_PLAY_MODE, PlayMode.ORDER.name))
            .thenReturn("SHUFFLE")
        `when`(prefs.getBoolean(StateManager.KEY_IS_PLAYING, false)).thenReturn(true)
        `when`(prefs.getLong(StateManager.KEY_TIMESTAMP, 0L)).thenReturn(1_000_000L)

        val loaded = stateManager.loadPlaybackState()

        assertEquals(456L, loaded.currentMusicId)
        assertEquals(10, loaded.currentPosition)
        assertEquals(2000, loaded.playbackProgress)
        assertEquals(PlayMode.SHUFFLE, loaded.playMode)
        assertEquals(true, loaded.isPlaying)
        assertEquals(1_000_000L, loaded.timestamp)
    }

    @Test
    fun loadPlaybackStateFallsBackToOrderForUnknownMode() {
        `when`(prefs.getString(eq(StateManager.KEY_PLAY_MODE), anyString()))
            .thenReturn("NOT_A_MODE")
        `when`(prefs.getLong(anyString(), anyLong())).thenReturn(-1L)
        `when`(prefs.getInt(anyString(), anyInt())).thenReturn(0)
        `when`(prefs.getBoolean(anyString(), anyBoolean())).thenReturn(false)

        val loaded = stateManager.loadPlaybackState()

        assertEquals(PlayMode.ORDER, loaded.playMode)
        assertFalse(loaded.isValid())
    }

    @Test
    fun clearPlaybackStateRemovesAllKeysAndApplies() {
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.remove(anyString())).thenReturn(editor)

        stateManager.clearPlaybackState()

        verify(editor).remove(StateManager.KEY_CURRENT_MUSIC_ID)
        verify(editor).remove(StateManager.KEY_CURRENT_POSITION)
        verify(editor).remove(StateManager.KEY_PLAYBACK_PROGRESS)
        verify(editor).remove(StateManager.KEY_PLAY_MODE)
        verify(editor).remove(StateManager.KEY_IS_PLAYING)
        verify(editor).remove(StateManager.KEY_TIMESTAMP)
        verify(editor).apply()
    }
}