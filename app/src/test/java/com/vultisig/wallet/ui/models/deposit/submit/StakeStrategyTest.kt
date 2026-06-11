@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.deposit.submit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
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
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
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

internal class StakeStrategyTest {

    private val nodeAddress = TextFieldState()
    private val tokenAmount = TextFieldState()

    private val accountsRepo: AccountsRepository = mockk()
    private val chainRepo: ChainAccountAddressRepository = mockk()
    private val specificRepo: BlockChainSpecificRepository = mockk()

    @Test
    fun `Ton stake memo is d and uses native token scaled by decimals`() = runTest {
        coEvery { chainRepo.isValid(Chain.Ton, "tonNode") } returns true
        givenAccounts()
        givenSpecific()
        nodeAddress.setTextAndPlaceCursorAtEnd("tonNode")
        tokenAmount.setTextAndPlaceCursorAtEnd("1")

        val tx = build().build()

        assertEquals("d", tx.memo)
        assertEquals(tonCoin(), tx.srcToken)
        assertEquals("tonNode", tx.dstAddress)
        assertEquals(BigInteger.valueOf(1_000_000_000), tx.srcTokenValue.value)
    }

    @Test
    fun `stake throws when deposit chain is not Ton`() = runTest {
        coEvery { chainRepo.isValid(any(), any()) } returns true
        nodeAddress.setTextAndPlaceCursorAtEnd("tonNode")
        tokenAmount.setTextAndPlaceCursorAtEnd("1")

        assertFailsWith<InvalidTransactionDataException> {
            build(depositChain = Chain.ThorChain).build()
        }
    }

    @Test
    fun `stake throws when node address is blank`() = runTest {
        coEvery { chainRepo.isValid(any(), any()) } returns false
        tokenAmount.setTextAndPlaceCursorAtEnd("1")

        assertFailsWith<InvalidTransactionDataException> { build().build() }
    }

    @Test
    fun `stake throws when token amount is missing or zero`() = runTest {
        coEvery { chainRepo.isValid(Chain.Ton, "tonNode") } returns true
        nodeAddress.setTextAndPlaceCursorAtEnd("tonNode")
        tokenAmount.setTextAndPlaceCursorAtEnd("0")

        assertFailsWith<InvalidTransactionDataException> { build().build() }
    }

    @Test
    fun `stake throws when no native token account exists`() = runTest {
        coEvery { chainRepo.isValid(Chain.Ton, "tonNode") } returns true
        coEvery { accountsRepo.loadAddress("vault-1", Chain.Ton) } returns
            flowOf(Address(chain = Chain.Ton, address = "tonNode", accounts = emptyList()))
        nodeAddress.setTextAndPlaceCursorAtEnd("tonNode")
        tokenAmount.setTextAndPlaceCursorAtEnd("1")

        assertFailsWith<InvalidTransactionDataException> { build().build() }
    }

    private fun build(depositChain: Chain = Chain.Ton) =
        StakeStrategy(
            vaultIdProvider = { "vault-1" },
            chainProvider = { Chain.Ton },
            stateProvider = { DepositFormUiModel(depositChain = depositChain) },
            nodeAddressFieldState = nodeAddress,
            tokenAmountFieldState = tokenAmount,
            accountsRepository = accountsRepo,
            chainAccountAddressRepository = chainRepo,
            blockChainSpecificRepository = specificRepo,
            calculateGasFee = { _, token, _ -> TokenValue(BigInteger.ONE, token) },
            getFeesFiatValue = { _, _, _ -> estimatedFee() },
        )

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
            )
        } returns
            BlockChainSpecificAndUtxo(
                BlockChainSpecific.Ton(
                    sequenceNumber = 0UL,
                    expireAt = 0UL,
                    bounceable = false,
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
