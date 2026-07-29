package com.vultisig.wallet.data.services

import android.content.SharedPreferences
import com.vultisig.wallet.data.api.NotificationApi
import com.vultisig.wallet.data.api.NotifyRequest
import com.vultisig.wallet.data.db.dao.VaultNotificationSettingsDao
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.VaultRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class PushNotificationManagerTest {

    private val notificationApi: NotificationApi = mockk(relaxed = true)
    private val vaultRepository: VaultRepository = mockk(relaxed = true)
    private val settingsDao: VaultNotificationSettingsDao = mockk(relaxed = true)
    private val encryptedPrefs: SharedPreferences = mockk(relaxed = true)
    private val fcmTokenProvider: FcmTokenProvider = mockk(relaxed = true)

    private val manager =
        PushNotificationManager(
            notificationApi,
            vaultRepository,
            settingsDao,
            encryptedPrefs,
            fcmTokenProvider,
        )

    private val vault =
        Vault(
            id = "vault-id",
            name = "My Vault",
            pubKeyECDSA = "02abcdef",
            hexChainCode = "chaincode",
            localPartyID = "iPhone-A1B2",
        )

    private fun storedToken(token: String?) {
        every { encryptedPrefs.getString(FCM_TOKEN_KEY, null) } returns token
    }

    /**
     * The initiator's own token is irrelevant to /notify — the server picks recipients from the
     * devices registered under `vaultId`. Skipping the call here stranded peers that had opted in,
     * while the caller was told the push went out (#5440).
     */
    @Test
    fun `notifies vault devices when the local device has no stored token`() = runTest {
        storedToken(null)

        manager.notifyVaultDevices(vault, QR_CODE_DATA)

        coVerify(exactly = 1) { notificationApi.notify(any()) }
    }

    @Test
    fun `notifies vault devices when the local device has a stored token`() = runTest {
        storedToken("fcm-token")

        manager.notifyVaultDevices(vault, QR_CODE_DATA)

        coVerify(exactly = 1) { notificationApi.notify(any()) }
    }

    @Test
    fun `sends the vault identity and qr payload, never the local token`() = runTest {
        storedToken(null)
        val request = slot<NotifyRequest>()
        coEvery { notificationApi.notify(capture(request)) } returns Unit

        manager.notifyVaultDevices(vault, QR_CODE_DATA)

        with(request.captured) {
            vaultId shouldBe sha256Hex(vault.pubKeyECDSA + vault.hexChainCode)
            vaultName shouldBe vault.name
            localPartyId shouldBe vault.localPartyID
            qrCodeData shouldBe QR_CODE_DATA
        }
    }

    /**
     * The caller reports success off a normal return, so a failed notify must not return normally —
     * otherwise the user sees "sent" and is locked out by the resend cooldown.
     */
    @Test
    fun `propagates notify failures to the caller`() = runTest {
        storedToken("fcm-token")
        coEvery { notificationApi.notify(any()) } throws IOException("unreachable")

        assertThrows<IOException> { manager.notifyVaultDevices(vault, QR_CODE_DATA) }
    }

    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") {
            "%02x".format(it)
        }

    private companion object {
        const val FCM_TOKEN_KEY = "fcm_device_token"
        const val QR_CODE_DATA = "https://vultisig.com?type=SignTransaction"
    }
}
