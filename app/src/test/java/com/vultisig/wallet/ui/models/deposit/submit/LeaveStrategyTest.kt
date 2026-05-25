@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.deposit.submit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.OPERATION_LEAVE
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class LeaveStrategyTest {

    private val nodeAddress = TextFieldState()
    private val chainRepo: ChainAccountAddressRepository = mockk()
    private val specificRepo: BlockChainSpecificRepository = mockk()

    @Test
    fun `Thor leave memo encodes node address and srcTokenValue is zero`() = runTest {
        coEvery { chainRepo.isValid(Chain.ThorChain, "thorNode") } returns true
        givenSpecific()
        nodeAddress.setTextAndPlaceCursorAtEnd("thorNode")

        val tx = build(Chain.ThorChain).build()

        assertEquals("LEAVE:thorNode", tx.memo)
        assertEquals(BigInteger.ZERO, tx.srcTokenValue.value)
        assertEquals(OPERATION_LEAVE, tx.operation)
    }

    @Test
    fun `Maya leave srcTokenValue is one`() = runTest {
        coEvery { chainRepo.isValid(Chain.MayaChain, "mayaNode") } returns true
        givenSpecific()
        nodeAddress.setTextAndPlaceCursorAtEnd("mayaNode")

        val tx = build(Chain.MayaChain).build()

        assertEquals(BigInteger.ONE, tx.srcTokenValue.value)
    }

    @Test
    fun `leave throws when node address is blank`() = runTest {
        coEvery { chainRepo.isValid(any(), any()) } returns false

        assertFailsWith<InvalidTransactionDataException> { build(Chain.ThorChain).build() }
    }

    private fun build(chain: Chain) =
        LeaveStrategy(
            vaultIdProvider = { "vault-1" },
            chainProvider = { chain },
            selectedTokenProvider = { coin(chain) },
            nodeAddressFieldState = nodeAddress,
            chainAccountAddressRepository = chainRepo,
            blockChainSpecificRepository = specificRepo,
            calculateGasFee = { _, token, _ -> TokenValue(BigInteger.ONE, token) },
            getFeesFiatValue = { _, _, _ -> estimatedFee(chain) },
        )

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
                BlockChainSpecific.THORChain(
                    accountNumber = BigInteger.ZERO,
                    sequence = BigInteger.ZERO,
                    fee = BigInteger.ZERO,
                    isDeposit = true,
                    transactionType =
                        vultisig.keysign.v1.TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
                )
            )
    }

    private fun estimatedFee(chain: Chain) =
        EstimatedGasFee(
            formattedFiatValue = "$0.01",
            formattedTokenValue = "0.0001",
            tokenValue = TokenValue(BigInteger.ONE, coin(chain)),
            fiatValue = mockk(relaxed = true),
        )

    private fun coin(chain: Chain): Coin =
        Coin(
            chain = chain,
            ticker = if (chain == Chain.MayaChain) "CACAO" else "RUNE",
            logo = "",
            address = "self",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )
}
