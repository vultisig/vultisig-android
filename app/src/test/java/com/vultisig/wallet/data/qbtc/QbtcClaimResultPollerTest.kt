@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalEncodingApi::class)

package com.vultisig.wallet.data.qbtc

import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.usecases.Encryption
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

internal class QbtcClaimResultPollerTest {

    private val sessionApi = mockk<SessionApi>()
    private val encryption = mockk<Encryption>()
    private val json = Json { ignoreUnknownKeys = true }
    private val poller = QbtcClaimResultPoller(sessionApi, encryption, json)

    private val session =
        QbtcClaimKeysignSession(
            serverUrl = "https://relay.test",
            sessionId = "session-id",
            encryptionKeyHex = "00".repeat(32),
            committee = listOf("deviceA", "deviceB"),
        )

    @Test
    fun `awaitResult returns the message once it appears after empty polls`() = runTest {
        val expected = QbtcClaimResultMessage(txHash = "ABC123", totalSats = 5_000L)
        val plaintext =
            json.encodeToString(QbtcClaimResultMessage.serializer(), expected).toByteArray()
        val cipher = Base64.encode("enc".toByteArray())

        var calls = 0
        coEvery {
            sessionApi.getSetupMessage(any(), any(), QbtcClaimResultMessage.MESSAGE_ID)
        } answers
            {
                calls++
                if (calls < 3) "" else cipher
            }
        // The relay returns nothing (empty -> null plaintext) until the initiator pushes; then the
        // ciphertext decrypts to the result.
        every { encryption.decrypt(any(), any()) } answers
            {
                if (firstArg<ByteArray>().isEmpty()) null else plaintext
            }

        val result = poller.awaitResult(session, timeout = 60.seconds, interval = 1.seconds)

        result?.txHash shouldBe "ABC123"
        result?.totalSats shouldBe 5_000L
    }

    @Test
    fun `awaitResult returns null when nothing arrives before the timeout`() = runTest {
        coEvery { sessionApi.getSetupMessage(any(), any(), any()) } returns ""
        every { encryption.decrypt(any(), any()) } returns null

        val result = poller.awaitResult(session, timeout = 5.seconds, interval = 1.seconds)

        result.shouldBeNull()
    }
}
