package com.example.automediaplayer.util

import android.content.Context
import android.content.ContentUris
import android.provider.MediaStore
import android.util.Log
import com.example.automediaplayer.model.MusicData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 音乐扫描器（第 1 周·第二章）。
 *
 * 通过 ContentResolver 查询系统 MediaStore，将音频元数据封装为 [MusicData]。
 * 查询在 Dispatchers.IO 执行，避免在主线程访问媒体数据库造成卡顿或 ANR。
 */
object MusicScanner {

    private const val TAG = "MusicScanner"
    private const val MIN_MUSIC_DURATION_MS = 45_000L

    /**
     * 扫描所有外部存储卷（含 U 盘）中的音乐。
     *
     * 使用 VOLUME_EXTERNAL 而不是 EXTERNAL_CONTENT_URI，可以同时覆盖
     * 内置存储与挂载的 USB 存储卷，满足车机插 U 盘后扫描列表的场景。
     */
    suspend fun scanMusicFiles(context: Context): List<MusicData> = withContext(Dispatchers.IO) {
        val musicList = mutableListOf<MusicData>()
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )

        // 只保留音乐，并过滤铃声/提示音；45 秒阈值来自教材第二章。
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
            "${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf(MIN_MUSIC_DURATION_MS.toString())
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        Log.d(TAG, "query uri=$collection, selection=$selection, args=${selectionArgs.joinToString()}")
        val mediaCursor = context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )
        Log.d(TAG, "query returned ${mediaCursor?.count ?: -1} rows")
        mediaCursor?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                musicList += MusicData(
                    id = id,
                    title = cursor.getString(titleColumn) ?: "未知标题",
                    artist = cursor.getString(artistColumn) ?: "未知艺术家",
                    album = cursor.getString(albumColumn) ?: "未知专辑",
                    duration = cursor.getLong(durationColumn),
                    uri = ContentUris.withAppendedId(collection, id),
                    // 分区存储下不依赖 DATA 绝对路径；播放统一使用 content:// Uri。
                    filePath = "",
                    albumId = cursor.getLong(albumIdColumn)
                )
            }
        }

        Log.d(TAG, "scanMusicFiles: found ${musicList.size} tracks")
        musicList
    }
}
