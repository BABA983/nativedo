package io.github.baba983.nativedo.core.network

import java.io.IOException

/**
 * The exchange was abandoned before its body was read because the shared jar's `_t` rotated while
 * the request was in flight.
 *
 * <p>[SessionCookieInterceptor] settles the race: a response that went out under an older token than
 * the jar holds now cannot be read as a logout, a challenge or a refusal - the newer rotation is the
 * live one, and a negative answer built on the older one is meaningless. It is deliberately its own
 * [IOException] rather than a bare one: a caller that falls back to an offline cache on a transport
 * outage must not read this as an outage, because the site was reached and the session simply moved
 * on. A plain read that lost the race still returns its payload; only a negative answer is dropped
 * here, and the caller asks again under the newer session.</p>
 */
class SessionChangedException : IOException("Session changed while request was in flight")
