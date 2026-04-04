package io.github.baba983.nativedo.core.network

import java.io.IOException

class SessionRecoveryRequiredException(
    cause: Throwable? = null,
) : IOException("Session recovery required", cause)

fun Throwable.isSessionRecoveryRequired(): Boolean = this is SessionRecoveryRequiredException
