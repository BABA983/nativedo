package io.github.baba983.nativedo.feature.chat.data

import kotlinx.serialization.SerializationException
import retrofit2.HttpException

/**
 * The site has no chat channel API to answer: `GET /chat/api/channels.json` was not found, which is
 * what a site without the chat plugin (or without its API route) answers. It is a property of the
 * site, not a lost session and not an outage, so a screen must neither open the login WebView nor
 * expire the reader's login for it.
 */
class ChatChannelsUnavailableException(
    cause: Throwable? = null,
) : Exception("GET /chat/api/channels.json was not found: the site has no chat channel API", cause)

/**
 * The site answered, but not with channel data: an HTML page from a proxy or a maintenance answer,
 * or a JSON shape this client cannot read. The site was reached and the session was not the problem,
 * so this must not read as a network outage. The cause is the decoder's own exception; the client
 * never keeps or logs the response body.
 */
class ChatChannelsMalformedException(
    cause: Throwable? = null,
) : Exception("GET /chat/api/channels.json answered data this client cannot read", cause)

/**
 * Separate the channel list's failures the reader can act on differently - a site without the chat
 * API, an answer that is not channel data - from the ones the session layer already owns. A
 * challenge, a lost session, a refusal and a rate limit pass through untouched, so the existing
 * session recovery keeps deciding them; only the two the session layer has no meaning for are named
 * here.
 */
internal fun Throwable.classifiedChatChannelFailure(): Throwable =
    when (this) {
        is ChatChannelsUnavailableException, is ChatChannelsMalformedException -> this
        is HttpException ->
            if (code() == 404 || code() == 410) ChatChannelsUnavailableException(this) else this
        is SerializationException -> ChatChannelsMalformedException(this)
        else -> if (cause is SerializationException) ChatChannelsMalformedException(this) else this
    }
