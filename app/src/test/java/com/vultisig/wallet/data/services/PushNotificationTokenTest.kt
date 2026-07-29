package com.vultisig.wallet.data.services

import android.content.SharedPreferences
import com.vultisig.wallet.data.api.DeviceRegistrationRequest
import com.vultisig.wallet.data.api.NotificationApi
import com.vultisig.wallet.data.db.dao.VaultNotificationSettingsDao
import com.vultisig.wallet.data.db.models.VaultNotificationSettingsEntity
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Covers token acquisition and re-registration — the path #5439 left with no way to heal. */
internal class PushNotificationTokenTest {

    private val notificationApi: NotificationApi = mockk(relaxed = true)
    private val vaultRepository: com.vultisig.wallet.data.repositories.VaultRepository =
        mockk(relaxed = true)
    private val settingsDao: VaultNotificationSettingsDao = mockk(relaxed = true)
    private val encryptedPrefs: SharedPreferences = mockk(relaxed = true)
    private val editor: SharedPreferences.Editor = mockk(relaxed = true)
    private val fcmTokenProvider: FcmTokenProvider = mockk()

    private lateinit var manager: PushNotificationManager

    @BeforeEach
    fun setUp() {
        every { encryptedPrefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        manager =
            PushNotificationManager(
                notificationApi,
                vaultRepository,
                settingsDao,
                encryptedPrefs,
                fcmTokenProvider,
            )
    }

    private fun storedToken(token: String?) {
        every { encryptedPrefs.getString(FCM_TOKEN_KEY, null) } returns token
    }

    private fun secureVault(id: String) =
        Vault(
            id = id,
            name = id,
            pubKeyECDSA = "02$id",
            hexChainCode = "chaincode",
            localPartyID = "party-$id",
            signers = listOf("party-$id", "party-other"),
            libType = SigningLibType.DKLS,
        )

    private fun optedIn(vararg vaultIds: String) {
        coEvery { settingsDao.getAllEnabled() } returns
            vaultIds.map {
                VaultNotificationSettingsEntity(vaultId = it, notificationsEnabled = true)
            }
        vaultIds.forEach { id -> coEvery { vaultRepository.get(id) } returns secureVault(id) }
    }

    @Test
    fun `storeToken persists synchronously so service teardown cannot lose it`() {
        val stored = slot<String>()
        every { editor.putString(FCM_TOKEN_KEY, capture(stored)) } returns editor

        manager.storeToken("fresh-token")

        stored.captured shouldBe "fresh-token"
        verify { editor.putString(FCM_TOKEN_KEY, "fresh-token") }
    }

    @Test
    fun `currentToken returns the stored token without minting a new one`() = runTest {
        storedToken("stored-token")

        manager.currentToken() shouldBe "stored-token"

        coVerify(exactly = 0) { fcmTokenProvider.fetchToken() }
    }

    @Test
    fun `currentToken mints and persists a token when none is stored`() = runTest {
        storedToken(null)
        coEvery { fcmTokenProvider.fetchToken() } returns "minted-token"

        manager.currentToken() shouldBe "minted-token"

        verify { editor.putString(FCM_TOKEN_KEY, "minted-token") }
    }

    /** Null, not an exception: the caller reschedules rather than treating this as permanent. */
    @Test
    fun `currentToken returns null when minting fails`() = runTest {
        storedToken(null)
        coEvery { fcmTokenProvider.fetchToken() } throws IOException("no play services")

        manager.currentToken() shouldBe null
    }

    @Test
    fun `reRegisterOptedInVaults registers every opted-in vault with the new token`() = runTest {
        optedIn("vault-a", "vault-b")
        val requests = mutableListOf<DeviceRegistrationRequest>()
        coEvery { notificationApi.registerDevice(capture(requests)) } returns Unit

        manager.reRegisterOptedInVaults("new-token") shouldBe true

        requests.map { it.token } shouldBe listOf("new-token", "new-token")
        requests.map { it.partyName } shouldBe listOf("party-vault-a", "party-vault-b")
    }

    @Test
    fun `reRegisterOptedInVaults reports failure when a vault cannot be registered`() = runTest {
        optedIn("vault-a", "vault-b")
        coEvery { notificationApi.registerDevice(match { it.partyName == "party-vault-a" }) } throws
            IOException("unreachable")

        manager.reRegisterOptedInVaults("new-token") shouldBe false
    }

    /** One failure must not strand the vaults behind it — they still get the new token. */
    @Test
    fun `reRegisterOptedInVaults keeps going after a failure`() = runTest {
        optedIn("vault-a", "vault-b")
        coEvery { notificationApi.registerDevice(match { it.partyName == "party-vault-a" }) } throws
            IOException("unreachable")

        manager.reRegisterOptedInVaults("new-token")

        coVerify(exactly = 1) {
            notificationApi.registerDevice(match { it.partyName == "party-vault-b" })
        }
    }

    @Test
    fun `hasOptedInVaults is false when nothing is enabled`() = runTest {
        coEvery { settingsDao.getAllEnabled() } returns emptyList()

        manager.hasOptedInVaults() shouldBe false
    }

    private companion object {
        const val FCM_TOKEN_KEY = "fcm_device_token"
    }
}
