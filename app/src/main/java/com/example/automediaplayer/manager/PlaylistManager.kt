package com.example.automediaplayer.manager

import com.example.automediaplayer.model.MusicData
import com.example.automediaplayer.model.PlayMode
import kotlin.random.Random

/**
 * 播放队列管理器（第 2 周·第三章）。
 *
 * 只负责“队列 + 索引 + 切歌算法”，不持有 MediaPlayer。
 * MusicPlayService 收到上一首/下一首请求后，先调用本类得到新索引，
 * 再按索引播放对应歌曲。
 *
 * 随机播放使用 [playedIndices] 作为“已播历史栈”：
 * 前进时把旧位置压栈，后退时弹栈，因此可以沿原路径回退，而不是重新随机。
 */
class PlaylistManager {

    private var originalPlaylist: List<MusicData> = emptyList()
    private var currentPlaylist: List<MusicData> = emptyList()
    private var currentPosition = -1
    private var currentPlayMode = PlayMode.ORDER
    private val playedIndices = mutableListOf<Int>()

    /** 设置新的播放列表并按 [musicId] 定位；传 0 或找不到时定位到第 0 首。 */
    fun setPlaylist(playlist: List<MusicData>, musicId: Long = 0L) {
        originalPlaylist = playlist.toList()
        val targetId = if (musicId != 0L) musicId else playlist.firstOrNull()?.id
        rebuildQueue(targetId)
        playedIndices.clear()
    }

    /** 按当前模式重建队列，并让 [keepMusicId] 对应的歌曲保持在当前索引。 */
    private fun rebuildQueue(keepMusicId: Long?) {
        currentPlaylist = if (currentPlayMode == PlayMode.SHUFFLE) {
            originalPlaylist.shuffled()
        } else {
            originalPlaylist
        }
        currentPosition = when {
            currentPlaylist.isEmpty() -> -1
            keepMusicId == null -> 0
            else -> currentPlaylist.indexOfFirst { it.id == keepMusicId }.takeIf { it >= 0 } ?: 0
        }
    }

    /** 切换模式时保持当前歌曲不变；随机模式重新洗牌。 */
    fun setPlayMode(mode: PlayMode) {
        if (mode == currentPlayMode) return
        val keepId = getCurrentMusic()?.id
        currentPlayMode = mode
        rebuildQueue(keepId)
        playedIndices.clear()
    }

    fun getPlayMode(): PlayMode = currentPlayMode

    /** 当前队列（随机模式下是洗牌后的顺序）。 */
    fun getPlaylist(): List<MusicData> = currentPlaylist

    /** 原始播放列表：供 MediaBrowser 的 onLoadChildren 稳定地展示全部歌曲。 */
    fun getAllMusic(): List<MusicData> = originalPlaylist

    fun getCurrentMusic(): MusicData? = currentPlaylist.getOrNull(currentPosition)

    fun getCurrentPosition(): Int = currentPosition

    /** 当前歌曲在队列中的索引；无歌曲时为 -1。 */
    fun getCurrentIndex(): Int = currentPosition

    /**
     * 前进一首。
     *
     * @param manual true 表示用户点击“下一首”，false 表示自然播放完成。
     *               单曲循环只在自然播完时原地重播；手动切歌仍按列表前进。
     * @return true 表示索引已更新；false 表示顺序模式已到末尾。
     */
    fun moveToNext(manual: Boolean = true): Boolean {
        if (currentPlaylist.isEmpty()) return false
        val nextPosition = when (currentPlayMode) {
            PlayMode.ORDER -> {
                if (currentPosition < currentPlaylist.size - 1) currentPosition + 1 else -1
            }
            PlayMode.LOOP_ALL -> (currentPosition + 1).coerceAtLeast(0) % currentPlaylist.size
            PlayMode.LOOP_ONE -> {
                if (manual) (currentPosition + 1).coerceAtLeast(0) % currentPlaylist.size
                else currentPosition.coerceAtLeast(0)
            }
            PlayMode.SHUFFLE -> nextShufflePosition()
        }
        if (nextPosition == -1) return false

        if (currentPosition >= 0 && nextPosition != currentPosition) {
            playedIndices.add(currentPosition)
        }
        currentPosition = nextPosition
        return true
    }

    /** 上一首；顺序模式最多退到第 0 首，列表/单曲循环绕回末尾。 */
    fun moveToPrevious(): Boolean {
        if (currentPlaylist.isEmpty()) return false
        val previousPosition = when (currentPlayMode) {
            PlayMode.ORDER -> (currentPosition - 1).coerceAtLeast(0)
            PlayMode.LOOP_ALL, PlayMode.LOOP_ONE ->
                if (currentPosition > 0) currentPosition - 1 else currentPlaylist.size - 1
            PlayMode.SHUFFLE -> previousShufflePosition()
        }
        if (previousPosition == -1) return false
        currentPosition = previousPosition
        return true
    }

    /** 随机前进：候选集排除当前曲和已经播过的索引。 */
    private fun nextShufflePosition(): Int {
        if (currentPlaylist.isEmpty()) return -1
        if (currentPosition < 0) return Random.nextInt(currentPlaylist.size)

        if (playedIndices.size >= currentPlaylist.size) {
            playedIndices.clear()
        }
        val available = currentPlaylist.indices.filter {
            it != currentPosition && !playedIndices.contains(it)
        }
        if (available.isEmpty()) {
            val fallback = currentPlaylist.indices.filter { it != currentPosition }
            // 队列中只剩当前这一首时，随机模式也应重播当前曲，而不是停止。
            return if (fallback.isEmpty()) currentPosition else fallback.random()
        }
        return available.random()
    }

    /** 随机后退：优先弹出历史栈顶，栈空时随机选一首非当前曲。 */
    private fun previousShufflePosition(): Int {
        if (playedIndices.isNotEmpty()) {
            return playedIndices.removeAt(playedIndices.size - 1)
        }
        val candidates = currentPlaylist.indices.filter { it != currentPosition }
        return if (candidates.isEmpty()) currentPosition else candidates.random()
    }

    /** 跳转到指定位置；无效位置返回 null。 */
    fun moveToPosition(position: Int): MusicData? {
        if (position !in currentPlaylist.indices) return null
        currentPosition = position
        if (currentPlayMode == PlayMode.SHUFFLE) {
            playedIndices.clear()
        }
        return currentPlaylist[position]
    }
}
