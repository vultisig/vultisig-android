@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keygen

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.passcode.AutoLockHold
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.ChainImportSetting
import com.vultisig.wallet.data.repositories.FeatureFlagRepository
import com.vultisig.wallet.data.repositories.KeyImportRepository
import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepositoryContract
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.vault.TemporaryVaultRepository
import com.vultisig.wallet.data.usecases.DeriveChainKeyUseCase
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.usecases.ExtractMasterKeysUseCase
import com.vultisig.wallet.data.usecases.SaveVaultUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Unit tests for [KeygenViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class KeygenViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var context: Context
    private lateinit var navigator: Navigator<Destination>
    private lateinit var saveVault: SaveVaultUseCase
    private lateinit var lastOpenedVaultRepository: LastOpenedVaultRepository
    private lateinit var vaultDataStoreRepository: VaultDataStoreRepository
    private lateinit var vaultPasswordRepository: VaultPasswordRepository
    private lateinit var temporaryVaultRepository: TemporaryVaultRepository
    private lateinit var vaultRepository: VaultRepository
    private lateinit var keyImportRepository: KeyImportRepository
    private lateinit var extractMasterKeys: ExtractMasterKeysUseCase
    private lateinit var deriveChainKey: DeriveChainKeyUseCase
    private lateinit var sessionApi: SessionApi
    private lateinit var encryption: Encryption
    private lateinit var featureFlagRepository: FeatureFlagRepository
    private lateinit var referralCodeSettingsRepository: ReferralCodeSettingsRepositoryContract
    private lateinit var chainAccountAddressRepository: ChainAccountAddressRepository

    /** Sets up mocks and test dispatcher before each test. */
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { any<SavedStateHandle>().toRoute<Route.Keygen.Generating>() } returns
            Route.Keygen.Generating(
                action = TssAction.SingleKeygen,
                sessionId = "",
                serverUrl = "",
                localPartyId = "",
                vaultName = "Test Vault",
                hexChainCode = "",
                keygenCommittee = emptyList(),
                encryptionKeyHex = "",
                isInitiatingDevice = false,
                libType = SigningLibType.DKLS,
                vaultId = "vault-1",
                oldCommittee = emptyList(),
                oldResharePrefix = "",
                email = null,
                password = null,
                hint = null,
                deviceCount = null,
            )
        context = mockk(relaxed = true)
        navigator = mockk(relaxed = true)
        saveVault = mockk(relaxed = true)
        lastOpenedVaultRepository = mockk(relaxed = true)
        vaultDataStoreRepository = mockk(relaxed = true)
        vaultPasswordRepository = mockk(relaxed = true)
        temporaryVaultRepository = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        keyImportRepository = mockk(relaxed = true)
        extractMasterKeys = mockk(relaxed = true)
        deriveChainKey = mockk(relaxed = true)
        sessionApi = mockk(relaxed = true)
        encryption = mockk(relaxed = true)
        featureFlagRepository = mockk(relaxed = true)
        referralCodeSettingsRepository = mockk(relaxed = true)
        chainAccountAddressRepository = mockk(relaxed = true)
    }

    /** Cleans up mocks and resets test dispatcher after each test. */
    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    private fun givenKeyImportRoute(chains: List<String>) {
        every { any<SavedStateHandle>().toRoute<Route.Keygen.Generating>() } returns
            KEY_IMPORT_ROUTE.copy(chains = chains)
        // Settling the chain list is all these tests are after; the ceremony itself needs the TSS
        // native libraries, so it stops on the missing import data right after.
        every { keyImportRepository.get() } returns null
    }

    private fun createViewModel() =
        KeygenViewModel(
            savedStateHandle = SavedStateHandle(),
            navigator = navigator,
            context = context,
            saveVault = saveVault,
            lastOpenedVaultRepository = lastOpenedVaultRepository,
            vaultDataStoreRepository = vaultDataStoreRepository,
            vaultPasswordRepository = vaultPasswordRepository,
            temporaryVaultRepository = temporaryVaultRepository,
            vaultRepository = vaultRepository,
            keyImportRepository = keyImportRepository,
            extractMasterKeys = extractMasterKeys,
            deriveChainKey = deriveChainKey,
            sessionApi = sessionApi,
            encryption = encryption,
            featureFlagRepository = featureFlagRepository,
            autoLockHold = AutoLockHold(),
            referralCodeSettingsRepository = referralCodeSettingsRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
        )

    /** Verifies the state action matches the route arg. */
    @Test
    fun `state action matches the route arg`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.state.value.action shouldBe TssAction.SingleKeygen
        }

    /** Verifies init with empty keys surfaces an error and never advances past CreatingInstance. */
    @Test
    fun `init with empty keys surfaces error and stays at CreatingInstance`() =
        runTest(testDispatcher) {
            val vm = createViewModel()

            val state = vm.state.value
            state.error.shouldNotBeNull()
            state.keygenState shouldBe KeygenState.CreatingInstance
        }

    /**
     * The initiator opens one keygen session per chain it picked and waits for this device in each,
     * so a chain this build has dropped since — an initiator still on a version that offers it —
     * has to stop the ceremony here. Skipping it left that session a party short and both devices
     * failed on a timeout that named nothing.
     */
    @Test
    fun `key import refuses a chain this version no longer supports`() =
        runTest(testDispatcher) {
            givenKeyImportRoute(chains = listOf(Chain.Bitcoin.raw, RETIRED_CHAIN_ID))

            val vm = createViewModel()

            vm.state.value.error.shouldNotBeNull().rawError shouldContain RETIRED_CHAIN_ID
            coVerify(exactly = 0) { keyImportRepository.setChainSettings(any()) }
        }

    /** A joining device still builds its settings from the chains it does know. */
    @Test
    fun `key import takes the chains it knows from the route`() =
        runTest(testDispatcher) {
            givenKeyImportRoute(chains = listOf(Chain.Bitcoin.raw, Chain.Ethereum.raw))

            createViewModel()

            coVerify {
                keyImportRepository.setChainSettings(
                    listOf(
                        ChainImportSetting(chain = Chain.Bitcoin),
                        ChainImportSetting(chain = Chain.Ethereum),
                    )
                )
            }
        }

    /** Verifies tryAgain navigates back. */
    @Test
    fun `tryAgain navigates back`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.tryAgain()
            coVerify { navigator.navigate(Destination.Back) }
        }

    /**
     * Reshare, migrate and MLDSA keygen build on the vault's existing shares, and a read taken
     * while the app is locked comes back without them.
     */
    @Test
    fun `the ceremony waits until the existing keyshares can be read`() =
        runTest(testDispatcher) {
            val unlocked = CompletableDeferred<Unit>()
            coEvery { vaultRepository.awaitKeySharesReadable() } coAnswers { unlocked.await() }

            createViewModel()

            coVerify(exactly = 0) { vaultRepository.get(any()) }

            unlocked.complete(Unit)
            runCurrent()

            coVerify(exactly = 1) { vaultRepository.get("vault-1") }
        }

    private companion object {
        // A chain id an older initiator can still name after this build dropped the entry.
        const val RETIRED_CHAIN_ID = "Kujira"

        val KEY_IMPORT_ROUTE =
            Route.Keygen.Generating(
                action = TssAction.KeyImport,
                sessionId = "",
                serverUrl = "",
                localPartyId = "",
                vaultName = "Test Vault",
                hexChainCode = "",
                keygenCommittee = emptyList(),
                encryptionKeyHex = "",
                isInitiatingDevice = false,
                libType = SigningLibType.DKLS,
                vaultId = null,
                oldCommittee = emptyList(),
                oldResharePrefix = "",
                email = null,
                password = null,
                hint = null,
                deviceCount = null,
            )
    }
}
