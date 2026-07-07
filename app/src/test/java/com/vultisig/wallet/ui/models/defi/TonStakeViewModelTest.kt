package com.vultisig.wallet.ui.models.defi

import androidx.lifecycle.SavedStateHandle
import com.vultisig.wallet.data.api.chains.ton.TonStakingApi
import com.vultisig.wallet.data.api.chains.ton.TonStakingPoolEntryJson
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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
internal class TonStakeViewModelTest {

    private val decimals = Coins.Ton.TON.decimal
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

        coEvery { vaultRepository.get(VAULT_ID) } returns VAULT
        coEvery { tonStakingApi.getStakingPools() } returns emptyList()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun entry(
        address: String,
        apy: Double,
        verified: Boolean = true,
        implementation: String? = "whales",
        minStake: Long = 50_000_000_000L,
        current: Int? = null,
        max: Int? = null,
    ) =
        TonStakingPoolEntryJson(
            address = address,
            name = address,
            apy = apy,
            minStake = minStake,
            verified = verified,
            currentNominators = current,
            maxNominators = max,
            implementation = implementation,
        )

    @Test
    fun `keeps only verified nominator pools`() {
        val result =
            TonStakeViewModel.filterAndSortPools(
                listOf(
                    entry("whales-ok", apy = 5.0, implementation = "whales"),
                    entry("tf-ok", apy = 4.0, implementation = "tf"),
                    entry("liquid-excluded", apy = 9.0, implementation = "liquidTF"),
                    entry("unknown-excluded", apy = 8.0, implementation = "somethingElse"),
                    entry("unverified-excluded", apy = 7.0, verified = false),
                ),
                decimals,
            )

        result.map { it.address } shouldBe listOf("whales-ok", "tf-ok")
    }

    @Test
    fun `sorts by APY descending`() {
        val result =
            TonStakeViewModel.filterAndSortPools(
                listOf(entry("low", apy = 2.0), entry("high", apy = 12.5), entry("mid", apy = 7.0)),
                decimals,
            )

        result.map { it.address } shouldBe listOf("high", "mid", "low")
    }

    @Test
    fun `excludes pools at nominator capacity but keeps those with room or unknown counts`() {
        val result =
            TonStakeViewModel.filterAndSortPools(
                listOf(
                    entry("full", apy = 9.0, current = 40, max = 40),
                    entry("room", apy = 8.0, current = 10, max = 40),
                    entry("unknown", apy = 7.0, current = null, max = null),
                ),
                decimals,
            )

        result.map { it.address } shouldBe listOf("room", "unknown")
    }

    @Test
    fun `scales min stake from nanotons to human-decimal TON`() {
        val result =
            TonStakeViewModel.filterAndSortPools(
                listOf(entry("p", apy = 5.0, minStake = 50_000_000_000L)),
                decimals,
            )

        result.single().minStake.stripTrailingZeros().toPlainString() shouldBe "50"
    }

    @Test
    fun `display name falls back to a short address when unnamed`() {
        val pool =
            TonPoolUiModel(
                address = "0:a45b17f28409229b78360e3290420f13e4fe20f90d7e2bf8c4ac6703259e22fa",
                name = "",
                apy = 5.0,
                minStake = BigDecimal.TEN,
                verified = true,
            )

        pool.displayName shouldContain "…"
    }

    @Test
    fun `stakeable balance reserves the deposit network fee, not the larger withdraw fee`() =
        runTest {
            // 1 TON spendable, 0.05 TON deposit network fee → 0.95 TON stakeable. Reserving the
            // 0.2 TON withdraw fee here (the old bug) would leave only 0.8 TON.
            stubSpendableBalance(nanoTon = 1_000_000_000L)
            coEvery { depositGasFeeHelper.calculateGasFee(VAULT_ID, any(), any(), any()) } returns
                TokenValue(value = BigInteger.valueOf(50_000_000L), unit = "GRAM", decimals = 9)

            val vm = createViewModel()

            vm.state.value.stakeableBalance.stripTrailingZeros().toPlainString() shouldBe "0.95"
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

    private fun createViewModel(): TonStakeViewModel {
        val savedStateHandle = mockk<SavedStateHandle>()
        every { savedStateHandle.get<String>("vaultId") } returns VAULT_ID
        every { savedStateHandle.get<String>("poolAddress") } returns null
        return TonStakeViewModel(
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
