package io.github.baba983.nativedo.auth.device

import java.security.KeyPairGenerator
import java.security.PublicKey
import java.util.Base64
import javax.crypto.Cipher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three ways the polling loop can lose without the device pass being able to show it:
 * a payload that was not produced for this grant's nonce must never become a stored key, and
 * a denial or an expiry must be terminal (no second poll, no key).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceLoginControllerTest {

    private class FakeApi(
        var pollOutcome: DevicePollOutcome = DevicePollOutcome.Pending,
    ) : DeviceAuthApi {
        var pollCalls = 0
        var lastCreateNonce: String? = null
        val probeUsernames = mutableListOf<String?>()

        override suspend fun create(request: DeviceCreateRequest): DeviceCreateResponse {
            assertEquals("nativedo-test", request.clientId)
            lastCreateNonce = request.nonce
            return DeviceCreateResponse(
                deviceCode = "d".repeat(64),
                userCode = "ABCD-2345",
                verificationUri = "https://linux.do/login/device",
                verificationUriWithRequest = "https://linux.do/login/device?request=TESTTEST",
                expiresInSeconds = 600,
                interval = 5,
            )
        }

        override suspend fun poll(deviceCode: String): DevicePollOutcome {
            pollCalls++
            return pollOutcome
        }

        override suspend fun probeSession(key: String, clientId: String): KeyProbeResult {
            probeUsernames += key
            return KeyProbeResult(status = 200, username = "example_user", errorType = null)
        }

        override suspend fun revoke(key: String): Int = 200
    }

    private fun newKeyPair() = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun seal(publicKey: PublicKey, json: String): String {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return Base64.getEncoder().encodeToString(cipher.doFinal(json.toByteArray()))
    }

    private fun sealedPayload(
        keyPair: java.security.KeyPair,
        nonce: String,
    ): String = seal(
        keyPair.public,
        """{"key":"PAYLOAD-KEY","nonce":"$nonce","push":false,"api":4}""",
    )

    @Test
    fun `a payload sealed for another nonce is rejected and stores nothing`() = runTest {
        val keyPair = newKeyPair()
        val store = DeviceKeyStore()
        val api = FakeApi()
        val controller = DeviceLoginController(
            api = api,
            keyStore = store,
            loginKey = DeviceLoginKeys.softwareForTest(keyPair),
            clientId = "nativedo-test",
            now = { testScheduler.currentTime },
            scope = backgroundScope,
        )

        controller.startLogin()
        runCurrent()
        assertTrue(controller.state.value is DeviceLoginController.State.Waiting)

        api.pollOutcome = DevicePollOutcome.Authorized(sealedPayload(keyPair, "another-grant"))
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(
            DeviceLoginController.State.Failed("授权载荷校验失败：nonce_mismatch"),
            controller.state.value,
        )
        assertFalse(store.hasKey())
        assertTrue(api.probeUsernames.isEmpty())
    }

    @Test
    fun `a granted payload with the right nonce is stored once and probed`() = runTest {
        val keyPair = newKeyPair()
        val store = DeviceKeyStore()
        val api = FakeApi()
        val controller = DeviceLoginController(
            api = api,
            keyStore = store,
            loginKey = DeviceLoginKeys.softwareForTest(keyPair),
            clientId = "nativedo-test",
            now = { testScheduler.currentTime },
            scope = backgroundScope,
        )

        controller.startLogin()
        runCurrent()
        val nonce = api.lastCreateNonce ?: error("no nonce in the create request")
        api.pollOutcome = DevicePollOutcome.Authorized(sealedPayload(keyPair, nonce))
        advanceTimeBy(5_000)
        runCurrent()

        assertTrue(controller.state.value is DeviceLoginController.State.Authorized)
        assertTrue(store.hasKey())
        assertEquals(listOf("PAYLOAD-KEY"), api.probeUsernames)
    }

    @Test
    fun `a denial is terminal, stores nothing and stops polling`() = runTest {
        val keyPair = newKeyPair()
        val store = DeviceKeyStore()
        val api = FakeApi(pollOutcome = DevicePollOutcome.Denied)
        val controller = DeviceLoginController(
            api = api,
            keyStore = store,
            loginKey = DeviceLoginKeys.softwareForTest(keyPair),
            clientId = "nativedo-test",
            now = { testScheduler.currentTime },
            scope = backgroundScope,
        )

        controller.startLogin()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(DeviceLoginController.State.Denied, controller.state.value)
        assertEquals(1, api.pollCalls)
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(1, api.pollCalls)
        assertFalse(store.hasKey())
    }

    @Test
    fun `an expired grant is terminal and stops polling`() = runTest {
        val keyPair = newKeyPair()
        val store = DeviceKeyStore()
        val api = FakeApi(pollOutcome = DevicePollOutcome.Expired)
        val controller = DeviceLoginController(
            api = api,
            keyStore = store,
            loginKey = DeviceLoginKeys.softwareForTest(keyPair),
            clientId = "nativedo-test",
            now = { testScheduler.currentTime },
            scope = backgroundScope,
        )

        controller.startLogin()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(DeviceLoginController.State.Expired, controller.state.value)
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(1, api.pollCalls)
        assertFalse(store.hasKey())
    }
}
