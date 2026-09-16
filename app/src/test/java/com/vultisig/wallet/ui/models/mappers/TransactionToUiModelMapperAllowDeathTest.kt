@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * A Substrate send that carries the initiator's allow-death intent is signed as
 * `transfer_allow_death`, which can reap the sender. The co-signer only sees the payload through
 * the Verify screen, so the mapper has to surface the intent — and only the payload field decides,
 * never the amount.
 */
internal class TransactionToUiModelMapperAllowDeathTest {

    private val fiatValueToStringMapper: FiatValueToStringMapper = mockk(relaxed = true)
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper =
        mockk(relaxed = true)

    private fun mapper() =
        TransactionToUiModelMapperImpl(
            fiatValueToStringMapper = fiatValueToStringMapper,
            mapTokenValueToDecimalUiString = mapTokenValueToDecimalUiString,
        )

    @Test
    fun `an allow-death payload discloses that the send may empty the sender`() = runTest {
        every { mapTokenValueToDecimalUiString(any()) } returns "0"

        val uiModel = mapper().invoke(substrateTransaction(allowDeath = true))

        uiModel.emptiesSenderAccount shouldBe true
    }

    @Test
    fun `a keep-alive payload does not, whatever the amount`() = runTest {
        every { mapTokenValueToDecimalUiString(any()) } returns "0"

        val uiModel =
            mapper()
                .invoke(substrateTransaction(allowDeath = false, amount = BigInteger("999999999")))

        uiModel.emptiesSenderAccount shouldBe false
    }

    private fun substrateTransaction(
        allowDeath: Boolean,
        amount: BigInteger = BigInteger.TEN,
    ): Transaction =
        Transaction(
            id = "tx-1",
            vaultId = "vault-1",
            chainId = Chain.Bittensor.id,
            token = tao,
            srcAddress = "5Ej64CJQSZFsPK4byPVCZhNWiYeRXnELwYw4KYQBq6yfvaQ3",
            dstAddress = "5DtJMgqtYZg6NyCM1KDkmgZ6nW7pKgL1fneDHQtwPjBrQuXG",
            tokenValue = TokenValue(value = amount, token = tao),
            fiatValue = FiatValue(BigDecimal.ZERO, "USD"),
            gasFee = TokenValue(value = BigInteger.ONE, token = tao),
            totalGas = "0",
            memo = null,
            estimatedFee = "0",
            blockChainSpecific =
                BlockChainSpecific.Polkadot(
                    recentBlockHash = "0x00",
                    nonce = BigInteger.ZERO,
                    currentBlockNumber = BigInteger.ONE,
                    specVersion = 260u,
                    transactionVersion = 5u,
                    genesisHash = "0x00",
                    gas = 200_000UL,
                    allowDeath = allowDeath,
                ),
        )

    private companion object {
        val tao =
            Coin(
                chain = Chain.Bittensor,
                ticker = "TAO",
                logo = "tao",
                address = "5Ej64CJQSZFsPK4byPVCZhNWiYeRXnELwYw4KYQBq6yfvaQ3",
                decimal = 9,
                hexPublicKey = "hex",
                priceProviderID = "bittensor",
                contractAddress = "",
                isNativeToken = true,
            )
    }
}
