# Telegram Workflow Map: Login Workflow

1. Upstream scope:
   - Telegram reference checked: `org/telegram/ui/WebviewActivity.java` at `d2be1ad5966b4606c49010bcb6312fc8256bba35` for the minimal `BaseFragment` + `WebView` host shape, WebView settings, cookie acceptance, and WebView cleanup lifecycle.
   - Telegram `org/telegram/ui/LoginActivity.java` was checked at the same commit, but it is a native MTProto phone-login workflow and is not a linux.do/Discourse login analogue.
   - NativeDo source of truth: `contracts/discourse/auth-flow.md`.

2. Entry points:
   - Profile tab logged-out action: “Sign in”.
   - Profile tab expired-session action: “Sign in again”.
   - Future session-recovery prompts can present the same workflow when Cloudflare or cookie verification requires browser fallback.

3. Screens/fragments/sheets:
   - `ProfileFragment` remains the entry point inside the Telegram-derived root tabs shell.
   - `LoginWebViewFragment` is a NativeDo login fragment hosted by `ActionBarLayout` and uses an Android `WebView` for linux.do authentication.
   - `ProfileScreen` remains NativeDo Feature Content and renders only session/profile state plus callbacks.

4. Menus/actions:
   - Login fragment action bar: back only.
   - Web login actions are owned by linux.do inside WebView, including Cloudflare, passkeys, OAuth, or two-factor flows if the site presents them.
   - Logout stays local in this phase and clears native + WebView cookies through `CookieSyncBridge`.

5. State transitions:
   - Anonymous/Expired in Profile → user taps sign in → push `LoginWebViewFragment`.
   - Login WebView loads `https://linux.do/login` after syncing any native cookies back into the WebView store.
   - On linux.do pages outside `/login`, the fragment waits for authenticated WebView cookies.
   - If the DOM exposes `meta[name=current-username]`, NativeDo completes login with that username to avoid a fragile native Cloudflare verification hop.
   - Otherwise NativeDo syncs WebView cookies into the OkHttp cookie jar and asks `SessionCoordinator` to restore through `/session/current.json` and bootstrap fallback.
   - Authenticated session → persist `SessionSnapshot` and finish login fragment back to Profile.
   - Failed attempt without authenticated cookies keeps WebView open so the user can continue Cloudflare/login steps.
   - If the user presses back after `_t` appears in WebView cookies, NativeDo syncs and verifies the session before returning to Profile; back exits normally only when no authenticated WebView cookie is present.

6. Storage/backend dependencies:
   - Telegram stores MTProto account auth state in `UserConfig`, `MessagesController`, and Telegram network storage; this is not used.
   - NativeDo stores Discourse cookies in `CookieStore` and mirrors them to/from Android `CookieManager`. This is `ADAPTED-BACKEND`.
   - NativeDo stores the latest validated user snapshot in `SessionStore`. This is `ADAPTED-BACKEND`.
   - NativeDo verifies login through `/session/current.json`, homepage bootstrap HTML, or WebView DOM `current-username`. This is `ADAPTED-BACKEND`.

7. Navigation/back behavior:
   - Profile pushes login through the current `ActionBarLayout` stack.
   - Login uses the standard `BaseFragment` back button and edge back behavior from the host.
   - Successful login finishes the login fragment, revealing Profile, which observes `SessionCoordinator.sessionState` and loads the signed-in profile.
   - Logout does not push navigation; it clears session state in place.

8. Unsupported or adapted dependencies:
   - `HOST-WIRING`: NativeDo package/import/resource/theme mapping, `ActionBarLayout`/`BaseFragment` host, and Android WebView lifecycle inside the Telegram-derived fragment stack.
   - `ADAPTED-BACKEND`: Discourse cookie/session restore replaces Telegram MTProto login/account storage.
   - `ADAPTED-PRODUCT`: The login UI is the linux.do website in WebView, not Telegram’s native phone-number login screens.
   - `OMITTED`: Telegram phone-number/SMS/password/passkey account-login screens, Telegram bot-game/stat WebView bridge events, share/open-in menu entries, and Telegram account switching.
   - `PARITY-BLOCKER`: None for the current NativeDo login scope; full native linux.do login is intentionally not attempted.

Current task scope:
- In scope:
  - Wire Profile sign-in actions to a WebView-backed login fragment.
  - Sync native cookies into WebView before login and WebView cookies back into native storage after login.
  - Complete session restoration and return to Profile on authenticated state.
  - Keep Cloudflare/browser fallback behavior by leaving the WebView open when native verification fails.
- Deferred:
  - Server-side logout endpoint call.
  - Dedicated loading/error overlay copy inside the login WebView.
  - Triggering this workflow automatically from every session-expired event outside Profile.
- Parity blockers:
  - None for this phase.
