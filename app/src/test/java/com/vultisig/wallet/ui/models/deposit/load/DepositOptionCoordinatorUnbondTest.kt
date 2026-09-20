@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.deposit.load

import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.blockchain.model.BondedNodePosition
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.usecases.ThorchainBondUseCase
import com.vultisig.wallet.ui.models.deposit.DepositFieldStates
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.deposit.DepositOption
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import io.kotest.matchers.shouldBe
import io.mockk.any
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class DepositOptionCoordinatorUnbondTest {

    private val bondUseCase: ThorchainBondUseCase = mockk()
    private val mapper: TokenValueToStringWithUnitMapper = mockk()

    @Test
    fun `thor unbond balance is the node's bonded amount not another node's`() = runTest {
        val bonded = BigInteger("1250000000")
        coEvery { bondUseCase.getActiveNodes(VAULT_ID, VAULT_ADDRESS) } returns
            flowOf(listOf(bondedNode(NODE, bonded), bondedNode("thor1other", BigInteger.TEN)))
        every { mapper.invoke(any()) } returns "12.5 RUNE"

        val state = MutableStateFlow(DepositFormUiModel(depositChain = Chain.ThorChain))
        val coordinator = coordinator(backgroundScope, state, NODE)

        coordinator.selectDepositOption(DepositOption.Unbond)
        advanceUntilIdle()
        advanceTimeBy(DEBOUNCE_MS)
        advanceUntilIdle()

        state.value.bondedRuneCeiling?.nodeAddress shouldBe NODE
        state.value.bondedRuneCeiling?.amount shouldBe bonded
        state.value.balanceDecimal shouldBe TokenValue(bonded, Coins.ThorChain.RUNE).decimal
    }

    @Test
    fun `thor unbond ceiling is zero when the node is not bonded`() = runTest {
        coEvery { bondUseCase.getActiveNodes(VAULT_ID, VAULT_ADDRESS) } returns
            flowOf(listOf(bondedNode("thor1other", BigInteger("1250000000"))))
        every { mapper.invoke(any()) } returns "0 RUNE"

        val state = MutableStateFlow(DepositFormUiModel(depositChain = Chain.ThorChain))
        val coordinator = coordinator(backgroundScope, state, NODE)

        coordinator.selectDepositOption(DepositOption.Unbond)
        advanceUntilIdle()
        advanceTimeBy(DEBOUNCE_MS)
        advanceUntilIdle()

        state.value.bondedRuneCeiling?.amount shouldBe BigInteger.ZERO
    }

    @Test
    fun `bond does not load a thor unbond ceiling`() = runTest {
        val state = MutableStateFlow(DepositFormUiModel(depositChain = Chain.ThorChain))
        val coordinator = coordinator(backgroundScope, state, NODE)

        coordinator.selectDepositOption(DepositOption.Bond)
        advanceUntilIdle()

        coVerify(exactly = 0) { bondUseCase.getActiveNodes(any(), any()) }
        state.value.bondedRuneCeiling shouldBe null
    }

    private fun coordinator(
        scope: CoroutineScope,
        state: MutableStateFlow<DepositFormUiModel>,
        bondAddress: String,
    ) =
        DepositOptionCoordinator(
            mayaChainApi = mockk<MayaChainApi>(relaxed = true),
            accountsRepository = mockk<AccountsRepository>(relaxed = true),
            mapTokenValueToStringWithUnit = mapper,
            thorchainBondUseCase = bondUseCase,
            scope = scope,
            state = state,
            address =
                MutableStateFlow(
                    Address(
                        chain = Chain.ThorChain,
                        address = VAULT_ADDRESS,
                        accounts = emptyList(),
                    )
                ),
            fields = DepositFieldStates(),
            liquidityDataLoader = mockk(relaxed = true),
            securedAssetLoader = mockk(relaxed = true),
            cacaoMaturityLoader = mockk(relaxed = true),
            chainProvider = { Chain.ThorChain },
            vaultId = { VAULT_ID },
            bondAddress = { bondAddress },
        )

    private fun bondedNode(address: String, amount: BigInteger) =
        BondedNodePosition(
            id = "rune-$address",
            node = BondedNodePosition.BondedNode(address = address, state = "Active"),
            amount = amount,
            coin = Coins.ThorChain.RUNE,
            apy = 0.0,
            nextReward = 0.0,
            nextChurn = null,
        )

    companion object {
        private const val VAULT_ID = "vault-1"
        private const val VAULT_ADDRESS = "thor1vault"
        private const val NODE = "thor1node"
        private const val DEBOUNCE_MS = 300L
    }
}
