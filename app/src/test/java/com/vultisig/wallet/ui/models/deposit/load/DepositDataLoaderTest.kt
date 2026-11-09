@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.deposit.load

import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.deposit.DepositOption
import com.vultisig.wallet.ui.models.deposit.TokenMergeInfo
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class DepositDataLoaderTest {

    private val accountsRepository: AccountsRepository = mockk()

    @Test
    fun `Add Maya LP is offered to a vault that can hold MayaChain`() =
        runTest(UnconfinedTestDispatcher()) {
            val state = wire(Chain.Bitcoin, canAddMayaLiquidity = true)

            state.value.depositOptions shouldBe
                listOf(DepositOption.SecuredAsset, DepositOption.AddMayaLiquidity)
        }

    @Test
    fun `Add Maya LP is dropped for a vault that can never hold MayaChain`() =
        runTest(UnconfinedTestDispatcher()) {
            val state = wire(Chain.Bitcoin, canAddMayaLiquidity = false)

            state.value.depositOptions shouldBe listOf(DepositOption.SecuredAsset)
            state.value.depositOption shouldBe DepositOption.SecuredAsset
        }

    @Test
    fun `a chain offering only Add Maya LP falls back to Custom without it`() =
        runTest(UnconfinedTestDispatcher()) {
            val state = wire(Chain.Dash, canAddMayaLiquidity = false)

            state.value.depositOptions shouldBe emptyList()
            state.value.depositOption shouldBe DepositOption.Custom
        }

    @Test
    fun `Add LP fee is priced again once the balance arrives after the deep link is consumed`() =
        runTest(UnconfinedTestDispatcher()) {
            val eth = Coins.Ethereum.ETH.copy(address = ETH_ADDRESS)
            val loaded = MutableStateFlow(address(eth, balance = null))
            every { accountsRepository.loadAddress(VAULT_ID, Chain.Ethereum) } returns loaded
            var depositTypeAction: String? = DeFiNavActions.ADD_LP.type
            val state = MutableStateFlow(DepositFormUiModel())
            val pricedBalances = mutableListOf<BigInteger?>()

            loader(
                    state = state,
                    depositTypeActionProvider = { depositTypeAction },
                    clearDepositTypeAction = { depositTypeAction = null },
                )
                .wireInitialState(
                    vaultId = VAULT_ID,
                    chain = Chain.Ethereum,
                    tokensToMerge = listOf(TokenMergeInfo(ticker = "RUJI", contract = "")),
                    state = state,
                    updateTokenAmount = { _, _, _, _ -> },
                    selectDstChain = {},
                    collectSecuredAssetAddresses = {},
                    loadMayaLpPairing = {},
                    loadGasFeeForDisplay = { _, _, address ->
                        pricedBalances += address.accounts.single().tokenValue?.value
                    },
                )
            advanceUntilIdle()
            depositTypeAction shouldBe null

            loaded.value = address(eth, balance = BigInteger.TEN)
            advanceUntilIdle()

            pricedBalances.last() shouldBe BigInteger.TEN
        }

    private fun TestScope.wire(
        chain: Chain,
        canAddMayaLiquidity: Boolean,
    ): MutableStateFlow<DepositFormUiModel> {
        every { accountsRepository.loadAddress(VAULT_ID, chain) } returns emptyFlow()
        val state = MutableStateFlow(DepositFormUiModel())
        loader(state)
            .wireInitialState(
                vaultId = VAULT_ID,
                chain = chain,
                tokensToMerge = listOf(TokenMergeInfo(ticker = "RUJI", contract = "")),
                state = state,
                updateTokenAmount = { _, _, _, _ -> },
                selectDstChain = {},
                collectSecuredAssetAddresses = {},
                loadMayaLpPairing = {},
                loadGasFeeForDisplay = { _, _, _ -> },
                canAddMayaLiquidity = { canAddMayaLiquidity },
            )
        advanceUntilIdle()
        return state
    }

    private fun TestScope.loader(
        state: MutableStateFlow<DepositFormUiModel>,
        depositTypeActionProvider: () -> String? = { null },
        clearDepositTypeAction: () -> Unit = {},
    ) =
        DepositDataLoader(
            accountsRepository = accountsRepository,
            scope = backgroundScope,
            address = MutableStateFlow<Address?>(null),
            depositTypeActionProvider = depositTypeActionProvider,
            clearDepositTypeAction = clearDepositTypeAction,
            selectDepositOption = { option ->
                state.value = state.value.copy(depositOption = option)
            },
        )

    private fun address(coin: Coin, balance: BigInteger?) =
        Address(
            chain = coin.chain,
            address = coin.address,
            accounts =
                listOf(
                    Account(
                        token = coin,
                        tokenValue = balance?.let { TokenValue(it, coin) },
                        fiatValue = null,
                        price = null,
                    )
                ),
        )

    private companion object {
        const val VAULT_ID = "vault-1"
        const val ETH_ADDRESS = "0xself"
    }
}
