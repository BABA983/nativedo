package io.github.baba983.nativedo.sessionui

import android.content.Context
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionExpiredReason
import io.github.baba983.nativedo.topic.TopicLoadFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The explicit state a tab root shows in place of its own content, in the terms the reader can act
 * on: signing in, solving the site's challenge, or simply reading again. [Restoring] is the only one
 * with nothing to tap - the answer is still on its way.
 */
enum class SessionUiState { Restoring, SignIn, Expired, Challenge, Retry, Forbidden }

/** What a tap on the state does. */
enum class SessionUiAction { OpenLogin, OpenVerification, Retry }

/**
 * The session-state glue the notifications, profile and settings tab roots share.
 *
 * <p>The three screens draw with different lists, so this helper carries the parts that must not
 * drift between them: which state a session or a failed read maps to, the words for it, what its tap
 * does, and the observation of [io.github.baba983.nativedo.core.session.SessionCoordinator.sessionState]
 * that reaches a tab that is already open. The screen keeps its own widgets; it only asks here what
 * the state is and how to say it.</p>
 *
 * <p>Read failures are classified by [TopicLoadFailure], the same rule the topic screen uses, so an
 * outage or a plain 403 is never mislabelled as a logout.</p>
 */
object SessionUi {

    /** The state [state] should show, or {@code null} when the account has content of its own. */
    @JvmStatic
    fun stateFor(state: AppSessionState?): SessionUiState? = when (state) {
        is AppSessionState.Authenticated -> null
        is AppSessionState.Restoring, AppSessionState.Unknown, null -> SessionUiState.Restoring
        AppSessionState.Anonymous -> SessionUiState.SignIn
        is AppSessionState.Expired -> when (state.reason) {
            SessionExpiredReason.ChallengeRequired -> SessionUiState.Challenge
            SessionExpiredReason.RecoveryFailed -> SessionUiState.Retry
            SessionExpiredReason.ServerLoggedOut,
            SessionExpiredReason.CookieMissing,
            -> SessionUiState.Expired
        }
    }

    /** The state a read that failed while the session was authenticated should show. */
    @JvmStatic
    fun stateForLoadFailure(error: Throwable): SessionUiState = when (TopicLoadFailure.of(error)) {
        TopicLoadFailure.Challenge -> SessionUiState.Challenge
        TopicLoadFailure.LoginRequired -> SessionUiState.Expired
        TopicLoadFailure.Forbidden -> SessionUiState.Forbidden
        TopicLoadFailure.RateLimited, TopicLoadFailure.Network, TopicLoadFailure.Unknown -> SessionUiState.Retry
    }

    @JvmStatic
    fun action(state: SessionUiState): SessionUiAction = when (state) {
        SessionUiState.Challenge -> SessionUiAction.OpenVerification
        SessionUiState.SignIn, SessionUiState.Expired -> SessionUiAction.OpenLogin
        SessionUiState.Retry, SessionUiState.Forbidden, SessionUiState.Restoring -> SessionUiAction.Retry
    }

    /** [SessionUiState.Restoring] is the one state with nothing to tap. */
    @JvmStatic
    fun hasAction(state: SessionUiState): Boolean = state != SessionUiState.Restoring

    @JvmStatic
    fun text(context: Context, state: SessionUiState): CharSequence = context.getString(
        when (state) {
            SessionUiState.Restoring -> R.string.session_state_restoring
            SessionUiState.SignIn -> R.string.session_state_sign_in
            SessionUiState.Expired -> R.string.session_state_expired
            SessionUiState.Challenge -> R.string.session_state_challenge
            SessionUiState.Retry -> R.string.session_state_retry
            SessionUiState.Forbidden -> R.string.session_state_forbidden
        },
    )

    /**
     * Watches the coordinator's [io.github.baba983.nativedo.core.session.SessionCoordinator.sessionState]
     * and reports every value, the current one first. The returned job *is* the observation: it is
     * the scope's own [SupervisorJob], so cancelling it cancels the collector, and there is no
     * parent scope left behind holding a stranded job. A screen cancels it when its views go away
     * and starts a fresh observation when they come back.
     */
    @JvmStatic
    fun observe(container: AppContainer, onState: (AppSessionState) -> Unit): Job {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Main.immediate)
        scope.launch {
            container.sessionCoordinator.sessionState.collect { onState(it) }
        }
        return job
    }

    /** Cancels an observation from Java, where the no-argument [Job.cancel] extension is not visible. */
    @JvmStatic
    fun cancel(job: Job?) {
        job?.cancel()
    }

    /**
     * The identity a session stands for: the same account re-authenticating is the same identity,
     * a different account - or a lost session - is a different one. A screen that sees this change
     * drops what it read for the old identity rather than letting it stay on the screen.
     */
    @JvmStatic
    fun identity(state: AppSessionState?): String = when (state) {
        is AppSessionState.Authenticated -> "auth:" + state.username
        is AppSessionState.Expired -> "expired:" + state.reason.name
        is AppSessionState.Restoring -> "restoring"
        AppSessionState.Anonymous -> "anonymous"
        AppSessionState.Unknown, null -> "unknown"
    }
}
