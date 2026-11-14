package com.example.automediaplayer.manager

import com.example.automediaplayer.model.MyPlaybackState
import com.example.automediaplayer.model.PlayMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 第 4 周状态持久化纯逻辑测试，不依赖设备。 */
class StateManagerTest {

    private val now = 1_700_000_000_000L

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
}
