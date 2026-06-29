@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.deposit

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.usecases.IsVaultHasFastSignByIdUseCase
import com.vultisig.wallet.data.utils.NetworkErrorKind
import com.vultisig.wallet.data.utils.NetworkException
import com.vultisig.wallet.ui.models.mappers.DepositTransactionToUiModelMapper
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.util.LaunchKeysignUseCase
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
 * Unit tests for [VerifyDepositViewModel], pinning the QBTC fee-affordability gate that decides
 * whether a deposit/vote keysign may start (#5044).
 *
 * The gate guards a fund-critical path: a QBTC account that can't cover the network fee must never
 * be allowed to launch the keysign ceremony, because the chain rejects the resulting vote at
 * broadcast. These tests cover the three branches that matter — affordable, unaffordable, and an
 * unresolved balance (which must fail closed rather than default to affordable).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
internal class VerifyDepositViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var mapTransactionToUiModel: DepositTransactionToUiModelMapper
    private lateinit var depositTransactionRepository: DepositTransactionRepository
    private lateinit var balanceRepository: BalanceRepository
    private lateinit var vaultPasswordRepository: VaultPasswordRepository
    private lateinit var launchKeysign: LaunchKeysignUseCase
    private lateinit var isVaultHasFastSignById: IsVaultHasFastSignByIdUseCase

    /** Sets up mocks and test dispatcher before each test. */
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { any<SavedStateHandle>().toRoute<Route.VerifyDeposit>() } returns
            Route.VerifyDeposit(vaultId = VAULT_ID, transactionId = TX_ID)
        mapTransactionToUiModel = mockk(relaxed = true)
        depositTransactionRepository = mockk(relaxed = true)
        balanceRepository = mockk(relaxed = true)
        vaultPasswordRepository = mockk(relaxed = true)
        launchKeysign = mockk(relaxed = true)
        isVaultHasFastSignById = mockk(relaxed = true)
        // A relaxed mock can't synthesize the non-null DepositTransactionUiModel for this
        // suspend function-type mapper, so its invoke must be stubbed explicitly; otherwise the
        // null return throws in init before checkFeeAffordability runs and the gate never trips.
        coEvery { mapTransactionToUiModel(any()) } returns DepositTransactionUiModel()
        coEvery { isVaultHasFastSignById(any()) } returns false
    }

    /** Cleans up mocks and resets test dispatcher after each test. */
    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        VerifyDepositViewModel(
            savedStateHandle = SavedStateHandle(),
            mapTransactionToUiModel = mapTransactionToUiModel,
            depositTransactionRepository = depositTransactionRepository,
            balanceRepository = balanceRepository,
            vaultPasswordRepository = vaultPasswordRepository,
            launchKeysign = launchKeysign,
            isVaultHasFastSignById = isVaultHasFastSignById,
        )

    /**
     * Stubs the transaction the VM loads in `init`. The required spend is `estimatedFees +
     * srcTokenValue`, so [feeValue] alone (with a zero send amount) drives the affordability
     * threshold.
     */
    private fun givenTransaction(chain: Chain, feeValue: Long) {
        val coin =
            mockk<Coin>(relaxed = true).apply {
                every { this@apply.chain } returns chain
                every { ticker } returns "QBTC"
            }
        val tx =
            mockk<DepositTransaction>(relaxed = true).apply {
                every { srcToken } returns coin
                every { srcAddress } returns SRC_ADDRESS
                every { estimatedFees } returns TokenValue(BigInteger.valueOf(feeValue), "QBTC", 8)
                every { srcTokenValue } returns TokenValue(BigInteger.ZERO, "QBTC", 8)
            }
        coEvery { depositTransactionRepository.getTransaction(TX_ID) } returns tx
    }

    /** A QBTC balance that covers the fee leaves Sign enabled and lets the keysign launch. */
    @Test
    fun `confirm launches keysign when QBTC balance covers the fee`() =
        runTest(testDispatcher) {
            givenTransaction(Chain.Qbtc, feeValue = 10)
            every { balanceRepository.getTokenValue(SRC_ADDRESS, any()) } returns
                flowOf(TokenValue(BigInteger.valueOf(100), "QBTC", 8))
            val vm = createViewModel()
            advanceUntilIdle()

            vm.state.value.hasEnoughBalance.shouldBeTrue()
            vm.state.value.insufficientBalanceError.shouldBeNull()

            vm.confirm()

            coVerify { launchKeysign(any(), any(), any(), any(), any()) }
        }

    /** A QBTC balance below the fee disables Sign and blocks the keysign from launching. */
    @Test
    fun `confirm blocks keysign when QBTC balance cannot cover the fee`() =
        runTest(testDispatcher) {
            givenTransaction(Chain.Qbtc, feeValue = 10)
            every { balanceRepository.getTokenValue(SRC_ADDRESS, any()) } returns
                flowOf(TokenValue(BigInteger.valueOf(5), "QBTC", 8))
            val vm = createViewModel()
            advanceUntilIdle()

            vm.state.value.hasEnoughBalance.shouldBeFalse()
            vm.state.value.insufficientBalanceError.shouldNotBeNull()

            vm.confirm()

            coVerify(exactly = 0) { launchKeysign(any(), any(), any(), any(), any()) }
        }

    /**
     * Regression for #5044: when the QBTC balance lookup fails (`NetworkException`, a
     * `RuntimeException` thrown by the RPC layer), the gate must fail closed — Sign stays disabled
     * and no keysign launches — rather than defaulting to the affordable `hasEnoughBalance = true`.
     */
    @Test
    fun `confirm fails closed and blocks keysign when QBTC balance lookup fails`() =
        runTest(testDispatcher) {
            givenTransaction(Chain.Qbtc, feeValue = 10)
            every { balanceRepository.getTokenValue(SRC_ADDRESS, any()) } returns
                flow { throw NetworkException(0, "offline", NetworkErrorKind.NoConnectivity) }
            val vm = createViewModel()
            advanceUntilIdle()

            vm.state.value.hasEnoughBalance.shouldBeFalse()
            vm.state.value.insufficientBalanceError.shouldNotBeNull()

            vm.confirm()

            coVerify(exactly = 0) { launchKeysign(any(), any(), any(), any(), any()) }
        }

    /** Non-QBTC deposits skip the balance gate entirely and remain signable. */
    @Test
    fun `confirm launches keysign for a non-QBTC deposit without a balance check`() =
        runTest(testDispatcher) {
            givenTransaction(Chain.ThorChain, feeValue = 10)
            val vm = createViewModel()
            advanceUntilIdle()

            vm.state.value.hasEnoughBalance.shouldBeTrue()

            vm.confirm()

            coVerify(exactly = 0) { balanceRepository.getTokenValue(any(), any()) }
            coVerify { launchKeysign(any(), any(), any(), any(), any()) }
        }

    private companion object {
        const val VAULT_ID = "vault-1"
        const val TX_ID = "tx-1"
        const val SRC_ADDRESS = "qbtc-src-address"
    }
}
