package com.example.automediaplayer.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.util.Size
import android.widget.ImageView
import java.util.concurrent.Executors

/**
 * 专辑封面加载器（第 2 周 UI 扩展）。
 *
 * 通过 MediaStore 的 albumart Uri 读取缩略图；读取失败（模拟器无封面、
 * 歌曲无内嵌图片）时回退到默认封面。用 tag 校验避免 RecyclerView/ImageView
 * 复用导致图片错位。
 */
object AlbumArtLoader {

    private const val ALBUM_ART_BASE_URI = "content://media/external/audio/albumart"
    private val executor = Executors.newFixedThreadPool(2)

    fun load(
        context: Context,
        albumId: Long,
        imageView: ImageView,
        fallbackRes: Int
    ) {
        val tag = "album_$albumId"
        imageView.tag = tag
        imageView.setImageResource(fallbackRes)
        if (albumId <= 0L) return

        val uri = ContentUris.withAppendedId(Uri.parse(ALBUM_ART_BASE_URI), albumId)
        val resolver = context.applicationContext.contentResolver
        executor.execute {
            val bitmap = runCatching {
                resolver.loadThumbnail(uri, Size(480, 480), null)
            }.getOrNull()
            imageView.post {
                if (imageView.tag == tag) {
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                    } else {
                        imageView.setImageResource(fallbackRes)
                    }
                }
            }
        }
    }
}
