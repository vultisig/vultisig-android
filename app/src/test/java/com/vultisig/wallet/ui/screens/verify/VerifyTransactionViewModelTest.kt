@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.screens.verify

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.securityscanner.SecurityScannerContract
import com.vultisig.wallet.data.securityscanner.SecurityScannerFeaturesType
import com.vultisig.wallet.data.securityscanner.SecurityScannerResult
import com.vultisig.wallet.data.securityscanner.SecurityScannerSupport
import com.vultisig.wallet.data.securityscanner.SecurityScannerTransaction
import com.vultisig.wallet.data.usecases.IsVaultHasFastSignByIdUseCase
import com.vultisig.wallet.ui.models.TransactionDetailsUiModel
import com.vultisig.wallet.ui.models.TransactionScanStatus
import com.vultisig.wallet.ui.models.VerifyTransactionViewModel
import com.vultisig.wallet.ui.models.keysign.KeysignInitType
import com.vultisig.wallet.ui.models.mappers.TransactionToUiModelMapper
import com.vultisig.wallet.ui.models.swap.ValuedToken
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.util.LaunchKeysignUseCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Unit tests for [VerifyTransactionViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
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
        // Default loadTransaction() onto the happy path. Without a stub the relaxed mock returns
        // null, the safeLaunch body throws, and onError suspends through Navigator.back() on a
        // relaxed mock — that suspension races the test body on CI and surfaces as
        // UncaughtExceptionsBeforeTest. Per-test stubs override this (last-wins in MockK).
        coEvery { transactionRepository.getTransaction(any()) } returns mockk(relaxed = true)
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
            vm.uiState.value.consentAddress.shouldBeTrue()
        }

    /** Verifies checkConsentAmount sets consentAmount true. */
    @Test
    fun `checkConsentAmount sets consentAmount true`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.checkConsentAmount(true)
            vm.uiState.value.consentAmount.shouldBeTrue()
        }

    /** Verifies hasAllConsents is false when only address consent is given. */
    @Test
    fun `hasAllConsents is false when only address consent is given`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.checkConsentAddress(true)
            vm.uiState.value.hasAllConsents.shouldBeFalse()
        }

    /** Verifies hasAllConsents is true when both consents are given. */
    @Test
    fun `hasAllConsents is true when both consents are given`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.checkConsentAddress(true)
            vm.checkConsentAmount(true)
            vm.uiState.value.hasAllConsents.shouldBeTrue()
        }

    /** Verifies joinKeySign sets errorText when consents not checked. */
    @Test
    fun `joinKeySign sets errorText when consents not checked`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.joinKeySign()
            vm.uiState.value.errorText.shouldNotBeNull()
        }

    /** Verifies dismissError clears errorText. */
    @Test
    fun `dismissError clears errorText`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.joinKeySign()
            vm.dismissError()
            vm.uiState.value.errorText.shouldBeNull()
        }

    /**
     * Verifies dismissScanningWarning clears showScanningWarning after a flagged scan.
     *
     * Stubs securityScannerService so the full scanTransaction() pipeline runs and produces a
     * Scanned(unsafe) status: getSupportedChainsByFeature returns a list containing Ethereum,
     * isSecurityServiceEnabled returns true, scanTransaction returns an unsafe result. Then
     * joinKeySign triggers showScanningWarning via handleSigningFlowCommon.
     */
    @Test
    fun `dismissScanningWarning clears showScanningWarning after a flagged scan`() =
        runTest(testDispatcher) {
            // Build a transaction whose chain is Ethereum so the support check passes.
            val coin = mockk<Coin>(relaxed = true)
            every { coin.chain } returns Chain.Ethereum
            val tx = mockk<Transaction>(relaxed = true)
            every { tx.token } returns coin
            coEvery { transactionRepository.getTransaction(TX_ID) } returns tx

            // Stub the security scanner so scanning runs and returns an unsafe result.
            val support =
                SecurityScannerSupport(
                    provider = "test",
                    feature =
                        listOf(
                            SecurityScannerSupport.Feature(
                                chains = listOf(Chain.Ethereum),
                                featureType = SecurityScannerFeaturesType.SCAN_TRANSACTION,
                            )
                        ),
                )
            every { securityScannerService.getSupportedChainsByFeature() } returns listOf(support)
            coEvery { securityScannerService.isSecurityServiceEnabled() } returns true
            coEvery {
                securityScannerService.createSecurityScannerTransaction(any<Transaction>())
            } returns mockk<SecurityScannerTransaction>(relaxed = true)
            val scanResult = mockk<SecurityScannerResult>()
            every { scanResult.isSecure } returns false
            coEvery { securityScannerService.scanTransaction(any()) } returns scanResult

            val vm = createViewModel()
            advanceUntilIdle()

            // loadTransaction() and scanTransaction() each hop through Dispatchers.IO,
            // which runTest's virtual time cannot advance. Yield real time on a real
            // dispatcher until the scan status finalizes before driving joinKeySign().
            withContext(Dispatchers.Default) {
                withTimeout(2_000) {
                    vm.uiState.first { it.txScanStatus is TransactionScanStatus.Scanned }
                }
            }

            vm.checkConsentAddress(true)
            vm.checkConsentAmount(true)
            vm.joinKeySign()
            vm.uiState.value.showScanningWarning.shouldBeTrue()

            vm.dismissScanningWarning()

            vm.uiState.value.showScanningWarning.shouldBeFalse()
        }

    /** Verifies hasFastSign is true when isVaultHasFastSignById returns true. */
    @Test
    fun `hasFastSign is true when isVaultHasFastSignById returns true`() =
        runTest(testDispatcher) {
            coEvery { isVaultHasFastSignById(VAULT_ID) } returns true

            val vm = createViewModel()
            advanceUntilIdle()

            vm.uiState.value.hasFastSign.shouldBeTrue()
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

            vm.uiState.value.errorText.shouldBeNull()
            coVerify { launchKeysign(KeysignInitType.QR_CODE, TX_ID, any(), any(), VAULT_ID) }
        }

    /**
     * Verifies the mapper's output is propagated into uiState.transaction across the user-visible
     * fields. We feed the repository a non-null Transaction so loadTransaction reaches the mapper,
     * and assert that all relevant TransactionDetailsUiModel fields (addresses, fees, memo, token)
     * survive the loadTransaction copy that only overrides the *VaultName / dstAddressBookTitle
     * fields. Note: loadTransaction performs a withContext(Dispatchers.IO) hop for
     * vaultRepository.getAll(), so we suspend on uiState.first { ... } to await the post-hop
     * emission instead of relying on advanceUntilIdle (which only advances the test scheduler).
     */
    @Test
    fun `transaction fields reflect mapper output (srcAddress, dstAddress, fee, memo, token)`() =
        runTest(testDispatcher) {
            val tx = mockk<Transaction>(relaxed = true)
            val expectedToken = ValuedToken.Empty.copy(value = "1.5", fiatValue = "$3000")
            val expected =
                TransactionDetailsUiModel(
                    srcAddress = "0xSRC",
                    dstAddress = "0xDST",
                    networkFeeFiatValue = "$0.42",
                    networkFeeTokenValue = "0.0001 ETH",
                    memo = "hello memo",
                    token = expectedToken,
                )
            coEvery { transactionRepository.getTransaction(TX_ID) } returns tx
            coEvery { mapTransactionToUiModel(tx) } returns expected

            val vm = createViewModel()
            val state = vm.uiState.first { it.transaction.srcAddress.isNotEmpty() }

            val actual = state.transaction
            actual.srcAddress shouldBe "0xSRC"
            actual.dstAddress shouldBe "0xDST"
            actual.networkFeeFiatValue shouldBe "$0.42"
            actual.networkFeeTokenValue shouldBe "0.0001 ETH"
            actual.memo shouldBe "hello memo"
            actual.token shouldBe expectedToken
            // Token symbol (ticker) round-trips through the ValuedToken's underlying Coin.
            actual.token.token.ticker shouldBe "OM"
        }

    private companion object {
        const val VAULT_ID = "vault-1"
        const val TX_ID = "tx-1"
    }
}
