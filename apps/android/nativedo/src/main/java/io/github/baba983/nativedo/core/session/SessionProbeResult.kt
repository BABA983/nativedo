package io.github.baba983.nativedo.core.session

/**
 * The answer to a non-destructive check of the native jar's `_t`, used when the jar disagrees with
 * the WebView's copy and something has to decide which one to write.
 *
 * The check is deliberately not a session transition: [SessionCoordinator] and its api client own
 * logout evidence, and a check made only to seed the in-app browser must not clear anything.
 */
enum class SessionProbeResult {
    /** The server answered with the reader's identity for the native jar's token. */
    Accepted,

    /** The server answered with explicit logout evidence for the native jar's token. */
    Rejected,

    /**
     * No usable answer: a challenge, a transient failure, a fence that dropped a negative answer,
     * or a body that carried neither an identity nor logout evidence.
     */
    Unavailable,
}
