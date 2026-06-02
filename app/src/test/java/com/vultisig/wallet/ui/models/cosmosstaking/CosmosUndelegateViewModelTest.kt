@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.cosmosstaking

import androidx.lifecycle.SavedStateHandle
import com.vultisig.wallet.data.blockchain.cosmos.staking.BuildCosmosStakingKeysignPayloadUseCase
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosDelegation
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
import com.vultisig.wallet.ui.navigation.Route
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
internal class CosmosUndelegateViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val vaultRepository: VaultRepository = mockk()
    private val cosmosStakingService: CosmosStakingService = mockk()
    private val blockChainSpecificRepository: BlockChainSpecificRepository = mockk(relaxed = true)
    private val buildPayload: BuildCosmosStakingKeysignPayloadUseCase = mockk(relaxed = true)
    private val depositTransactionRepository: DepositTransactionRepository = mockk(relaxed = true)
    private val navigator: Navigator<Destination> = mockk(relaxed = true)

    private val validatorAddr = "terravaloper1src"

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
                    validatorAddress = validatorAddr,
                    balance = CosmosStakingCoin("uluna", "2000000"),
                    shares = "2000000",
                )
            )
        coEvery { cosmosStakingService.fetchValidators(any()) } returns
            listOf(
                CosmosValidator(
                    operatorAddress = validatorAddr,
                    moniker = "Source",
                    commission = BigDecimal("0.05"),
                    jailed = false,
                    status = CosmosValidator.Status.Bonded,
                    votingPower = BigDecimal("100"),
                )
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() =
        CosmosUndelegateViewModel(
            savedStateHandle =
                SavedStateHandle(
                    mapOf(
                        "vaultId" to "v1",
                        "chainId" to "Terra",
                        "validatorAddress" to validatorAddr,
                    )
                ),
            vaultRepository = vaultRepository,
            cosmosStakingService = cosmosStakingService,
            blockChainSpecificRepository = blockChainSpecificRepository,
            buildCosmosStakingKeysignPayload = buildPayload,
            depositTransactionRepository = depositTransactionRepository,
            balanceRepository = mockk(relaxed = true),
            navigator = navigator,
            ioDispatcher = testDispatcher,
        )

    @Test
    fun `staked balance prefills and the unbonding lock notice is set`() = runTest {
        val model = vm()
        // 2_000_000 uluna at 6 decimals = 2 LUNA.
        assertEquals(0, BigDecimal("2").compareTo(model.state.value.stakedBalance))
        assertNotNull(model.state.value.unbondingLockMessage)
        assertEquals(validatorAddr, model.state.value.validatorAddress)
    }

    @Test
    fun `amount exceeding the staked balance is rejected`() = runTest {
        val model = vm()
        model.amountFieldState.edit { replace(0, length, "5") } // > 2 staked
        model.submit()
        assertNotNull(model.state.value.errorMessage)
        coVerify(exactly = 0) { navigator.route(any<Route.VerifyDeposit>()) }
    }
}
