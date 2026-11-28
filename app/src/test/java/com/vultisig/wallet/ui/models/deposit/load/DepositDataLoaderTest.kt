@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.deposit.load

import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.deposit.DepositOption
import com.vultisig.wallet.ui.models.deposit.TokenMergeInfo
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
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

    private fun TestScope.wire(
        chain: Chain,
        canAddMayaLiquidity: Boolean,
    ): MutableStateFlow<DepositFormUiModel> {
        every { accountsRepository.loadAddress(VAULT_ID, chain) } returns emptyFlow()
        val state = MutableStateFlow(DepositFormUiModel())
        DepositDataLoader(
                accountsRepository = accountsRepository,
                scope = backgroundScope,
                address = MutableStateFlow<Address?>(null),
                depositTypeActionProvider = { null },
                clearDepositTypeAction = {},
                selectDepositOption = { option ->
                    state.value = state.value.copy(depositOption = option)
                },
            )
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

    private companion object {
        const val VAULT_ID = "vault-1"
    }
}
