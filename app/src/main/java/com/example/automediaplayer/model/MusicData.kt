package com.example.automediaplayer.model

import android.net.Uri
import java.util.Locale

/**
 * 音乐元数据模型（第 1 周·第二章）。
 *
 * 由 [com.example.automediaplayer.util.MusicScanner] 从 MediaStore 构造，
 * 供列表展示，并通过 [uri] 交给 MediaPlayer 播放。
 */
data class MusicData(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val filePath: String,
    /** MediaStore 专辑 id，用于加载专辑封面；0 表示未知。 */
    val albumId: Long = 0L
) {
    /** 将毫秒时长格式化为 mm:ss，例如 210000 -> 03:30。 */
    fun getFormattedDuration(): String {
        val totalSeconds = duration.coerceAtLeast(0L) / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
