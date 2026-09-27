package com.vultisig.wallet.ui.models.defi

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import com.vultisig.wallet.data.blockchain.ton.TonLiquidPoolState
import com.vultisig.wallet.data.blockchain.ton.TonLiquidStakingService
import com.vultisig.wallet.data.blockchain.ton.Tonstakers
import com.vultisig.wallet.data.crypto.ton.TonMessageBodyDecoder
import com.vultisig.wallet.data.crypto.ton.TonMessageBodyIntent
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.OPERATION_TONSTAKERS_STAKE
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TokenBalance
import com.vultisig.wallet.data.models.TokenBalanceAndPrice
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.models.deposit.DepositGasFeeHelper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class TonLiquidStakeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var vaultRepository: VaultRepository
    private lateinit var liquidStakingService: TonLiquidStakingService
    private lateinit var accountsRepository: AccountsRepository
    private lateinit var balanceRepository: BalanceRepository
    private lateinit var blockChainSpecificRepository: BlockChainSpecificRepository
    private lateinit var depositGasFeeHelper: DepositGasFeeHelper
    private lateinit var transactionRepository: DepositTransactionRepository
    private lateinit var navigator: Navigator<Destination>

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        vaultRepository = mockk(relaxed = true)
        liquidStakingService = mockk()
        accountsRepository = mockk(relaxed = true)
        balanceRepository = mockk(relaxed = true)
        blockChainSpecificRepository = mockk(relaxed = true)
        depositGasFeeHelper = mockk(relaxed = true)
        transactionRepository = mockk(relaxed = true)
        navigator = mockk(relaxed = true)

        coEvery { vaultRepository.get(VAULT_ID) } returns VAULT
        coEvery { depositGasFeeHelper.calculateGasFee(VAULT_ID, any(), any(), any()) } returns
            GAS_FEE
        coEvery { depositGasFeeHelper.getFeesFiatValue(any(), any(), any(), any()) } returns
            EstimatedGasFee(
                formattedTokenValue = "0.05 GRAM",
                formattedFiatValue = "$0.17",
                tokenValue = GAS_FEE,
                fiatValue = FiatValue(BigDecimal("0.17"), "USD"),
            )
        coEvery { liquidStakingService.getPoolState() } returns POOL_STATE
        coEvery { accountsRepository.loadAddress(VAULT_ID, Chain.Ton) } returns
            flowOf(
                Address(
                    chain = Chain.Ton,
                    address = TON_ADDRESS,
                    accounts =
                        listOf(
                            Account(
                                token = TON_COIN,
                                tokenValue = null,
                                fiatValue = null,
                                price = null,
                            )
                        ),
                )
            )
        stubNativeBalance(nanoTon = 10_050_000_000L)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `fails closed when the vault is missing`() = runTest {
        coEvery { vaultRepository.get(VAULT_ID) } returns null

        val state = createViewModel().state.value

        state.isReady.shouldBeFalse()
        state.errorMessage.shouldNotBeNull()
    }

    @Test
    fun `fails closed when the pool state cannot be read`() = runTest {
        coEvery { liquidStakingService.getPoolState() } returns null

        val state = createViewModel().state.value

        state.isReady.shouldBeFalse()
        state.errorMessage.shouldNotBeNull()
    }

    @Test
    fun `loads the stakeable balance net of gas and the pool minimum plus the deposit fee`() =
        runTest {
            val state = createViewModel().state.value

            state.isReady.shouldBeTrue()
            state.errorMessage.shouldBeNull()
            state.ticker shouldBe "GRAM"
            // 10.05 GRAM less the 0.05 GRAM gas reserve.
            state.stakeableBalance.compareTo(BigDecimal("10")) shouldBe 0
            // 1 GRAM pool minimum + 1 GRAM deposit fee.
            state.minimumDeposit.stripTrailingZeros() shouldBe BigDecimal("2")
            state.depositFee.stripTrailingZeros() shouldBe BigDecimal("1")
            state.apy shouldBe "13.36%"
            state.isDepositClosed.shouldBeFalse()
        }

    @Test
    fun `a pool that is not taking deposits closes the form`() = runTest {
        coEvery { liquidStakingService.getPoolState() } returns
            POOL_STATE.copy(isDepositOpen = false)
        val vm = createViewModel()

        vm.state.value.isDepositClosed.shouldBeTrue()

        vm.amountFieldState.setTextAndPlaceCursorAtEnd("5")
        vm.submit()
        coVerify(exactly = 0) { transactionRepository.addTransaction(any()) }
    }

    @Test
    fun `previews the tsTON minted for the amount net of the deposit fee`() = runTest {
        val vm = createViewModel()

        vm.amountFieldState.setTextAndPlaceCursorAtEnd("6")
        // Nothing composes in a JVM test, so the snapshot the edit lands in has to be published
        // by hand before `snapshotFlow` sees it.
        Snapshot.sendApplyNotifications()

        // (6 − 1 fee) GRAM / 1.16 = 4.310344827 tsTON, floored in base units.
        vm.state.value.expectedTsTon shouldBe BigDecimal("4.310344827")
    }

    @Test
    fun `no preview while the amount does not clear the deposit fee`() = runTest {
        val vm = createViewModel()

        vm.amountFieldState.setTextAndPlaceCursorAtEnd("1")
        Snapshot.sendApplyNotifications()

        vm.state.value.expectedTsTon.shouldBeNull()
    }

    @Test
    fun `submit builds a deposit to the pool carrying the entered value and routes to verify`() =
        runTest {
            val vm = createViewModel()
            vm.amountFieldState.setTextAndPlaceCursorAtEnd("6")
            val saved = slot<DepositTransaction>()
            coEvery { transactionRepository.addTransaction(capture(saved)) } returns Unit

            vm.submit()

            val tx = saved.captured
            tx.operation shouldBe OPERATION_TONSTAKERS_STAKE
            tx.dstAddress shouldBe Tonstakers.POOL_ADDRESS
            tx.srcToken shouldBe TON_COIN
            tx.srcTokenValue.value shouldBe BigInteger.valueOf(6_000_000_000L)
            tx.memo shouldBe ""
            tx.estimatedFees shouldBe GAS_FEE

            val message = tx.signTon.shouldNotBeNull().tonMessages.single().shouldNotBeNull()
            message.to shouldBe Tonstakers.POOL_ADDRESS
            message.amount shouldBe "6000000000"
            TonMessageBodyDecoder.decode(message.payload)
                .shouldBeInstanceOf<TonMessageBodyIntent.LiquidStakingDeposit>()

            coVerify {
                navigator.route(Route.VerifyDeposit(vaultId = VAULT_ID, transactionId = tx.id))
            }
            vm.state.value.isSubmitting.shouldBeFalse()
        }

    @Test
    fun `submit refuses an amount below the minimum`() = runTest {
        val vm = createViewModel()
        vm.amountFieldState.setTextAndPlaceCursorAtEnd("1.5")

        vm.submit()

        coVerify(exactly = 0) { transactionRepository.addTransaction(any()) }
    }

    @Test
    fun `submit refuses an amount above the stakeable balance`() = runTest {
        val vm = createViewModel()
        vm.amountFieldState.setTextAndPlaceCursorAtEnd("10.01")

        vm.submit()

        coVerify(exactly = 0) { transactionRepository.addTransaction(any()) }
        vm.state.value.errorMessage.shouldNotBeNull()
    }

    @Test
    fun `percentage chips fill the field from the stakeable balance`() = runTest {
        val vm = createViewModel()

        vm.onPercentageChange(25)

        vm.amountFieldState.text.toString() shouldBe "2.5"
        vm.state.value.percentageSelected shouldBe 25
    }

    private fun stubNativeBalance(nanoTon: Long) {
        coEvery { balanceRepository.getCachedTokenBalanceAndPrice(TON_ADDRESS, any()) } returns
            TokenBalanceAndPrice(
                tokenBalance =
                    TokenBalance(
                        tokenValue =
                            TokenValue(
                                value = BigInteger.valueOf(nanoTon),
                                unit = "GRAM",
                                decimals = 9,
                            ),
                        fiatValue = FiatValue(BigDecimal.ZERO, "USD"),
                    ),
                price = null,
            )
    }

    private fun createViewModel(): TonLiquidStakeViewModel {
        val savedStateHandle = mockk<SavedStateHandle>()
        every { savedStateHandle.get<String>("vaultId") } returns VAULT_ID
        return TonLiquidStakeViewModel(
            savedStateHandle = savedStateHandle,
            vaultRepository = vaultRepository,
            liquidStakingService = liquidStakingService,
            accountsRepository = accountsRepository,
            balanceRepository = balanceRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            depositGasFeeHelper = depositGasFeeHelper,
            transactionRepository = transactionRepository,
            navigator = navigator,
            ioDispatcher = testDispatcher,
        )
    }

    private companion object {
        const val VAULT_ID = "vault-1"
        const val TON_ADDRESS = "EQBfwesEQte6-OnnVoRroXg2Fhs5kKQtfIITGP22CG98-SSR"

        val TON_COIN = Coins.Ton.TON.copy(address = TON_ADDRESS, hexPublicKey = "aa")
        val GAS_FEE =
            TokenValue(value = BigInteger.valueOf(50_000_000L), unit = "GRAM", decimals = 9)

        /** tsTON→TON rate of 1.16. */
        val POOL_STATE =
            TonLiquidPoolState(
                totalBalance = BigInteger.valueOf(116_000_000_000L),
                supply = BigInteger.valueOf(100_000_000_000L),
                apy = 13.36,
                minStake = BigInteger.valueOf(1_000_000_000L),
                isDepositOpen = true,
                isOptimistic = true,
            )

        val VAULT =
            Vault(
                id = VAULT_ID,
                name = "Vultisig Wallet",
                pubKeyECDSA = "",
                pubKeyEDDSA = "",
                hexChainCode = "",
                localPartyID = "",
                signers = emptyList(),
                resharePrefix = "",
                libType = SigningLibType.DKLS,
                coins = listOf(TON_COIN),
            )
    }
}
