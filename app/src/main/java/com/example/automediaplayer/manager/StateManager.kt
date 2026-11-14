package com.example.automediaplayer.manager

import android.content.Context
import com.example.automediaplayer.model.MyPlaybackState
import com.example.automediaplayer.model.PlayMode

/**
 * 播放状态持久化封装（第 4 周·第五章 4.2）。
 *
 * 使用 SharedPreferences 保存最小状态集合，供 MusicPlayService 在启动时
 * 恢复、在播放/暂停/拖动进度/切模式后延迟保存，并每 5 秒做一次保底保存。
 */
class StateManager(context: Context) {

    private val sharedPrefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        const val PREFS_NAME = "AutoMediaPlayerPrefs"
        const val KEY_CURRENT_MUSIC_ID = "current_music_id"
        const val KEY_CURRENT_POSITION = "current_position"
        const val KEY_PLAYBACK_PROGRESS = "playback_progress"
        const val KEY_PLAY_MODE = "play_mode"
        const val KEY_IS_PLAYING = "is_playing"
        const val KEY_TIMESTAMP = "timestamp"

        private const val MAX_AGE_HOURS = 24

        /**
         * 纯函数形式的有效期判断，便于单元测试；时间戳缺失或超过 24 小时即过期。
         */
        fun isExpired(
            timestamp: Long,
            now: Long = System.currentTimeMillis(),
            maxAgeHours: Int = MAX_AGE_HOURS
        ): Boolean {
            if (timestamp <= 0L) return true
            val maxAgeMs = maxAgeHours.coerceAtLeast(1) * 60L * 60L * 1000L
            return now - timestamp > maxAgeMs
        }
    }

    /** apply() 异步落盘，避免在主线程上做同步 IO。 */
    fun savePlaybackState(state: MyPlaybackState) {
        sharedPrefs.edit()
            .putLong(KEY_CURRENT_MUSIC_ID, state.currentMusicId)
            .putInt(KEY_CURRENT_POSITION, state.currentPosition)
            .putInt(KEY_PLAYBACK_PROGRESS, state.playbackProgress)
            .putString(KEY_PLAY_MODE, state.playMode.name)
            .putBoolean(KEY_IS_PLAYING, state.isPlaying)
            .putLong(KEY_TIMESTAMP, state.timestamp)
            .apply()
    }

    fun loadPlaybackState(): MyPlaybackState {
        val modeName = sharedPrefs.getString(KEY_PLAY_MODE, PlayMode.ORDER.name)
            ?: PlayMode.ORDER.name
        val mode = runCatching { PlayMode.valueOf(modeName) }.getOrDefault(PlayMode.ORDER)
        return MyPlaybackState(
            currentMusicId = sharedPrefs.getLong(KEY_CURRENT_MUSIC_ID, -1L),
            currentPosition = sharedPrefs.getInt(KEY_CURRENT_POSITION, 0),
            playbackProgress = sharedPrefs.getInt(KEY_PLAYBACK_PROGRESS, 0),
            playMode = mode,
            isPlaying = sharedPrefs.getBoolean(KEY_IS_PLAYING, false),
            timestamp = sharedPrefs.getLong(KEY_TIMESTAMP, 0L)
        )
    }

    /** 返回有效且未过期的状态；无效、过期或没有保存过状态时返回 null。 */
    fun loadValidPlaybackState(): MyPlaybackState? {
        val state = loadPlaybackState()
        if (!state.isValid()) return null
        if (isExpired(state.timestamp)) return null
        return state
    }

    fun isStateExpired(maxAgeHours: Int = MAX_AGE_HOURS): Boolean {
        return isExpired(loadPlaybackState().timestamp, maxAgeHours = maxAgeHours)
    }

    fun hasValidState(): Boolean = loadValidPlaybackState() != null

    fun clearPlaybackState() {
        sharedPrefs.edit()
            .remove(KEY_CURRENT_MUSIC_ID)
            .remove(KEY_CURRENT_POSITION)
            .remove(KEY_PLAYBACK_PROGRESS)
            .remove(KEY_PLAY_MODE)
            .remove(KEY_IS_PLAYING)
            .remove(KEY_TIMESTAMP)
            .apply()
    }
}
