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
 * The gate weighs the fee alone, against whichever balance the fee is denominated in. A QBTC vote's
 * whole cost is its fee, and an account that cannot cover it must never launch the ceremony — the
 * chain rejects the vote at broadcast — so an unresolved balance there fails closed. A Kamino USDC
 * deposit pays its Solana fee in SOL, so the fee is checked against the native balance instead, and
 * an unresolved balance leaves Sign enabled rather than blocking a fundable transaction. A withdraw
 * stages an amount that arrives rather than leaves, so the amount is never added to the fee.
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
     * Stubs the transaction the VM loads in `init`. [feeValue] alone drives the affordability
     * threshold; [sentValue] is what the transaction stages as its amount, which the gate must not
     * add to the fee — see the withdraw test below for why.
     */
    private fun givenTransaction(
        chain: Chain,
        feeValue: Long,
        srcTicker: String = "QBTC",
        feeUnit: String = srcTicker,
        sentValue: Long = 0,
        chargedFeeValue: Long? = null,
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
                // Stubbed explicitly, including the null: a relaxed mock answers an unstubbed
                // nullable with a mock of its own, which would make every flow look like one that
                // distinguishes its charge from its quote.
                every { chargedFees } returns
                    chargedFeeValue?.let { TokenValue(BigInteger.valueOf(it), feeUnit, 8) }
                every { srcTokenValue } returns
                    TokenValue(BigInteger.valueOf(sentValue), srcTicker, 8)
            }
        coEvery { depositTransactionRepository.getTransaction(TX_ID) } returns tx
    }

    /** The balance every read of the source address resolves to. */
    private fun givenBalance(value: Long) {
        coEvery { balanceRepository.getBalanceOrNull(SRC_ADDRESS, any()) } returns
            BigInteger.valueOf(value)
    }

    /**
     * A balance that could not be read at all. The repository reports this as null whether the node
     * threw or answered an error — `SolanaApi.getBalance`'s zero-for-everything is exactly what
     * [BalanceRepository.getBalanceOrNull] exists to keep out of this gate.
     */
    private fun givenUnreadableBalance() {
        coEvery { balanceRepository.getBalanceOrNull(SRC_ADDRESS, any()) } returns null
    }

    /** A QBTC balance that covers the fee leaves Sign enabled and lets the keysign launch. */
    @Test
    fun `confirm launches keysign when QBTC balance covers the fee`() =
        runTest(testDispatcher) {
            givenTransaction(Chain.Qbtc, feeValue = 10)
            givenBalance(100)
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
            givenBalance(5)
            val vm = createViewModel()
            advanceUntilIdle()

            vm.state.value.hasEnoughBalance.shouldBeFalse()
            vm.state.value.insufficientBalanceError.shouldNotBeNull()

            vm.confirm()

            coVerify(exactly = 0) { launchKeysign(any(), any(), any(), any(), any()) }
        }

    /**
     * Regression for #5044: when the QBTC balance cannot be read, the gate must fail closed — Sign
     * stays disabled and no keysign launches — rather than defaulting to the affordable
     * `hasEnoughBalance = true`.
     */
    @Test
    fun `confirm fails closed and blocks keysign when QBTC balance lookup fails`() =
        runTest(testDispatcher) {
            givenTransaction(Chain.Qbtc, feeValue = 10)
            givenUnreadableBalance()
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
            givenUnreadableBalance()
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
            coEvery {
                balanceRepository.getBalanceOrNull(SRC_ADDRESS, match { !it.isNativeToken })
            } returns BigInteger.valueOf(2_500_000_000)
            coEvery {
                balanceRepository.getBalanceOrNull(SRC_ADDRESS, match { it.isNativeToken })
            } returns BigInteger.valueOf(1_320_000)
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
            givenBalance(50_000_000)
            val vm = createViewModel()
            advanceUntilIdle()

            vm.state.value.hasEnoughBalance.shouldBeTrue()
            vm.state.value.insufficientBalanceError.shouldBeNull()

            vm.confirm()

            coVerify { launchKeysign(any(), any(), any(), any(), any()) }
        }

    /**
     * A Kamino SOL withdraw stages the withdrawn lamports as its amount, and its fee is denominated
     * in that same SOL — but the amount arrives from the vault rather than leaving the wallet, so
     * only the fee has to be affordable. Adding the amount disabled Sign on every transaction of
     * this shape whenever the position was larger than the liquid balance: a stake withdraw
     * (`SolanaStakingPositionsViewModel`), Move Stake, Finish Move and a Cosmos undelegate all
     * stage it the same way.
     */
    @Test
    fun `a withdraw is signable on the fee alone though its amount dwarfs the balance`() =
        runTest(testDispatcher) {
            givenTransaction(
                chain = Chain.Solana,
                feeValue = 1_320_000,
                srcTicker = "SOL",
                sentValue = 5_000_000_000, // 5 SOL coming back out of the position
            )
            givenBalance(2_000_000) // covers the fee, nothing like the amount

            val vm = createViewModel()
            advanceUntilIdle()

            vm.state.value.hasEnoughBalance.shouldBeTrue()
            vm.state.value.insufficientBalanceError.shouldBeNull()

            vm.confirm()

            coVerify { launchKeysign(any(), any(), any(), any(), any()) }
        }

    /**
     * A Kamino fee row is padded to the 1,000,000-lamport placeholder both platforms display where
     * the runtime deducts 5,000, so the quote is not the question to ask of a balance.
     *
     * The padding is precisely what a Max deposit leaves in the wallet — `spendableBalance`
     * reserves it and the chain does not take it — so weighing the quote disabled Sign on the
     * withdraw that exits such a position, for a charge the wallet could pay twice over.
     */
    @Test
    fun `a deposit is signable when the balance covers the charge but not the padded quote`() =
        runTest(testDispatcher) {
            givenTransaction(
                chain = Chain.Solana,
                feeValue = 1_400_000, // quoted: padded base + the withdraw priority fee
                srcTicker = "SOL",
                chargedFeeValue = 405_000, // charged: 5,000 signature fee + the same priority fee
            )
            givenBalance(995_000)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.state.value.hasEnoughBalance.shouldBeTrue()
            vm.state.value.insufficientBalanceError.shouldBeNull()

            vm.confirm()

            coVerify { launchKeysign(any(), any(), any(), any(), any()) }
        }

    /**
     * Dropping the padding is not dropping the check: a wallet short of the charge itself is still
     * refused, and the charge can exceed the quote — a Kamino token deposit pays rent in SOL that
     * neither device quotes, because a co-signer cannot derive it from the relayed payload.
     */
    @Test
    fun `a deposit is blocked when the balance falls short of a charge larger than the quote`() =
        runTest(testDispatcher) {
            givenTransaction(
                chain = Chain.Solana,
                feeValue = 1_320_000,
                srcTicker = "USDC",
                feeUnit = "SOL",
                chargedFeeValue = 8_663_360, // the fee plus share-account and user-state rent
            )
            givenBalance(2_000_000)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.state.value.hasEnoughBalance.shouldBeFalse()
            vm.state.value.insufficientBalanceError.shouldNotBeNull()
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
