@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.deposit.submit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.data.api.chains.ton.TonStakingApi
import com.vultisig.wallet.data.api.chains.ton.TonStakingPoolInfoJson
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class UnstakeStrategyTest {

    private val nodeAddress = TextFieldState()
    private val tokenAmount = TextFieldState()

    private val accountsRepo: AccountsRepository = mockk()
    private val specificRepo: BlockChainSpecificRepository = mockk()
    private val tonStakingApi: TonStakingApi = mockk()

    @Test
    fun `tf withdraw uses w comment, bounceable dest and fixed 0_2 TON fee`() = runTest {
        givenPool(implementation = "tf")
        givenAccounts()
        givenSpecific()
        nodeAddress.setTextAndPlaceCursorAtEnd("0:pool")
        // Entered amount is ignored for withdraw.
        tokenAmount.setTextAndPlaceCursorAtEnd("1")

        val tx = build().build()

        assertEquals("w", tx.memo)
        assertEquals(tonCoin(), tx.srcToken)
        assertEquals("EQ_0:pool", tx.dstAddress)
        assertEquals(BigInteger.valueOf(200_000_000), tx.srcTokenValue.value)
    }

    @Test
    fun `withdraw blocked for unsupported pool implementation`() = runTest {
        givenPool(implementation = "liquidTF")
        givenAccounts()
        nodeAddress.setTextAndPlaceCursorAtEnd("0:pool")

        assertFailsWith<InvalidTransactionDataException> { build().build() }
    }

    @Test
    fun `unstake throws when deposit chain is not Ton`() = runTest {
        nodeAddress.setTextAndPlaceCursorAtEnd("0:pool")

        assertFailsWith<InvalidTransactionDataException> {
            build(depositChain = Chain.ThorChain).build()
        }
    }

    @Test
    fun `unstake throws when node address is blank`() = runTest {
        assertFailsWith<InvalidTransactionDataException> { build().build() }
    }

    @Test
    fun `unstake throws when no native token account exists`() = runTest {
        givenPool(implementation = "whales")
        coEvery { accountsRepo.loadAddress("vault-1", Chain.Ton) } returns
            flowOf(Address(chain = Chain.Ton, address = "0:pool", accounts = emptyList()))
        nodeAddress.setTextAndPlaceCursorAtEnd("0:pool")

        assertFailsWith<InvalidTransactionDataException> { build().build() }
    }

    private fun build(depositChain: Chain = Chain.Ton) =
        UnstakeStrategy(
            vaultIdProvider = { "vault-1" },
            chainProvider = { Chain.Ton },
            stateProvider = { DepositFormUiModel(depositChain = depositChain) },
            nodeAddressFieldState = nodeAddress,
            tokenAmountFieldState = tokenAmount,
            accountsRepository = accountsRepo,
            tonStakingApi = tonStakingApi,
            toBounceableAddress = { "EQ_$it" },
            blockChainSpecificRepository = specificRepo,
            calculateGasFee = { _, token, _ -> TokenValue(BigInteger.ONE, token) },
            getFeesFiatValue = { _, _, _ -> estimatedFee() },
        )

    private fun givenPool(implementation: String) {
        coEvery { tonStakingApi.getStakingPool(any()) } returns
            TonStakingPoolInfoJson(implementation = implementation)
    }

    private fun givenAccounts() {
        coEvery { accountsRepo.loadAddress("vault-1", Chain.Ton) } returns
            flowOf(
                Address(
                    chain = Chain.Ton,
                    address = "tonSelf",
                    accounts =
                        listOf(
                            Account(
                                token = tonCoin(),
                                tokenValue = null,
                                fiatValue = null,
                                price = null,
                            )
                        ),
                )
            )
    }

    private fun givenSpecific() {
        coEvery {
            specificRepo.getSpecific(
                chain = any(),
                address = any(),
                token = any(),
                gasFee = any(),
                isSwap = any(),
                isMaxAmountEnabled = any(),
                isDeposit = any(),
                dstAddress = any(),
            )
        } returns
            BlockChainSpecificAndUtxo(
                BlockChainSpecific.Ton(
                    sequenceNumber = 0UL,
                    expireAt = 0UL,
                    bounceable = true,
                    sendMaxAmount = false,
                )
            )
    }

    private fun estimatedFee() =
        EstimatedGasFee(
            formattedFiatValue = "$0.01",
            formattedTokenValue = "0.0001",
            tokenValue = TokenValue(BigInteger.ONE, tonCoin()),
            fiatValue = mockk(relaxed = true),
        )

    private fun tonCoin(): Coin =
        Coin(
            chain = Chain.Ton,
            ticker = "TON",
            logo = "",
            address = "tonSelf",
            decimal = 9,
            hexPublicKey = "",
            priceProviderID = "the-open-network",
            contractAddress = "",
            isNativeToken = true,
        )
}
