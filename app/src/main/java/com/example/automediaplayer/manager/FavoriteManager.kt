package com.example.automediaplayer.manager

import android.content.Context

/**
 * 收藏管理器（第 2 周 UI 扩展）。
 *
 * 使用 SharedPreferences 的 StringSet 持久化歌曲 id，应用重启后仍然有效。
 * 只保存 id，不复制歌曲文件；列表刷新后按 id 重新匹配。
 */
class FavoriteManager(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getFavoriteIds(): MutableSet<Long> {
        val raw = preferences.getStringSet(KEY_FAVORITE_IDS, emptySet()).orEmpty()
        return raw.mapNotNull { it.toLongOrNull() }.toMutableSet()
    }

    fun isFavorite(musicId: Long): Boolean = getFavoriteIds().contains(musicId)

    /** 切换收藏状态，返回切换后的结果：true 表示已收藏。 */
    fun toggleFavorite(musicId: Long): Boolean {
        val ids = getFavoriteIds()
        val nowFavorite = if (ids.contains(musicId)) {
            ids.remove(musicId)
            false
        } else {
            ids.add(musicId)
            true
        }
        // 转成不可变副本再写入，避免 SharedPreferences 持有可变 Set 的引用。
        preferences.edit()
            .putStringSet(KEY_FAVORITE_IDS, ids.map { it.toString() }.toSet())
            .apply()
        return nowFavorite
    }

    private companion object {
        const val PREFS_NAME = "auto_media_player_favorites"
        const val KEY_FAVORITE_IDS = "favorite_music_ids"
    }
}
