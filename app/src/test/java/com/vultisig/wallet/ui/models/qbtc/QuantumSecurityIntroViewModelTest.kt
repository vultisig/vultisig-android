@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.qbtc

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.KeyShare
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.IsVaultHasFastSignByIdUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class QuantumSecurityIntroViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var navigator: Navigator<Destination>
    private lateinit var vaultRepository: VaultRepository
    private lateinit var isVaultHasFastSignById: IsVaultHasFastSignByIdUseCase
    private lateinit var savedStateHandle: SavedStateHandle

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        savedStateHandle = mockk()
        every { savedStateHandle.toRoute<Route.QuantumSecurityIntro>() } returns
            Route.QuantumSecurityIntro(vaultId = VAULT_ID)
        navigator = mockk(relaxed = true)
        vaultRepository = mockk()
        isVaultHasFastSignById = mockk()
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        QuantumSecurityIntroViewModel(
            savedStateHandle = savedStateHandle,
            navigator = navigator,
            vaultRepository = vaultRepository,
            isVaultHasFastSignById = isVaultHasFastSignById,
        )

    private fun vault(
        pubKeyMLDSA: String = "",
        keyshares: List<KeyShare> = emptyList(),
        signers: List<String> = listOf("device1", "device2"),
    ) =
        Vault(
            id = VAULT_ID,
            name = VAULT_NAME,
            pubKeyMLDSA = pubKeyMLDSA,
            keyshares = keyshares,
            signers = signers,
            libType = SigningLibType.DKLS,
        )

    @Test
    fun `getStarted with valid MLDSA key navigates to QbtcClaim`() =
        runTest(mainDispatcher) {
            coEvery { vaultRepository.get(VAULT_ID) } returns
                vault(
                    pubKeyMLDSA = MLDSA_KEY,
                    keyshares = listOf(KeyShare(pubKey = MLDSA_KEY, keyShare = "share")),
                )

            createViewModel().getStarted()
            advanceUntilIdle()

            coVerify { navigator.route(Route.QbtcClaim(vaultId = VAULT_ID)) }
        }

    @Test
    fun `getStarted on fast vault without key navigates to VerifyExistingVault single keygen`() =
        runTest(mainDispatcher) {
            coEvery { vaultRepository.get(VAULT_ID) } returns vault()
            coEvery { isVaultHasFastSignById(VAULT_ID) } returns true

            createViewModel().getStarted()
            advanceUntilIdle()

            coVerify {
                navigator.route(
                    Route.VerifyExistingVault(
                        name = VAULT_NAME,
                        tssAction = TssAction.SingleKeygen,
                        vaultId = VAULT_ID,
                    )
                )
            }
        }

    @Test
    fun `getStarted on secure vault without key navigates to peer discovery single keygen`() =
        runTest(mainDispatcher) {
            coEvery { vaultRepository.get(VAULT_ID) } returns
                vault(signers = listOf("device1", "device2", "device3"))
            coEvery { isVaultHasFastSignById(VAULT_ID) } returns false

            createViewModel().getStarted()
            advanceUntilIdle()

            coVerify {
                navigator.route(
                    Route.Keygen.PeerDiscovery(
                        action = TssAction.SingleKeygen,
                        vaultName = VAULT_NAME,
                        vaultId = VAULT_ID,
                    )
                )
            }
        }

    @Test
    fun `getStarted with key but no matching keyshare starts keygen instead of claim`() =
        runTest(mainDispatcher) {
            coEvery { vaultRepository.get(VAULT_ID) } returns vault(pubKeyMLDSA = MLDSA_KEY)
            coEvery { isVaultHasFastSignById(VAULT_ID) } returns false

            createViewModel().getStarted()
            advanceUntilIdle()

            coVerify {
                navigator.route(
                    Route.Keygen.PeerDiscovery(
                        action = TssAction.SingleKeygen,
                        vaultName = VAULT_NAME,
                        vaultId = VAULT_ID,
                    )
                )
            }
        }

    @Test
    fun `back navigates back`() =
        runTest(mainDispatcher) {
            createViewModel().back()
            advanceUntilIdle()

            coVerify { navigator.navigate(Destination.Back) }
        }

    private companion object {
        private const val VAULT_ID = "vaultId"
        private const val VAULT_NAME = "vaultName"
        private const val MLDSA_KEY = "mldsaPubKey"
    }
}
