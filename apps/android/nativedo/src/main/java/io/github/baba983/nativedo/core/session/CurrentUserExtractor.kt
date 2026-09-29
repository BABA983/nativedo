package io.github.baba983.nativedo.core.session

import android.webkit.WebView
import io.github.baba983.nativedo.core.network.NetworkJson
import io.github.baba983.nativedo.core.web.decodeJavascriptString
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlin.coroutines.resume

/**
 * Extracts the Discourse current-user identity from a WebView DOM.
 *
 * Priority: `#data-preloaded` currentUser JSON → `meta[name=current-username]`.
 */
internal object CurrentUserExtractor {

    private const val CURRENT_USER_SCRIPT = """
        (function() {
          function normalizeUser(currentUser) {
            if (!currentUser) {
              return null;
            }
            if (typeof currentUser === 'string') {
              try {
                currentUser = JSON.parse(currentUser);
              } catch (error) {
                return null;
              }
            }
            var username = currentUser.username || '';
            if (!username) {
              return null;
            }
            return {
              username: username,
              name: currentUser.name || '',
              avatar_template: currentUser.avatar_template || ''
            };
          }
          var meta = document.querySelector('meta[name="current-username"]');
          var metaUsername = meta && meta.content || '';
          var preloaded = document.querySelector('#data-preloaded');
          var user = null;
          try {
            var payload = JSON.parse(preloaded.getAttribute('data-preloaded') || '{}');
            user = normalizeUser(payload.currentUser);
          } catch (error) {
          }
          if (!user && metaUsername) {
            user = {
              username: metaUsername,
              name: '',
              avatar_template: ''
            };
          }
          if (!user) {
            return '';
          }
          return JSON.stringify(user);
        })();
    """

    @Serializable
    private data class LoginCurrentUserPayload(
        val username: String = "",
        val name: String = "",
        @SerialName("avatar_template")
        val avatarTemplate: String = "",
    ) {
        fun toSessionUserSummary(): SessionUserSummary? {
            val normalizedUsername = username.takeIf { it.isNotBlank() } ?: return null
            return SessionUserSummary(
                username = normalizedUsername,
                displayName = name.takeIf { it.isNotBlank() } ?: normalizedUsername,
                avatarTemplate = avatarTemplate,
            )
        }
    }

    internal fun decodeCurrentUserPayload(rawValue: String?): SessionUserSummary? {
        val payload = decodeJavascriptString(rawValue).trim()
        if (payload.isBlank()) {
            return null
        }
        return runCatching {
            NetworkJson.instance.decodeFromString<LoginCurrentUserPayload>(payload)
                .toSessionUserSummary()
        }.getOrNull()
    }

    internal suspend fun readCurrentUser(webView: WebView): SessionUserSummary? =
        suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript(CURRENT_USER_SCRIPT) { rawValue ->
                if (continuation.isActive) {
                    continuation.resume(decodeCurrentUserPayload(rawValue))
                }
            }
        }
}
