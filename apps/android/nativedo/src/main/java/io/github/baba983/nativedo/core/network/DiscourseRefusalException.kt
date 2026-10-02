package io.github.baba983.nativedo.core.network

/**
 * A refusal the site itself sent, carrying the status that delivered it.
 *
 * It is an [java.io.IOException], so a caller that only cares that the request failed keeps
 * working; it is typed so a screen can tell a permission refusal from a logout, a challenge or a
 * plain outage instead of reading the message back.
 */
class DiscourseRefusalException(
    val status: Int,
    message: String,
    cause: Throwable? = null,
) : java.io.IOException(message, cause)