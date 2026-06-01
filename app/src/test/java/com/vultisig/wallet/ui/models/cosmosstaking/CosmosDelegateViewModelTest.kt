@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.cosmosstaking

import androidx.lifecycle.SavedStateHandle
import com.vultisig.wallet.data.blockchain.cosmos.staking.BuildCosmosStakingKeysignPayloadUseCase
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
import kotlin.test.assertEquals
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
internal class CosmosDelegateViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val vaultRepository: VaultRepository = mockk()
    private val cosmosStakingService: CosmosStakingService = mockk()
    private val balanceRepository: com.vultisig.wallet.data.repositories.BalanceRepository =
        mockk(relaxed = true)
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
        coEvery { cosmosStakingService.fetchValidators(any()) } returns
            listOf(
                validator(
                    "terravaloper1a",
                    "Alpha",
                    BigDecimal("100"),
                    jailed = false,
                    CosmosValidator.Status.Bonded,
                ),
                validator(
                    "terravaloper1b",
                    "Beta",
                    BigDecimal("900"),
                    jailed = false,
                    CosmosValidator.Status.Bonded,
                ),
                validator(
                    "terravaloper1j",
                    "Jailed",
                    BigDecimal("999"),
                    jailed = true,
                    CosmosValidator.Status.Bonded,
                ),
                validator(
                    "terravaloper1u",
                    "Unbonded",
                    BigDecimal("999"),
                    jailed = false,
                    CosmosValidator.Status.Unbonded,
                ),
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun validator(
        addr: String,
        moniker: String,
        power: BigDecimal,
        jailed: Boolean,
        status: CosmosValidator.Status,
    ) =
        CosmosValidator(
            operatorAddress = addr,
            moniker = moniker,
            commission = BigDecimal("0.05"),
            jailed = jailed,
            status = status,
            votingPower = power,
        )

    private fun vm() =
        CosmosDelegateViewModel(
            savedStateHandle = SavedStateHandle(mapOf("vaultId" to "v1", "chainId" to "Terra")),
            vaultRepository = vaultRepository,
            cosmosStakingService = cosmosStakingService,
            blockChainSpecificRepository = blockChainSpecificRepository,
            balanceRepository = balanceRepository,
            buildCosmosStakingKeysignPayload = buildPayload,
            depositTransactionRepository = depositTransactionRepository,
            navigator = navigator,
            ioDispatcher = testDispatcher,
        )

    @Test
    fun `visible validators exclude jailed and non-bonded and sort by voting power desc`() =
        runTest {
            val model = vm()
            val visible = model.visibleValidators(model.state.value)
            // Only the 2 bonded non-jailed remain, Beta (900) before Alpha (100).
            assertEquals(
                listOf("terravaloper1b", "terravaloper1a"),
                visible.map { it.operatorAddress },
            )
        }

    @Test
    fun `search filters by moniker case-insensitively`() = runTest {
        val model = vm()
        model.onSearchQueryChange("alph")
        val visible = model.visibleValidators(model.state.value)
        assertEquals(1, visible.size)
        assertEquals("terravaloper1a", visible.first().operatorAddress)
    }

    @Test
    fun `submit without a selected validator surfaces an error`() = runTest {
        val model = vm()
        model.submit()
        assertTrue(model.state.value.errorMessage != null)
    }
}
