package com.example.automediaplayer.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 扩展练习：MediaBrowser 连接安全策略单元测试。
 *
 * 不只验证“自己的包能连”，还覆盖伪造包名、不同 UID 和空包名三种拒绝路径。
 */
class MediaBrowserAccessPolicyTest {

    private val ownerPackage = "com.example.automediaplayer"
    private val ownerUid = 10234

    @Test
    fun ownPackageAndUidAreAccepted() {
        assertTrue(
            MediaBrowserAccessPolicy.isTrustedClient(
                callingPackage = ownerPackage,
                clientUid = ownerUid,
                ownerPackage = ownerPackage,
                ownerUid = ownerUid
            )
        )
    }

    @Test
    fun foreignPackageIsRejectedEvenWhenUidMatches() {
        assertFalse(
            MediaBrowserAccessPolicy.isTrustedClient(
                callingPackage = "com.example.malicious",
                clientUid = ownerUid,
                ownerPackage = ownerPackage,
                ownerUid = ownerUid
            )
        )
    }

    @Test
    fun samePackageWithDifferentUidIsRejected() {
        assertFalse(
            MediaBrowserAccessPolicy.isTrustedClient(
                callingPackage = ownerPackage,
                clientUid = ownerUid + 1,
                ownerPackage = ownerPackage,
                ownerUid = ownerUid
            )
        )
    }

    @Test
    fun blankCallingPackageIsRejected() {
        assertFalse(
            MediaBrowserAccessPolicy.isTrustedClient(
                callingPackage = "",
                clientUid = ownerUid,
                ownerPackage = ownerPackage,
                ownerUid = ownerUid
            )
        )
        assertFalse(
            MediaBrowserAccessPolicy.isTrustedClient(
                callingPackage = null,
                clientUid = ownerUid,
                ownerPackage = ownerPackage,
                ownerUid = ownerUid
            )
        )
    }
}
