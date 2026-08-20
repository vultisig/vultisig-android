@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.deposit

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.AddressBookEntry
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.repositories.VaultRepository
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
import io.kotest.matchers.shouldBe
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
 * Unit tests for [VerifyDepositViewModel], pinning the fee-affordability gate that decides whether
 * a deposit keysign may start (#5044, #5607).
 *
 * Two shapes are covered because the amount and the fee are not always drawn from one balance. A
 * QBTC vote's whole cost is its fee, and an account that cannot cover it must never launch the
 * ceremony — the chain rejects the vote at broadcast — so an unresolved balance there fails closed.
 * A Kamino USDC deposit pays its Solana fee in SOL, so the fee is checked against the native
 * balance instead, and an unresolved balance leaves Sign enabled rather than blocking a fundable
 * transaction.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
internal class VerifyDepositViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var mapTransactionToUiModel: DepositTransactionToUiModelMapper
    private lateinit var depositTransactionRepository: DepositTransactionRepository
    private lateinit var balanceRepository: BalanceRepository
    private lateinit var vaultPasswordRepository: VaultPasswordRepository
    private lateinit var vaultRepository: VaultRepository
    private lateinit var addressBookRepository: AddressBookRepository
    private lateinit var chainAccountAddressRepository: ChainAccountAddressRepository
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
        vaultRepository = mockk(relaxed = true)
        addressBookRepository = mockk(relaxed = true)
        chainAccountAddressRepository = mockk(relaxed = true)
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
            vaultRepository = vaultRepository,
            addressBookRepository = addressBookRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
            ioDispatcher = testDispatcher,
            launchKeysign = launchKeysign,
            isVaultHasFastSignById = isVaultHasFastSignById,
        )

    /**
     * Stubs the transaction the VM loads in `init`. The required spend is `estimatedFees +
     * srcTokenValue`, so [feeValue] alone (with a zero send amount) drives the affordability
     * threshold.
     */
    private fun givenTransaction(
        chain: Chain,
        feeValue: Long,
        srcTicker: String = "QBTC",
        feeUnit: String = srcTicker,
    ) {
        val coin =
            mockk<Coin>(relaxed = true).apply {
                every { this@apply.chain } returns chain
                every { ticker } returns srcTicker
            }
        val tx =
            mockk<DepositTransaction>(relaxed = true).apply {
                every { srcToken } returns coin
                every { srcAddress } returns SRC_ADDRESS
                every { estimatedFees } returns TokenValue(BigInteger.valueOf(feeValue), feeUnit, 8)
                every { srcTokenValue } returns TokenValue(BigInteger.ZERO, srcTicker, 8)
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

    /**
     * A non-QBTC deposit is checked too (#5607) — but where QBTC fails closed, an unresolved
     * balance here leaves Sign enabled. The amount has already been sized against the same balance
     * by the form, so reading one flaky RPC call as "cannot afford" would block a transaction the
     * wallet can fund.
     */
    @Test
    fun `an unreadable balance leaves a non-QBTC deposit signable`() =
        runTest(testDispatcher) {
            givenTransaction(Chain.ThorChain, feeValue = 10)
            every { balanceRepository.getTokenValue(SRC_ADDRESS, any()) } returns
                flow { throw NetworkException(0, "offline", NetworkErrorKind.NoConnectivity) }
            val vm = createViewModel()
            advanceUntilIdle()

            vm.state.value.hasEnoughBalance.shouldBeTrue()
            vm.state.value.insufficientBalanceError.shouldBeNull()

            vm.confirm()

            coVerify { launchKeysign(any(), any(), any(), any(), any()) }
        }

    /**
     * A Kamino USDC deposit pays its Solana fee in SOL, so the fee and the amount come out of two
     * different balances. The gate has to read the one the fee is denominated in: a vault holding
     * USDC and no SOL passed this screen and the whole MPC ceremony before the node rejected it at
     * broadcast (#5607).
     */
    @Test
    fun `a deposit whose fee is paid in the native token is checked against the native balance`() =
        runTest(testDispatcher) {
            givenTransaction(
                chain = Chain.Solana,
                feeValue = 9_658_360,
                srcTicker = "USDC",
                feeUnit = "SOL",
            )
            // The USDC the deposit spends is there; the SOL its fee needs is not.
            every {
                balanceRepository.getTokenValue(SRC_ADDRESS, match { !it.isNativeToken })
            } returns flowOf(TokenValue(BigInteger.valueOf(2_500_000_000), "USDC", 6))
            every {
                balanceRepository.getTokenValue(SRC_ADDRESS, match { it.isNativeToken })
            } returns flowOf(TokenValue(BigInteger.valueOf(1_320_000), "SOL", 9))
            val vm = createViewModel()
            advanceUntilIdle()

            vm.state.value.hasEnoughBalance.shouldBeFalse()
            vm.state.value.insufficientBalanceError.shouldNotBeNull()

            vm.confirm()

            coVerify(exactly = 0) { launchKeysign(any(), any(), any(), any(), any()) }
        }

    /** The same deposit, on a wallet that does hold the SOL, stays signable. */
    @Test
    fun `a deposit is signable when the native balance covers the fee`() =
        runTest(testDispatcher) {
            givenTransaction(
                chain = Chain.Solana,
                feeValue = 9_658_360,
                srcTicker = "USDC",
                feeUnit = "SOL",
            )
            every { balanceRepository.getTokenValue(SRC_ADDRESS, any()) } returns
                flowOf(TokenValue(BigInteger.valueOf(50_000_000), "SOL", 9))
            val vm = createViewModel()
            advanceUntilIdle()

            vm.state.value.hasEnoughBalance.shouldBeTrue()
            vm.state.value.insufficientBalanceError.shouldBeNull()

            vm.confirm()

            coVerify { launchKeysign(any(), any(), any(), any(), any()) }
        }

    /**
     * Stubs a non-QBTC (ThorChain) deposit so the balance gate is skipped and the From/To label
     * resolution (#5301) is the only work `init` does. [destination] is the raw dstAddress; [coins]
     * are the current vault's enabled coins used to resolve the destination-vault label.
     */
    private fun givenThorDepositWithVault(
        destination: String,
        vaultName: String,
        coins: List<Coin> = emptyList(),
    ) {
        val coin = mockk<Coin>(relaxed = true).apply { every { chain } returns Chain.ThorChain }
        val tx =
            mockk<DepositTransaction>(relaxed = true).apply {
                every { srcToken } returns coin
                every { vaultId } returns VAULT_ID
                every { dstAddress } returns destination
                every { estimatedFees } returns TokenValue(BigInteger.ZERO, "RUNE", 8)
                every { srcTokenValue } returns TokenValue(BigInteger.ZERO, "RUNE", 8)
            }
        coEvery { depositTransactionRepository.getTransaction(TX_ID) } returns tx
        coEvery { vaultRepository.getAll() } returns
            listOf(Vault(id = VAULT_ID, name = vaultName, coins = coins))
    }

    private fun thorCoin(address: String): Coin =
        mockk<Coin>(relaxed = true).apply {
            every { chain } returns Chain.ThorChain
            every { this@apply.address } returns address
        }

    /** From resolves to the signing vault's name; with no destination there is no To label. */
    @Test
    fun `resolves the source vault name for the From label`() =
        runTest(testDispatcher) {
            givenThorDepositWithVault(destination = "", vaultName = "Main Vault")

            val vm = createViewModel()
            advanceUntilIdle()
            val tx = vm.state.value.depositTransactionUiModel

            tx.srcVaultName shouldBe "Main Vault"
            tx.dstVaultName.shouldBeNull()
            tx.dstAddressBookTitle.shouldBeNull()
        }

    /**
     * A destination owned by a local vault (a self-operated node) resolves To to that vault name.
     */
    @Test
    fun `resolves a local destination vault name for the To label`() =
        runTest(testDispatcher) {
            val node = "thor1node0000000000000000000000000000000node"
            givenThorDepositWithVault(
                destination = node,
                vaultName = "Main Vault",
                coins = listOf(thorCoin(node)),
            )

            val vm = createViewModel()
            advanceUntilIdle()
            val tx = vm.state.value.depositTransactionUiModel

            tx.dstVaultName shouldBe "Main Vault"
            tx.dstAddressBookTitle.shouldBeNull()
        }

    /** When no local vault owns the destination, To falls back to an address-book title. */
    @Test
    fun `falls back to the address book title when no vault owns the destination`() =
        runTest(testDispatcher) {
            val external = "thor1external000000000000000000000000external"
            givenThorDepositWithVault(destination = external, vaultName = "Main Vault")
            // The pubkey-derivation fallback in resolveDstVaultName must not match either.
            coEvery { chainAccountAddressRepository.getAddress(any<Chain>(), any()) } returns
                ("thor1someother" to "")
            coEvery { addressBookRepository.getEntry(Chain.ThorChain.id, external) } returns
                AddressBookEntry(Chain.ThorChain, external, "Savings")

            val vm = createViewModel()
            advanceUntilIdle()
            val tx = vm.state.value.depositTransactionUiModel

            tx.dstVaultName.shouldBeNull()
            tx.dstAddressBookTitle shouldBe "Savings"
        }

    private companion object {
        const val VAULT_ID = "vault-1"
        const val TX_ID = "tx-1"
        const val SRC_ADDRESS = "qbtc-src-address"
    }
}
