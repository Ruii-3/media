package com.example.automediaplayer

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.media.browse.MediaBrowser
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.example.automediaplayer.service.MusicPlayService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 扩展练习：MediaBrowser 连接安全测试。
 *
 * 外部包名/UID 的拒绝逻辑由 JVM 测试
 * [com.example.automediaplayer.security.MediaBrowserAccessPolicyTest] 覆盖；
 * 本测试在 Android 环境验证 Manifest 防护与本应用可信连接确实可用。
 */
@RunWith(AndroidJUnit4::class)
class MediaBrowserSecurityTest {

    private companion object {
        const val BIND_PERMISSION =
            "com.example.automediaplayer.permission.BIND_MEDIA_BROWSER"
        const val CONNECT_TIMEOUT_SECONDS = 15L
    }

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    )

    @Test
    fun mediaBrowserServiceRequiresSignaturePermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val packageManager = context.packageManager
        val component = ComponentName(context, MusicPlayService::class.java)
        val serviceInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getServiceInfo(
                component,
                PackageManager.ComponentInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getServiceInfo(component, 0)
        }

        assertTrue("MediaBrowserService 必须声明 exported=true", serviceInfo.exported)
        assertEquals(BIND_PERMISSION, serviceInfo.permission)

        val permissionInfo = packageManager.getPermissionInfo(BIND_PERMISSION, 0)
        assertEquals(
            PermissionInfo.PROTECTION_SIGNATURE,
            permissionInfo.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
        )
    }

    @Test
    fun trustedAppCanConnectAndObtainRoot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val component = ComponentName(context, MusicPlayService::class.java)
        val probe = ConnectionProbe()
        var browser: MediaBrowser? = null

        try {
            // MediaBrowser 构造函数会创建 Handler，必须在有 Looper 的主线程执行。
            instrumentation.runOnMainSync {
                browser = MediaBrowser(context, component, probe, null).also { it.connect() }
            }
            assertTrue(
                "可信应用未在超时前连接 MediaBrowserService",
                probe.connected.await(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            )
            assertTrue("MediaBrowser 连接被拒绝", probe.connectedSuccessfully)
            assertNotNull("连接成功后 MediaBrowser.root 不应为空", browser?.root)
        } finally {
            instrumentation.runOnMainSync {
                browser?.disconnect()
            }
        }
    }

    private class ConnectionProbe : MediaBrowser.ConnectionCallback() {
        val connected = CountDownLatch(1)
        @Volatile
        var connectedSuccessfully = false

        override fun onConnected() {
            connectedSuccessfully = true
            connected.countDown()
        }

        override fun onConnectionFailed() {
            connected.countDown()
        }
    }
}
