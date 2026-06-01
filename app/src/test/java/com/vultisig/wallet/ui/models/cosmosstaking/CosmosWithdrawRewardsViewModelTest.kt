@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.cosmosstaking

import androidx.lifecycle.SavedStateHandle
import com.vultisig.wallet.data.blockchain.cosmos.staking.BuildCosmosStakingKeysignPayloadUseCase
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosDelegatorReward
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosDelegatorRewards
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingCoin
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingService
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosValidator
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenBalance
import com.vultisig.wallet.data.models.TokenBalanceAndPrice
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
internal class CosmosWithdrawRewardsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val vaultRepository: VaultRepository = mockk()
    private val cosmosStakingService: CosmosStakingService = mockk()
    private val balanceRepository: BalanceRepository = mockk()
    private val blockChainSpecificRepository: BlockChainSpecificRepository = mockk(relaxed = true)
    private val buildPayload: BuildCosmosStakingKeysignPayloadUseCase = mockk(relaxed = true)
    private val depositTransactionRepository: DepositTransactionRepository = mockk(relaxed = true)
    private val navigator: Navigator<Destination> = mockk(relaxed = true)

    private val coin =
        Coin(
            chain = Chain.Terra,
            ticker = "LUNA",
            logo = "",
            address = "terra1delegator",
            decimal = 6,
            hexPublicKey = "02".repeat(33),
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { vaultRepository.get(any()) } returns
            Vault(
                id = "v1",
                name = "v",
                pubKeyECDSA = "pk",
                localPartyID = "lp",
                coins = listOf(coin),
            )
        // 12 validators with rewards so the soft cap (8) is exceeded.
        val rewards =
            (1..12).map { i ->
                CosmosDelegatorReward(
                    validatorAddress = "terravaloper1val$i",
                    reward = listOf(CosmosStakingCoin(denom = "uluna", amount = "1000000")),
                )
            }
        coEvery { cosmosStakingService.fetchDelegatorRewards(any(), any()) } returns
            CosmosDelegatorRewards(rewards = rewards, total = emptyList())
        coEvery { cosmosStakingService.fetchValidators(any()) } returns
            (1..12).map { i ->
                CosmosValidator(
                    operatorAddress = "terravaloper1val$i",
                    moniker = "Validator $i",
                    commission = BigDecimal("0.05"),
                    jailed = false,
                    status = CosmosValidator.Status.Bonded,
                    votingPower = BigDecimal("100"),
                )
            }
        coEvery { balanceRepository.getCachedTokenBalanceAndPrice(any(), any()) } returns
            TokenBalanceAndPrice(
                tokenBalance =
                    TokenBalance(
                        tokenValue = TokenValue(value = BigInteger("100000000"), token = coin),
                        fiatValue = null,
                    ),
                price = null,
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() =
        CosmosWithdrawRewardsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("vaultId" to "v1", "chainId" to "Terra")),
            vaultRepository = vaultRepository,
            cosmosStakingService = cosmosStakingService,
            balanceRepository = balanceRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            buildCosmosStakingKeysignPayload = buildPayload,
            depositTransactionRepository = depositTransactionRepository,
            navigator = navigator,
            ioDispatcher = testDispatcher,
        )

    @Test
    fun `default selection is capped at 8 and the cap warning fires`() = runTest {
        val model = vm()
        val s = model.state.value
        assertEquals(12, s.candidates.size)
        assertEquals(8, s.selectedValidators.size)
        assertTrue(s.hitBatchCapWarning)
        assertTrue(s.validForm)
    }

    @Test
    fun `toggling a 9th validator is rejected and raises the cap warning`() = runTest {
        val model = vm()
        // All first-8 selected. Pick a 9th not in the set.
        val ninth = model.state.value.candidates[8].validatorAddress
        model.toggle(ninth)
        val s = model.state.value
        assertEquals(8, s.selectedValidators.size)
        assertFalse(s.selectedValidators.contains(ninth))
        assertTrue(s.hitBatchCapWarning)
    }

    @Test
    fun `toggleSelectAll clears when already at cap then reselects first 8`() = runTest {
        val model = vm()
        // Initially 8 selected (== min(12, cap)). First toggle clears.
        model.toggleSelectAll()
        assertEquals(0, model.state.value.selectedValidators.size)
        assertFalse(model.state.value.validForm)
        // Second toggle reselects first 8.
        model.toggleSelectAll()
        assertEquals(8, model.state.value.selectedValidators.size)
    }

    @Test
    fun `estimated fee scales with selection count`() = runTest {
        val model = vm()
        // 8 selected × 7_500 uluna fee = 60_000 uluna = 0.06 LUNA at 6 decimals.
        val fee = model.state.value.estimatedFee
        assertEquals(0, BigDecimal("0.06").compareTo(fee.stripTrailingZeros()))
    }

    @Test
    fun `insufficient balance disables the form`() = runTest {
        coEvery { balanceRepository.getCachedTokenBalanceAndPrice(any(), any()) } returns
            TokenBalanceAndPrice(
                tokenBalance =
                    TokenBalance(
                        tokenValue = TokenValue(value = BigInteger.ZERO, token = coin),
                        fiatValue = null,
                    ),
                price = null,
            )
        val model = vm()
        assertFalse(model.state.value.hasSufficientBalanceForFee)
        assertFalse(model.state.value.validForm)
    }
}
