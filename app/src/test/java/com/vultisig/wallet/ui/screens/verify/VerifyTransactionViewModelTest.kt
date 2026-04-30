@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.screens.verify

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.securityscanner.SecurityScannerContract
import com.vultisig.wallet.data.securityscanner.SecurityScannerResult
import com.vultisig.wallet.data.usecases.IsVaultHasFastSignByIdUseCase
import com.vultisig.wallet.ui.models.TransactionDetailsUiModel
import com.vultisig.wallet.ui.models.TransactionScanStatus
import com.vultisig.wallet.ui.models.VerifyTransactionViewModel
import com.vultisig.wallet.ui.models.keysign.KeysignInitType
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
        // Function-type-interface mocks need explicit return-type stubs; relaxed mode auto-stubs
        // to a generic Object that fails the implicit cast at the VM call site.
        coEvery { isVaultHasFastSignById(any()) } returns false
        coEvery { mapTransactionToUiModel(any()) } returns TransactionDetailsUiModel()
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

    /** Verifies dismissScanningWarning clears showScanningWarning after a flagged scan. */
    @Test
    fun `dismissScanningWarning clears showScanningWarning after a flagged scan`() =
        runTest(testDispatcher) {
            // Stub a transaction so loadTransaction succeeds and the scan plumbing runs.
            coEvery { transactionRepository.getTransaction(TX_ID) } returns mockk(relaxed = true)
            val vm = createViewModel()
            vm.checkConsentAddress(true)
            vm.checkConsentAmount(true)
            // joinKeySign goes through handleSigningFlowCommon which sets showScanningWarning when
            // the scan status reports an unsafe transaction. Drive that path by emitting a Scanned
            // status with isSecure=false via a SecurityScannerResult stub.
            val scanResult = mockk<SecurityScannerResult>()
            every { scanResult.isSecure } returns false
            vm.uiState.update { it.copy(txScanStatus = TransactionScanStatus.Scanned(scanResult)) }
            vm.joinKeySign()
            assertTrue(vm.uiState.value.showScanningWarning)

            vm.dismissScanningWarning()

            assertFalse(vm.uiState.value.showScanningWarning)
        }

    /** Verifies hasFastSign is true when isVaultHasFastSignById returns true. */
    @Test
    fun `hasFastSign is true when isVaultHasFastSignById returns true`() =
        runTest(testDispatcher) {
            coEvery { isVaultHasFastSignById(VAULT_ID) } returns true

            val vm = createViewModel()
            advanceUntilIdle()

            assertTrue(vm.uiState.value.hasFastSign)
        }

    /**
     * Verifies joinKeySign forwards the QR_CODE init type and the route ids to launchKeysign when
     * both consents are given.
     */
    @Test
    fun `joinKeySign launches QR keysign with vault and tx ids when consents are given`() =
        runTest(testDispatcher) {
            coEvery { transactionRepository.getTransaction(TX_ID) } returns mockk(relaxed = true)
            val vm = createViewModel()
            vm.checkConsentAddress(true)
            vm.checkConsentAmount(true)

            vm.joinKeySign()

            assertNull(vm.uiState.value.errorText)
            coVerify { launchKeysign(KeysignInitType.QR_CODE, TX_ID, any(), any(), VAULT_ID) }
        }

    /**
     * Verifies the mapper's output is propagated into uiState.transaction. We have to feed the
     * repository a non-null Transaction so loadTransaction reaches the mapper.
     */
    @Test
    fun `transaction srcAddress and dstAddress reflect mapper output`() =
        runTest(testDispatcher) {
            val tx = mockk<Transaction>(relaxed = true)
            val expected = TransactionDetailsUiModel(srcAddress = "0xSRC", dstAddress = "0xDST")
            coEvery { transactionRepository.getTransaction(TX_ID) } returns tx
            coEvery { mapTransactionToUiModel(tx) } returns expected

            val vm = createViewModel()
            advanceUntilIdle()

            assertEquals("0xSRC", vm.uiState.value.transaction.srcAddress)
            assertEquals("0xDST", vm.uiState.value.transaction.dstAddress)
        }

    private companion object {
        const val VAULT_ID = "vault-1"
        const val TX_ID = "tx-1"
    }
}
