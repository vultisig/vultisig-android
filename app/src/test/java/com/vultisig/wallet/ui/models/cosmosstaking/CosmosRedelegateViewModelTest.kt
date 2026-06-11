@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.cosmosstaking

import androidx.lifecycle.SavedStateHandle
import com.vultisig.wallet.data.blockchain.cosmos.staking.BuildCosmosStakingKeysignPayloadUseCase
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosDelegation
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosRedelegationCooldownState
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosRedelegationEntry
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingCoin
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingService
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosValidator
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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
internal class CosmosRedelegateViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val vaultRepository: VaultRepository = mockk()
    private val cosmosStakingService: CosmosStakingService = mockk()
    private val blockChainSpecificRepository: BlockChainSpecificRepository = mockk(relaxed = true)
    private val buildPayload: BuildCosmosStakingKeysignPayloadUseCase = mockk(relaxed = true)
    private val depositTransactionRepository: DepositTransactionRepository = mockk(relaxed = true)
    private val navigator: Navigator<Destination> = mockk(relaxed = true)

    private val srcValidator = "terravaloper1src"

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
        coEvery { cosmosStakingService.fetchDelegations(any(), any()) } returns
            listOf(
                CosmosDelegation(
                    validatorAddress = srcValidator,
                    balance = CosmosStakingCoin(denom = "uluna", amount = "5000000"),
                    shares = "5000000",
                )
            )
        coEvery { cosmosStakingService.fetchValidators(any()) } returns
            listOf(
                validator(srcValidator, "Source", BigDecimal("500")),
                validator("terravaloper1dst1", "Dest 1", BigDecimal("300")),
                validator("terravaloper1dst2", "Dest 2", BigDecimal("900")),
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun validator(addr: String, moniker: String, power: BigDecimal) =
        CosmosValidator(
            operatorAddress = addr,
            moniker = moniker,
            commission = BigDecimal("0.05"),
            jailed = false,
            status = CosmosValidator.Status.Bonded,
            votingPower = power,
        )

    private fun vm(
        stakedAmount: String? = null,
        ticker: String? = null,
    ): CosmosRedelegateViewModel =
        CosmosRedelegateViewModel(
            savedStateHandle =
                SavedStateHandle(
                    mapOf(
                        "vaultId" to "v1",
                        "chainId" to "Terra",
                        "validatorSrcAddress" to srcValidator,
                        "stakedAmount" to stakedAmount,
                        "ticker" to ticker,
                    )
                ),
            vaultRepository = vaultRepository,
            cosmosStakingService = cosmosStakingService,
            blockChainSpecificRepository = blockChainSpecificRepository,
            buildCosmosStakingKeysignPayload = buildPayload,
            depositTransactionRepository = depositTransactionRepository,
            keybaseAvatarService = mockk(relaxed = true),
            balanceRepository = mockk(relaxed = true),
            context = mockk(relaxed = true),
            navigator = navigator,
            ioDispatcher = testDispatcher,
        )

    @Test
    fun `staked balance is prefilled from the source delegation`() = runTest {
        coEvery { cosmosStakingService.fetchRedelegations(any(), any()) } returns emptyList()
        val model = vm()
        // 5_000_000 uluna at 6 decimals = 5 LUNA.
        assertEquals(0, BigDecimal("5").compareTo(model.state.value.stakedBalance))
    }

    @Test
    fun `prefilled staked amount is retained when the LCD returns no matching delegation`() =
        runTest {
            // Transient LCD blip: keep the value carried from the tapped position (#4815).
            coEvery { cosmosStakingService.fetchRedelegations(any(), any()) } returns emptyList()
            coEvery { cosmosStakingService.fetchDelegations(any(), any()) } returns emptyList()
            val model = vm(stakedAmount = "9")
            assertEquals(0, BigDecimal("9").compareTo(model.state.value.stakedBalance))
        }

    @Test
    fun `LCD value overrides the prefilled staked amount when it differs`() = runTest {
        coEvery { cosmosStakingService.fetchRedelegations(any(), any()) } returns emptyList()
        val model = vm(stakedAmount = "9")
        // The authoritative LCD read (5 LUNA) wins over the carried hint.
        assertEquals(0, BigDecimal("5").compareTo(model.state.value.stakedBalance))
    }

    @Test
    fun `the carried ticker seeds the title on the first frame so it never flashes Token`() =
        runTest {
            // #4822: the ticker is carried through the route, so the form shows the real symbol
            // immediately rather than the "Token" placeholder until the async coin load lands.
            coEvery { cosmosStakingService.fetchRedelegations(any(), any()) } returns emptyList()
            val model = vm(stakedAmount = "9", ticker = "LUNA")
            assertEquals("LUNA", model.state.value.ticker)
        }

    @Test
    fun `cooldown blocks submit and surfaces the unlock message`() = runTest {
        // Transitive redelegation rule (cosmos-sdk HasReceivingRedelegation): we want to redelegate
        // FROM srcValidator, but it was the DESTINATION of an active redelegation, so the chain
        // would reject. Gate must catch this and surface the unlock date.
        coEvery { cosmosStakingService.fetchRedelegations(any(), any()) } returns
            listOf(
                CosmosRedelegationEntry(
                    srcValidator = "terravaloper1other",
                    dstValidator = srcValidator,
                    completionTime = Instant.now().plusSeconds(10L * 86_400L),
                )
            )
        val model = vm()
        assertIs<CosmosRedelegationCooldownState.Blocked>(model.state.value.cooldownState)
        assertNotNull(model.state.value.cooldownBlockedMessage)

        model.submit()
        // Submit blocked — never navigated, error surfaced.
        assertNotNull(model.state.value.errorMessage)
        assertFalse(model.state.value.isSubmitting)
    }

    @Test
    fun `destination picker excludes the source and sorts by voting power desc`() = runTest {
        coEvery { cosmosStakingService.fetchRedelegations(any(), any()) } returns emptyList()
        val model = vm()
        val visible = model.visibleValidators(model.state.value)
        assertTrue(visible.none { it.operatorAddress == srcValidator })
        // Dest 2 (900) before Dest 1 (300).
        assertEquals("terravaloper1dst2", visible.first().operatorAddress)
    }
}
