@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keygen

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.api.FeatureFlagApi
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
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
import kotlin.test.assertFalse
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
    private lateinit var featureFlagApi: FeatureFlagApi
    private lateinit var referralCodeSettingsRepository: ReferralCodeSettingsRepositoryContract
    private lateinit var chainAccountAddressRepository: ChainAccountAddressRepository

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
        featureFlagApi = mockk(relaxed = true)
        referralCodeSettingsRepository = mockk(relaxed = true)
        chainAccountAddressRepository = mockk(relaxed = true)
    }

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
            featureFlagApi = featureFlagApi,
            referralCodeSettingsRepository = referralCodeSettingsRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
        )

    @Test
    fun `state action matches the route arg`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            assertEquals(TssAction.SingleKeygen, vm.state.value.action)
        }

    @Test
    fun `init with empty keys transitions to error state`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            assertNotNull(vm.state.value.error)
        }

    @Test
    fun `isSuccess is false after keygen error`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            assertFalse(vm.state.value.isSuccess)
        }

    @Test
    fun `keygenState is CreatingInstance after keygen error`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            assertEquals(KeygenState.CreatingInstance, vm.state.value.keygenState)
        }

    @Test
    fun `progress is 0f after keygen error`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            assertEquals(0f, vm.state.value.progress)
        }

    @Test
    fun `tryAgain navigates back`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.tryAgain()
            coVerify { navigator.navigate(Destination.Back) }
        }
}
