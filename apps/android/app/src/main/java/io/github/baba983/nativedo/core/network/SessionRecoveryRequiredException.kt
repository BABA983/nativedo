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
) : IOException("Session recovery required", cause)
