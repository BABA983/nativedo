package io.github.baba983.nativedo.topic

import io.github.baba983.nativedo.core.network.DiscourseRefusalException
import io.github.baba983.nativedo.core.network.SessionRecoveryRequiredException
import io.github.baba983.nativedo.core.session.LoginRequiredException
import io.github.baba983.nativedo.core.session.SessionLoggedOutException

/**
 * Why a topic read did not arrive, in the terms the reader can act on. The distinction is the whole
 * point: a challenge and a lost session can be dealt with in the login WebView and the read tried
 * again, a refusal cannot, and an outage is neither.
 */
enum class TopicLoadFailure {
    /** The edge wants a browser challenge solved; the login WebView is the way through. */
    Challenge,

    /** The session is gone, or was never usable; the login WebView is the way back. */
    LoginRequired,

    /** The site answered and said no (403): a permission refusal, not a session problem. */
    Forbidden,

    /** The site asked for less traffic (429). */
    RateLimited,

    /** The request never reached the site, or its answer never arrived. */
    Network,

    Unknown;

    companion object {
        @JvmStatic
        fun of(error: Throwable): TopicLoadFailure = when {
            error is SessionRecoveryRequiredException -> Challenge
            error is LoginRequiredException || error is SessionLoggedOutException -> LoginRequired
            error is DiscourseRefusalException && error.status == 429 -> RateLimited
            error is DiscourseRefusalException -> Forbidden
            error is java.io.IOException -> Network
            else -> Unknown
        }
    }
}