@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.screens.verify

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.securityscanner.SecurityScannerContract
import com.vultisig.wallet.data.usecases.IsVaultHasFastSignByIdUseCase
import com.vultisig.wallet.ui.models.VerifyTransactionViewModel
import com.vultisig.wallet.ui.models.mappers.TransactionToUiModelMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.util.LaunchKeysignUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Unit tests for [VerifyTransactionViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class VerifyTransactionViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var navigator: Navigator<Destination>
    private lateinit var mapTransactionToUiModel: TransactionToUiModelMapper
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var vaultPasswordRepository: VaultPasswordRepository
    private lateinit var launchKeysign: LaunchKeysignUseCase
    private lateinit var isVaultHasFastSignById: IsVaultHasFastSignByIdUseCase
    private lateinit var securityScannerService: SecurityScannerContract
    private lateinit var vaultRepository: VaultRepository
    private lateinit var addressBookRepository: AddressBookRepository

    /** Sets up mocks and test dispatcher before each test. */
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { any<SavedStateHandle>().toRoute<Route.VerifySend>() } returns
            Route.VerifySend(vaultId = VAULT_ID, transactionId = TX_ID)
        navigator = mockk(relaxed = true)
        mapTransactionToUiModel = mockk(relaxed = true)
        transactionRepository = mockk(relaxed = true)
        vaultPasswordRepository = mockk(relaxed = true)
        launchKeysign = mockk(relaxed = true)
        isVaultHasFastSignById = mockk(relaxed = true)
        securityScannerService = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        addressBookRepository = mockk(relaxed = true)
    }

    /** Cleans up mocks and resets test dispatcher after each test. */
    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        VerifyTransactionViewModel(
            savedStateHandle = SavedStateHandle(),
            navigator = navigator,
            mapTransactionToUiModel = mapTransactionToUiModel,
            transactionRepository = transactionRepository,
            vaultPasswordRepository = vaultPasswordRepository,
            launchKeysign = launchKeysign,
            isVaultHasFastSignById = isVaultHasFastSignById,
            securityScannerService = securityScannerService,
            vaultRepository = vaultRepository,
            addressBookRepository = addressBookRepository,
        )

    /** Verifies checkConsentAddress sets consentAddress true. */
    @Test
    fun `checkConsentAddress sets consentAddress true`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.checkConsentAddress(true)
            assertTrue(vm.uiState.value.consentAddress)
        }

    /** Verifies checkConsentAmount sets consentAmount true. */
    @Test
    fun `checkConsentAmount sets consentAmount true`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.checkConsentAmount(true)
            assertTrue(vm.uiState.value.consentAmount)
        }

    /** Verifies hasAllConsents is false when only address consent is given. */
    @Test
    fun `hasAllConsents is false when only address consent is given`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.checkConsentAddress(true)
            assertFalse(vm.uiState.value.hasAllConsents)
        }

    /** Verifies hasAllConsents is true when both consents are given. */
    @Test
    fun `hasAllConsents is true when both consents are given`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.checkConsentAddress(true)
            vm.checkConsentAmount(true)
            assertTrue(vm.uiState.value.hasAllConsents)
        }

    /** Verifies joinKeySign sets errorText when consents not checked. */
    @Test
    fun `joinKeySign sets errorText when consents not checked`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.joinKeySign()
            assertNotNull(vm.uiState.value.errorText)
        }

    /** Verifies dismissError clears errorText. */
    @Test
    fun `dismissError clears errorText`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.joinKeySign()
            vm.dismissError()
            assertNull(vm.uiState.value.errorText)
        }

    /** Verifies dismissScanningWarning sets showScanningWarning to false after it was true. */
    @Test
    fun `dismissScanningWarning sets showScanningWarning to false`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.uiState.update { it.copy(showScanningWarning = true) }
            vm.dismissScanningWarning()
            assertFalse(vm.uiState.value.showScanningWarning)
        }

    /** Verifies hasFastSign is true when isVaultHasFastSignById returns true. */
    @Test
    fun `hasFastSign is true when isVaultHasFastSignById returns true`() =
        runTest(testDispatcher) {
            coEvery { isVaultHasFastSignById(VAULT_ID) } returns true
            val vm = createViewModel()
            assertTrue(vm.uiState.value.hasFastSign)
        }

    /** Verifies joinKeySign calls launchKeysign when both consents are given. */
    @Test
    fun `joinKeySign calls launchKeysign when both consents are given`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.checkConsentAddress(true)
            vm.checkConsentAmount(true)
            vm.joinKeySign()
            coVerify { launchKeysign(any(), any(), any(), any(), any()) }
        }

    private companion object {
        const val VAULT_ID = "vault-1"
        const val TX_ID = "tx-1"
    }
}
