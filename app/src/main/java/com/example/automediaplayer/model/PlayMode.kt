package com.example.automediaplayer.model

/**
 * 播放模式（第 2 周·第三章）。
 *
 * 四种模式的切歌差异：
 * - [ORDER]    顺序播放：下一首只向后走，最后一首播完停止；
 * - [LOOP_ALL] 列表循环：最后一首的下一首回到第一首；
 * - [LOOP_ONE] 单曲循环：自然播完重播当前曲，手动切歌仍换歌；
 * - [SHUFFLE]  随机播放：从未播索引中随机挑选，上一首按历史回退。
 */
enum class PlayMode {
    ORDER,
    LOOP_ALL,
    LOOP_ONE,
    SHUFFLE;

    /** 主界面模式按钮的循环顺序，四种模式全部可到达。 */
    fun nextMode(): PlayMode = when (this) {
        ORDER -> LOOP_ALL
        LOOP_ALL -> LOOP_ONE
        LOOP_ONE -> SHUFFLE
        SHUFFLE -> ORDER
    }
}
