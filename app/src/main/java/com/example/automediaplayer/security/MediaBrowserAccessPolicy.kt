package com.example.automediaplayer.security

/**
 * MediaBrowserService.onGetRoot 的进程内访问策略。
 *
 * Manifest 中的签名级权限负责在 Binder 建立前阻止非本签名应用绑定；
 * 本策略再加一层纵深防御，要求调用包与调用 UID 同时等于服务所属应用。
 *
 * 该类保持纯 Kotlin，不依赖 Android 框架，便于在 JVM 上验证拒绝路径。
 */
object MediaBrowserAccessPolicy {

    fun isTrustedClient(
        callingPackage: String?,
        clientUid: Int,
        ownerPackage: String,
        ownerUid: Int
    ): Boolean {
        return !callingPackage.isNullOrBlank() &&
            callingPackage == ownerPackage &&
            clientUid == ownerUid
    }
}
