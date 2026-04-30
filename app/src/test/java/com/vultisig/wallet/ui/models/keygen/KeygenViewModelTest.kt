@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keygen

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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
            referralCodeSettingsRepository = referralCodeSettingsRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
        )

    /** Verifies the state action matches the route arg. */
    @Test
    fun `state action matches the route arg`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            assertEquals(TssAction.SingleKeygen, vm.state.value.action)
        }

    /** Verifies init with empty keys surfaces an error and never advances past CreatingInstance. */
    @Test
    fun `init with empty keys surfaces error and stays at CreatingInstance`() =
        runTest(testDispatcher) {
            val vm = createViewModel()

            val state = vm.state.value
            assertNotNull(state.error)
            assertEquals(KeygenState.CreatingInstance, state.keygenState)
        }

    /** Verifies tryAgain navigates back. */
    @Test
    fun `tryAgain navigates back`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.tryAgain()
            coVerify { navigator.navigate(Destination.Back) }
        }
}
