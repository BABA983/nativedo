package io.github.baba983.nativedo.auth.device

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.core.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.browser.Browser
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme

/**
 * The experimental entry for Discourse's User API Key device authorization: it asks linux.do for
 * a code, hands the approval page to the app's own in-app browser (the jar's session is already
 * signed in there), polls in the background, and shows what came back.
 *
 * The screen deliberately does not touch the cookie session: the stored key is shown as what it
 * is - a separate, revocable credential - and 设置 -> 账号 keeps opening the normal WebView login.
 */
class DeviceLoginFragment : BaseFragment() {

    private val fragmentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var container: AppContainer
    private lateinit var controller: DeviceLoginController
    private var state: DeviceLoginController.State = DeviceLoginController.State.Idle

    private lateinit var statusView: TextView
    private lateinit var codeView: TextView
    private lateinit var openButton: Button
    private lateinit var retryButton: Button
    private lateinit var auditButton: Button
    private lateinit var auditView: TextView
    private lateinit var revokeButton: Button

    override fun createView(context: Context): View {
        actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back)
        actionBar.setTitle("设备码登录（实验）")
        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                if (id == -1) {
                    finishFragment()
                }
            }
        })

        container = NativeDo.container(context)
        controller = DeviceLoginController(
            api = container.deviceAuthApi,
            keyStore = container.deviceKeyStore,
            loginKey = container.deviceLoginKey,
            clientId = container.deviceClientId,
            scope = fragmentScope,
        )

        val root = FrameLayout(context).apply {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(24))
        }
        root.addView(
            ScrollView(context).apply { addView(column) },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        statusView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
            setLineSpacing(dp(4).toFloat(), 1f)
        }
        column.addView(statusView, matchWidth())

        codeView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText))
            visibility = View.GONE
            setPadding(0, dp(14), 0, dp(4))
        }
        column.addView(codeView, matchWidth())

        openButton = themedButton(context, "在站内浏览器中打开授权页").apply {
            visibility = View.GONE
            setOnClickListener { openInBrowser() }
        }
        column.addView(openButton, matchWidthWithTop(dp(16)))

        retryButton = themedButton(context, "重新申请授权码").apply {
            visibility = View.GONE
            setOnClickListener { controller.startLogin() }
        }
        column.addView(retryButton, matchWidthWithTop(dp(10)))

        column.addView(
            TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
                text = "说明：这个流程拿到的是 User API Key（请求头凭证），不是浏览器的 cookie 会话；" +
                    "本版本不会用它替换当前登录，密钥只保存在本机（AndroidKeyStore 加密），可在此撤销。"
                setLineSpacing(dp(3).toFloat(), 1f)
            },
            matchWidthWithTop(dp(18)),
        )

        column.addView(
            TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
                text = "调试：逐项探测 linux.do 接受的 scope（会创建多个未批准的授权码，10 分钟后自行失效）。"
                setLineSpacing(dp(3).toFloat(), 1f)
            },
            matchWidthWithTop(dp(18)),
        )

        auditButton = themedButton(context, "探测可用 scope（调试）").apply {
            setOnClickListener { controller.probeScopes() }
        }
        column.addView(auditButton, matchWidthWithTop(dp(6)))

        auditView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(Typeface.MONOSPACE)
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
            setLineSpacing(dp(2).toFloat(), 1f)
            visibility = View.GONE
        }
        column.addView(auditView, matchWidthWithTop(dp(8)))

        revokeButton = themedButton(context, "撤销本机保存的设备密钥").apply {
            visibility = View.GONE
            setOnClickListener { controller.revokeStoredKey() }
        }
        column.addView(revokeButton, matchWidthWithTop(dp(18)))

        fragmentView = root

        fragmentScope.launch {
            controller.state.collect { newState ->
                state = newState
                render(newState)
            }
        }
        fragmentScope.launch {
            controller.scopeAudit.collect { renderAudit() }
        }
        fragmentScope.launch {
            controller.auditRunning.collect { auditButton.isEnabled = !it }
        }
        fragmentScope.launch {
            controller.hasStoredKey.collect { hasKey ->
                revokeButton.visibility = if (hasKey) View.VISIBLE else View.GONE
            }
        }
        controller.startLogin()
        return root
    }

    private fun render(newState: DeviceLoginController.State) {
        openButton.visibility = View.GONE
        retryButton.visibility = View.GONE
        codeView.visibility = View.GONE
        when (newState) {
            DeviceLoginController.State.Idle -> {
                statusView.text = ""
            }
            DeviceLoginController.State.Creating -> {
                statusView.text = "正在向 linux.do 申请授权码…"
            }
            is DeviceLoginController.State.Waiting -> {
                statusView.text = "在下方按钮打开的站内浏览器里确认这个授权码（10 分钟内有效）："
                codeView.text = newState.userCode
                codeView.visibility = View.VISIBLE
                openButton.visibility = View.VISIBLE
            }
            is DeviceLoginController.State.Authorized -> {
                val who = newState.username?.let { "@$it" } ?: "身份未返回"
                val expires = newState.expiresAt?.let { "，有效期至 $it" }.orEmpty()
                statusView.text = "授权成功：$who（密钥存储：${newState.keyStorage}$expires）。\n" +
                    "已用这把密钥请求过 /session/current.json。当前 App 仍使用 cookie 会话。"
                retryButton.visibility = View.VISIBLE
            }
            DeviceLoginController.State.Denied -> {
                statusView.text = "授权已在浏览器中被拒绝。"
                retryButton.visibility = View.VISIBLE
            }
            DeviceLoginController.State.Expired -> {
                statusView.text = "授权码已过期。"
                retryButton.visibility = View.VISIBLE
            }
            is DeviceLoginController.State.Failed -> {
                statusView.text = newState.reason
                retryButton.visibility = View.VISIBLE
            }
        }
    }

    private fun renderAudit() {
        val results = controller.scopeAudit.value
        if (results.isEmpty()) {
            auditView.visibility = View.GONE
            return
        }
        auditView.visibility = View.VISIBLE
        auditView.text = results.joinToString("\n") { result ->
            buildString {
                append(result.scopes)
                append(" → ")
                if (result.status == 200) {
                    append("200 通过（user_code ").append(result.userCode).append("）")
                } else {
                    append(result.status)
                    result.errorType?.let { append(" ").append(it) }
                    result.message?.takeIf(String::isNotBlank)?.let { append("：").append(it.take(80)) }
                }
            }
        }
    }

    private fun openInBrowser() {
        val waiting = state as? DeviceLoginController.State.Waiting ?: return
        Browser.openUrl(context, waiting.verificationUri)
    }

    private fun themedButton(context: Context, label: String): Button =
        Button(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            isAllCaps = false
            setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText))
            setBackgroundColor(Theme.getColor(Theme.key_featuredStickers_addButton))
        }

    private fun matchWidth(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun matchWidthWithTop(top: Int): LinearLayout.LayoutParams =
        matchWidth().apply { topMargin = top }

    private fun dp(value: Int): Int = AndroidUtilities.dp(value.toFloat())

    override fun onBackPressed(invoked: Boolean): Boolean {
        controller.cancel()
        return true
    }

    override fun onFragmentDestroy() {
        controller.cancel()
        fragmentScope.cancel()
        super.onFragmentDestroy()
    }
}
