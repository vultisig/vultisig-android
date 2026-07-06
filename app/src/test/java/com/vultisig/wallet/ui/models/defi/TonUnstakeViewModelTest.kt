package com.vultisig.wallet.ui.models.defi

import androidx.lifecycle.SavedStateHandle
import com.vultisig.wallet.data.api.chains.ton.TonStakingApi
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.FiatValue
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
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
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
internal class TonUnstakeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var vaultRepository: VaultRepository
    private lateinit var tonStakingApi: TonStakingApi
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
        tonStakingApi = mockk(relaxed = true)
        accountsRepository = mockk(relaxed = true)
        balanceRepository = mockk(relaxed = true)
        blockChainSpecificRepository = mockk(relaxed = true)
        depositGasFeeHelper = mockk(relaxed = true)
        transactionRepository = mockk(relaxed = true)
        navigator = mockk(relaxed = true)

        // Default: a resolvable native TON coin and a modest gas fee. Individual tests override.
        coEvery { vaultRepository.get(VAULT_ID) } returns VAULT
        coEvery { depositGasFeeHelper.calculateGasFee(VAULT_ID, any(), any(), any()) } returns
            TokenValue(value = BigInteger.valueOf(50_000_000L), unit = "GRAM", decimals = 9)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `fails closed when the vault is missing`() = runTest {
        coEvery { vaultRepository.get(VAULT_ID) } returns null

        val state = createViewModel().state.value

        state.isLoading.shouldBeFalse()
        state.hasSufficientBalance.shouldBeFalse()
        state.errorMessage.shouldNotBeNull()
    }

    @Test
    fun `fails closed when the native TON coin is absent`() = runTest {
        coEvery { vaultRepository.get(VAULT_ID) } returns VAULT.copy(coins = emptyList())

        val state = createViewModel().state.value

        state.isLoading.shouldBeFalse()
        state.hasSufficientBalance.shouldBeFalse()
        state.errorMessage.shouldNotBeNull()
    }

    @Test
    fun `fails closed when the gas-fee lookup throws instead of defaulting to zero`() = runTest {
        stubSpendableBalance(nanoTon = 1_000_000_000L)
        coEvery { depositGasFeeHelper.calculateGasFee(VAULT_ID, any(), any(), any()) } throws
            RuntimeException("fee service down")

        val state = createViewModel().state.value

        state.isLoading.shouldBeFalse()
        state.hasSufficientBalance.shouldBeFalse()
        state.errorMessage.shouldNotBeNull()
    }

    @Test
    fun `enables the action when the balance covers the withdraw fee and gas`() = runTest {
        // Required = 0.2 TON withdraw fee + 0.05 TON gas = 0.25 TON; 1 TON covers it.
        stubSpendableBalance(nanoTon = 1_000_000_000L)

        val state = createViewModel().state.value

        state.isLoading.shouldBeFalse()
        state.hasSufficientBalance.shouldBeTrue()
    }

    @Test
    fun `disables the action when the balance cannot cover the fees`() = runTest {
        // 0.1 TON < the 0.25 TON required.
        stubSpendableBalance(nanoTon = 100_000_000L)

        val state = createViewModel().state.value

        state.isLoading.shouldBeFalse()
        state.hasSufficientBalance.shouldBeFalse()
    }

    private fun stubSpendableBalance(nanoTon: Long) {
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

    private fun createViewModel(): TonUnstakeViewModel {
        val savedStateHandle = mockk<SavedStateHandle>()
        every { savedStateHandle.get<String>("vaultId") } returns VAULT_ID
        every { savedStateHandle.get<String>("poolAddress") } returns POOL
        every { savedStateHandle.get<String>("stakedDisplay") } returns "50 GRAM"
        return TonUnstakeViewModel(
            savedStateHandle = savedStateHandle,
            vaultRepository = vaultRepository,
            tonStakingApi = tonStakingApi,
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
        const val TON_ADDRESS = "UQtonAddress"
        const val POOL = "0:a45b17f28409229b78360e3290420f13e4fe20f90d7e2bf8c4ac6703259e22fa"

        val TON_COIN = Coins.Ton.TON.copy(address = TON_ADDRESS)

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
