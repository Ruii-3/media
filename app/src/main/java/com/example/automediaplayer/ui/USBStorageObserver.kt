package com.example.automediaplayer.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Environment
import android.os.storage.StorageManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File

/** USBStorageObserver 与业务层之间只暴露“已连接 / 已断开”两个语义事件。 */
interface USBStatusCallback {
    fun onUSBConnected()
    fun onUSBDisconnected()
}

/**
 * 第 5 周：USB 插拔三层结构中的广播监听层与事件归一化层。
 *
 * 系统会分别发送 MOUNTED / UNMOUNTED / REMOVED，本类统一成两个回调，
 * 交给 MusicPlayService 决定重新扫描还是停止播放并清空列表。
 */
class USBStorageObserver(
    context: Context,
    private val callback: USBStatusCallback
) {

    private companion object {
        const val TAG = "USBStorageObserver"
    }

    private val appContext = context.applicationContext
    private val storageManager = appContext.getSystemService(StorageManager::class.java)
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!isExternalStorageEvent(intent)) return
            val path = intent.data?.path.orEmpty()
            when (intent.action) {
                Intent.ACTION_MEDIA_MOUNTED -> {
                    Log.i(TAG, "USB mounted: $path")
                    callback.onUSBConnected()
                }
                Intent.ACTION_MEDIA_UNMOUNTED,
                Intent.ACTION_MEDIA_REMOVED,
                Intent.ACTION_MEDIA_BAD_REMOVAL -> {
                    Log.i(TAG, "USB disconnected: ${intent.action} $path")
                    callback.onUSBDisconnected()
                }
            }
        }
    }

    fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            // 存储广播的 data 是 file://...，不加 scheme 会收不到。
            addDataScheme("file")
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        registered = true
        Log.i(TAG, "USB receiver registered")
    }

    fun stop() {
        if (!registered) return
        runCatching { appContext.unregisterReceiver(receiver) }
            .onFailure { Log.w(TAG, "unregisterReceiver failed", it) }
        registered = false
        Log.i(TAG, "USB receiver unregistered")
    }

    /**
     * 只接受外部存储卷。主存储 /storage/emulated/0 的挂载广播很多，
     * 若不排除会在开机或应用启动时误触发“USB 已连接”。
     */
    private fun isExternalStorageEvent(intent: Intent): Boolean {
        val path = intent.data?.path ?: return false
        if (path.startsWith("/storage/emulated/")) return false
        if (path == Environment.getExternalStorageDirectory().absolutePath) return false

        val volume = runCatching { storageManager.getStorageVolume(File(path)) }.getOrNull()
        if (volume != null) return volume.isRemovable

        // 模拟器可以用 /storage/XXXX-XXXX 这类假路径注入广播，便于课堂演示。
        return path.startsWith("/storage/") || path.startsWith("/mnt/media_rw/")
    }
}
