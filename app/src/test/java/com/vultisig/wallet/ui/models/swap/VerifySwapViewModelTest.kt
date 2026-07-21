@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.swap

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapTransaction
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.securityscanner.SecurityScannerContract
import com.vultisig.wallet.data.usecases.IsVaultHasFastSignByIdUseCase
import com.vultisig.wallet.ui.models.keysign.KeysignInitType
import com.vultisig.wallet.ui.models.mappers.SwapTransactionToUiModelMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.util.LaunchKeysignUseCase
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Unit tests for [VerifySwapViewModel], focused on the consent gate that decides whether signing
 * may start.
 *
 * The gate has a single source of truth, [VerifySwapUiModel.hasAllConsents]: signing must launch on
 * exactly the same condition the Continue button enables on. These tests pin that contract — in
 * particular the no-approval relaxation (`consentAllowance || !hasConsentAllowance`), which a
 * parallel re-derivation inside `keysign()` previously dropped.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
internal class VerifySwapViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var navigator: Navigator<Destination>
    private lateinit var mapTransactionToUiModel: SwapTransactionToUiModelMapper
    private lateinit var swapTransactionRepository: SwapTransactionRepository
    private lateinit var vaultPasswordRepository: VaultPasswordRepository
    private lateinit var launchKeysign: LaunchKeysignUseCase
    private lateinit var isVaultHasFastSignById: IsVaultHasFastSignByIdUseCase
    private lateinit var securityScannerService: SecurityScannerContract
    private lateinit var vaultRepository: VaultRepository
    private lateinit var inboundHaltPreflight: SwapInboundHaltPreflight

    /** Sets up mocks and test dispatcher before each test. */
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { any<SavedStateHandle>().toRoute<Route.VerifySwap>() } returns
            Route.VerifySwap(vaultId = VAULT_ID, transactionId = TX_ID)
        navigator = mockk(relaxed = true)
        mapTransactionToUiModel = mockk(relaxed = true)
        swapTransactionRepository = mockk(relaxed = true)
        vaultPasswordRepository = mockk(relaxed = true)
        launchKeysign = mockk(relaxed = true)
        isVaultHasFastSignById = mockk(relaxed = true)
        securityScannerService = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        inboundHaltPreflight = mockk(relaxed = true)
        coEvery { isVaultHasFastSignById(any()) } returns false
        coEvery { vaultRepository.get(VAULT_ID) } returns mockk<Vault>(relaxed = true)
    }

    /** Cleans up mocks and resets test dispatcher after each test. */
    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        VerifySwapViewModel(
            savedStateHandle = SavedStateHandle(),
            navigator = navigator,
            mapTransactionToUiModel = mapTransactionToUiModel,
            swapTransactionRepository = swapTransactionRepository,
            vaultPasswordRepository = vaultPasswordRepository,
            launchKeysign = launchKeysign,
            isVaultHasFastSignById = isVaultHasFastSignById,
            securityScannerService = securityScannerService,
            vaultRepository = vaultRepository,
            inboundHaltPreflight = inboundHaltPreflight,
        )

    /**
     * Drives `init` deterministically: a non-EVM (THORChain) swap keeps the security scanner on its
     * early-return path so `txScanStatus` stays `NotStarted` and `joinKeySign()` proceeds straight
     * to the consent gate. [approvalRequired] mirrors production wiring — the mapper's
     * `hasConsentAllowance` and the VM's `consentAllowance` initializer both derive from it.
     */
    private fun givenSwap(approvalRequired: Boolean) {
        val tx =
            mockk<SwapTransaction>(relaxed = true) {
                every { isApprovalRequired } returns approvalRequired
                every { memo } returns null
                every { srcToken } returns
                    mockk<Coin>(relaxed = true).apply { every { chain } returns Chain.ThorChain }
                every { dstToken } returns
                    mockk<Coin>(relaxed = true).apply { every { chain } returns Chain.ThorChain }
            }
        coEvery { swapTransactionRepository.getTransaction(TX_ID) } returns tx
        coEvery { mapTransactionToUiModel(tx) } returns
            SwapTransactionUiModel(hasConsentAllowance = approvalRequired)
    }

    /** Missing consents block signing: a consent error is surfaced and no keysign is launched. */
    @Test
    fun `joinKeySign surfaces a consent error and launches nothing when consents are missing`() =
        runTest(testDispatcher) {
            givenSwap(approvalRequired = true)
            val vm = createViewModel()
            advanceUntilIdle()

            vm.joinKeySign()

            vm.state.value.errorText.shouldNotBeNull()
            coVerify(exactly = 0) { launchKeysign(any(), any(), any(), any(), any()) }
        }

    /** All three consents confirmed on an approval-required swap launch the swap keysign. */
    @Test
    fun `joinKeySign launches the swap keysign once all consents are confirmed`() =
        runTest(testDispatcher) {
            givenSwap(approvalRequired = true)
            val vm = createViewModel()
            advanceUntilIdle()

            vm.consentAmount(true)
            vm.consentReceiveAmount(true)
            vm.consentAllowance(true)

            vm.joinKeySign()

            vm.state.value.errorText.shouldBeNull()
            vm.state.value.isSigning shouldBe false
            coVerify {
                launchKeysign(
                    KeysignInitType.QR_CODE,
                    TX_ID,
                    any(),
                    Route.Keysign.Keysign.TxType.Swap,
                    VAULT_ID,
                )
            }
        }

    /**
     * Regression for the consent gate having two definitions. A swap that needs no token approval
     * carries `hasConsentAllowance = false`, so `hasAllConsents` is satisfied by amount + receive
     * alone (the allowance term relaxes via `|| !hasConsentAllowance`). With the allowance flag
     * explicitly unset, signing must still launch — proving `keysign()` reads `hasAllConsents`
     * rather than a stricter parallel condition that would dead-end here.
     */
    @Test
    fun `joinKeySign launches the swap keysign for a no-approval swap with the allowance flag unset`() =
        runTest(testDispatcher) {
            givenSwap(approvalRequired = false)
            val vm = createViewModel()
            advanceUntilIdle()

            vm.consentAmount(true)
            vm.consentReceiveAmount(true)
            vm.consentAllowance(false)

            vm.joinKeySign()

            vm.state.value.errorText.shouldBeNull()
            coVerify {
                launchKeysign(
                    KeysignInitType.QR_CODE,
                    TX_ID,
                    any(),
                    Route.Keysign.Keysign.TxType.Swap,
                    VAULT_ID,
                )
            }
        }

    /** The biometric Fast Sign trigger honors the same consent gate. */
    @Test
    fun `authFastSign launches the swap keysign once all consents are confirmed`() =
        runTest(testDispatcher) {
            givenSwap(approvalRequired = true)
            val vm = createViewModel()
            advanceUntilIdle()

            vm.consentAmount(true)
            vm.consentReceiveAmount(true)
            vm.consentAllowance(true)

            vm.authFastSign()

            vm.state.value.errorText.shouldBeNull()
            coVerify {
                launchKeysign(
                    KeysignInitType.BIOMETRY,
                    TX_ID,
                    any(),
                    Route.Keysign.Keysign.TxType.Swap,
                    VAULT_ID,
                )
            }
        }

    /** A live inbound halt aborts paired signing and surfaces the existing trading-halted error. */
    @Test
    fun `joinKeySign blocks signing when the source chain becomes halted`() =
        runTest(testDispatcher) {
            givenSwap(approvalRequired = false)
            coEvery { inboundHaltPreflight.assertSourceChainNotHalted(any()) } throws
                SwapException.TradingHalted("halted")
            val vm = createViewModel()
            advanceUntilIdle()

            vm.consentAmount(true)
            vm.consentReceiveAmount(true)
            vm.joinKeySign()
            advanceUntilIdle()

            vm.state.value.errorText.shouldNotBeNull()
            vm.state.value.isSigning shouldBe false
            coVerify(exactly = 0) { launchKeysign(any(), any(), any(), any(), any()) }
        }

    /** The biometric path runs through the same live inbound safety gate. */
    @Test
    fun `authFastSign blocks signing when inbound status cannot be verified`() =
        runTest(testDispatcher) {
            givenSwap(approvalRequired = false)
            coEvery { inboundHaltPreflight.assertSourceChainNotHalted(any()) } throws
                IllegalStateException("network unavailable")
            val vm = createViewModel()
            advanceUntilIdle()

            vm.consentAmount(true)
            vm.consentReceiveAmount(true)
            vm.authFastSign()
            advanceUntilIdle()

            vm.state.value.errorText.shouldNotBeNull()
            vm.state.value.isSigning shouldBe false
            coVerify(exactly = 0) { launchKeysign(any(), any(), any(), any(), any()) }
        }

    private companion object {
        const val VAULT_ID = "vault-1"
        const val TX_ID = "tx-1"
    }
}
