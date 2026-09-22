package com.vultisig.wallet.ui.models.defi

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import com.vultisig.wallet.data.blockchain.ton.TonLiquidPoolState
import com.vultisig.wallet.data.blockchain.ton.TonLiquidPosition
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
import com.vultisig.wallet.data.models.OPERATION_TONSTAKERS_UNSTAKE
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
internal class TonLiquidUnstakeViewModelTest {

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
        coEvery { liquidStakingService.getPosition(TON_ADDRESS) } returns POSITION
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
    fun `fails closed when the vault holds no tsTON`() = runTest {
        coEvery { liquidStakingService.getPosition(TON_ADDRESS) } returns
            TonLiquidPosition(tsTonBalance = BigInteger.ZERO, jettonWalletAddress = null)

        val state = createViewModel().state.value

        state.isReady.shouldBeFalse()
        state.errorMessage.shouldNotBeNull()
    }

    @Test
    fun `fails closed when the pool rate cannot be read`() = runTest {
        stubNativeBalance(nanoTon = 5_000_000_000L)
        coEvery { liquidStakingService.getPoolState() } returns null

        val state = createViewModel().state.value

        state.isReady.shouldBeFalse()
        state.errorMessage.shouldNotBeNull()
    }

    @Test
    fun `loads the tsTON balance and gates on 1_05 TON plus gas of native balance`() = runTest {
        // Required = 1.05 TON attached + 0.05 TON gas = 1.1 TON; 1.1 TON covers it exactly.
        stubNativeBalance(nanoTon = 1_100_000_000L)

        val state = createViewModel().state.value

        state.isReady.shouldBeTrue()
        state.errorMessage.shouldBeNull()
        state.availableTsTon.stripTrailingZeros() shouldBe BigDecimal("4.3")
        state.nativeTicker shouldBe "GRAM"
        state.ticker shouldBe "tsTON"
        state.isOptimistic.shouldBeTrue()
        state.hasSufficientNativeBalance.shouldBeTrue()
    }

    @Test
    fun `disables the action when the native balance cannot cover the attached value`() = runTest {
        stubNativeBalance(nanoTon = 1_000_000_000L)

        val state = createViewModel().state.value

        state.isReady.shouldBeTrue()
        state.hasSufficientNativeBalance.shouldBeFalse()
    }

    @Test
    fun `previews the TON the pool pays at its rate`() = runTest {
        stubNativeBalance(nanoTon = 5_000_000_000L)
        val vm = createViewModel()

        vm.amountFieldState.setTextAndPlaceCursorAtEnd("2")
        // Nothing composes in a JVM test, so the snapshot the edit lands in has to be published
        // by hand before `snapshotFlow` sees it.
        Snapshot.sendApplyNotifications()

        // 2 tsTON × 1.16 = 2.32 GRAM.
        vm.state.value.expectedTon?.stripTrailingZeros() shouldBe BigDecimal("2.32")
    }

    @Test
    fun `percentage chips fill the field from the tsTON balance`() = runTest {
        stubNativeBalance(nanoTon = 5_000_000_000L)
        val vm = createViewModel()

        vm.onPercentageChange(50)

        vm.amountFieldState.text.toString() shouldBe "2.15"
        vm.state.value.percentageSelected shouldBe 50
    }

    @Test
    fun `submit builds a burn to the tsTON wallet carrying the attached value and routes to verify`() =
        runTest {
            stubNativeBalance(nanoTon = 5_000_000_000L)
            val vm = createViewModel()
            vm.amountFieldState.edit { replace(0, length, "2.5") }
            val saved = slot<DepositTransaction>()
            coEvery { transactionRepository.addTransaction(capture(saved)) } returns Unit

            vm.submit()

            val tx = saved.captured
            tx.operation shouldBe OPERATION_TONSTAKERS_UNSTAKE
            tx.dstAddress shouldBe JETTON_WALLET
            tx.srcAddress shouldBe TON_ADDRESS
            // The verify screen shows the tsTON leaving; the payload signs from the native coin.
            tx.srcToken.ticker shouldBe "tsTON"
            tx.srcTokenValue.value shouldBe BigInteger.valueOf(2_500_000_000L)
            tx.estimatedFees shouldBe GAS_FEE

            val message = tx.signTon.shouldNotBeNull().tonMessages.single().shouldNotBeNull()
            message.to shouldBe JETTON_WALLET
            message.amount shouldBe Tonstakers.UNSTAKE_ATTACHED_VALUE.toString()
            val body =
                TonMessageBodyDecoder.decode(message.payload)
                    .shouldBeInstanceOf<TonMessageBodyIntent.JettonBurn>()
            body.amount shouldBe BigInteger.valueOf(2_500_000_000L)
            body.responseDestination shouldBe TON_ADDRESS_RAW
            body.liquidStakingWithdrawal.shouldNotBeNull()

            coVerify {
                navigator.route(Route.VerifyDeposit(vaultId = VAULT_ID, transactionId = tx.id))
            }
            vm.state.value.isSubmitting.shouldBeFalse()
        }

    @Test
    fun `submit refuses an amount above the tsTON balance`() = runTest {
        stubNativeBalance(nanoTon = 5_000_000_000L)
        val vm = createViewModel()
        vm.amountFieldState.edit { replace(0, length, "4.31") }

        vm.submit()

        coVerify(exactly = 0) { transactionRepository.addTransaction(any()) }
        coVerify(exactly = 0) { navigator.route(match { it is Route.VerifyDeposit }) }
    }

    @Test
    fun `submit is a no-op while the native balance is short`() = runTest {
        stubNativeBalance(nanoTon = 1_000_000_000L)
        val vm = createViewModel()
        vm.amountFieldState.edit { replace(0, length, "1") }

        vm.submit()

        coVerify(exactly = 0) { transactionRepository.addTransaction(any()) }
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

    private fun createViewModel(): TonLiquidUnstakeViewModel {
        val savedStateHandle = mockk<SavedStateHandle>()
        every { savedStateHandle.get<String>("vaultId") } returns VAULT_ID
        return TonLiquidUnstakeViewModel(
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
        const val TON_ADDRESS_RAW =
            "0:5fc1eb0442d7baf8e9e756846ba17836161b3990a42d7c821318fdb6086f7cf9"
        const val JETTON_WALLET = "EQBiuk7kPCTMhLvpPJvGoccFY7pA6b4gSdmZ0NvMYi_JlPzJ"

        val TON_COIN = Coins.Ton.TON.copy(address = TON_ADDRESS, hexPublicKey = "aa")
        val GAS_FEE =
            TokenValue(value = BigInteger.valueOf(50_000_000L), unit = "GRAM", decimals = 9)

        val POSITION =
            TonLiquidPosition(
                tsTonBalance = BigInteger.valueOf(4_300_000_000L),
                jettonWalletAddress = JETTON_WALLET,
            )

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
