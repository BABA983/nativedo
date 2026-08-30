package io.github.baba983.nativedo.core.session

class SessionLoggedOutException(
    cause: Throwable? = null,
) : RuntimeException(cause)


/** The request requires authentication, but no authenticated session was active. */
class LoginRequiredException(
    cause: Throwable? = null,
) : RuntimeException("Login required", cause)
