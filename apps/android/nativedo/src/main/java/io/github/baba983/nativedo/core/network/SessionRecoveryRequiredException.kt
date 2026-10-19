package io.github.baba983.nativedo.core.network

import java.io.IOException

/**
 * The server response could not prove that the session is logged out.
 *
 * Cloudflare challenges, temporary upstream failures, and an HTML response
 * where JSON was expected all belong here. Callers must keep the cookies and
 * let the WebView perform recovery.
 */
class SessionRecoveryRequiredException(
    cause: Throwable? = null,
    /**
     * True only when the response itself is a Cloudflare challenge. A malformed
     * or unreadable body is recovery-worthy too, but replaying it cannot help.
     */
    val challenge: Boolean = false,
) : IOException("登录状态暂时无法验证，请在登录页面完成网站验证后重试", cause)
