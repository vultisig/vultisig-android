@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.settings

import com.vultisig.wallet.data.db.models.VaultNotificationSettingsEntity
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.services.PushNotificationManager
import com.vultisig.wallet.data.services.SystemNotificationStatus
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.SnackbarFlow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class NotificationsSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val navigator: Navigator<Destination> = mockk(relaxed = true)
    private val vaultRepository: VaultRepository = mockk(relaxed = true)
    private val pushNotificationManager: PushNotificationManager = mockk(relaxed = true)
    private val systemNotificationStatus: SystemNotificationStatus = mockk()
    private val snackbarFlow: SnackbarFlow = mockk(relaxed = true)

    private val vault =
        Vault(
            id = "vault-a",
            name = "Secure Vault",
            signers = listOf("party-a", "party-b"),
            libType = SigningLibType.DKLS,
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { vaultRepository.getAllAsFlow() } returns flowOf(listOf(vault))
        every { pushNotificationManager.observeAllSettings() } returns
            flowOf(
                listOf(
                    VaultNotificationSettingsEntity(vaultId = vault.id, notificationsEnabled = true)
                )
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        NotificationsSettingsViewModel(
            navigator,
            vaultRepository,
            pushNotificationManager,
            systemNotificationStatus,
            snackbarFlow,
        )

    /**
     * The in-app toggle stays ON after the OS revokes permission or the user mutes the channel —
     * neither raises a callback — so the screen has to ask, or it claims pushes are on while every
     * one is dropped (#5441).
     */
    @Test
    fun `flags blocked notifications while the in-app toggle still reads on`() = runTest {
        every { systemNotificationStatus.areNotificationsEnabled() } returns false
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.refreshSystemNotificationState()

        with(viewModel.state.value) {
            masterEnabled shouldBe true
            isBlockedBySystem shouldBe true
        }
    }

    @Test
    fun `does not flag anything when the system allows notifications`() = runTest {
        every { systemNotificationStatus.areNotificationsEnabled() } returns true
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.refreshSystemNotificationState()

        viewModel.state.value.isBlockedBySystem shouldBe false
    }

    /** The settings flow re-emits often; a rebuild of the list must not clear the OS warning. */
    @Test
    fun `keeps the blocked flag when the settings flow re-emits`() = runTest {
        every { systemNotificationStatus.areNotificationsEnabled() } returns false
        val viewModel = viewModel()
        viewModel.refreshSystemNotificationState()

        advanceUntilIdle()

        with(viewModel.state.value) {
            vaults.map { it.vaultId } shouldBe listOf(vault.id)
            isBlockedBySystem shouldBe true
        }
    }
}
