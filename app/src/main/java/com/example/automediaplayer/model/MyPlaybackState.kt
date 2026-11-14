package com.example.automediaplayer.model

/**
 * 可恢复播放状态（第 4 周·第五章 4.2）。
 *
 * 只保存恢复所需的最小数据集：歌曲 id 用于反查歌曲，position 用于歌曲
 * 不在队列中时兜底，progress 用于续播，playMode 用于保持切歌习惯。
 * 封面和完整歌单都不写入 SharedPreferences，避免存储体积过大。
 */
data class MyPlaybackState(
    val currentMusicId: Long = -1L,
    val currentPosition: Int = 0,
    val playbackProgress: Int = 0,
    val playMode: PlayMode = PlayMode.ORDER,
    val isPlaying: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    /** MediaStore 歌曲 id 必须为正数，-1 表示没有可恢复的歌曲。 */
    fun isValid(): Boolean = currentMusicId > 0L
}
