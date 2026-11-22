package com.vultisig.wallet.data.api.swapAggregators

import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import io.kotest.assertions.throwables.shouldThrow
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Verifies that only direct deposits with a decodable recipient and amount pass the EVM guard.
 */
internal class EvmSwapTxGuardSwapKitDepositTest {

    @Test
    fun `signs a real NEAR Intents USDT deposit`() {
        EvmSwapTxGuard.check(payload(depositTx()))
    }

    @Test
    fun `refuses an executable SwapKit router call`() {
        shouldThrow<IllegalArgumentException> {
            EvmSwapTxGuard.check(payload(depositTx().copy(to = ROUTER, data = "0x01020304")))
        }
    }

    @Test
    fun `uses the outer recipient of a value-only native deposit`() {
        val tx =
            depositTx().copy(to = ROUTER, data = "0x", value = SOLD.toString(), gas = 21_000)
        val swap = payload(tx).copy(fromCoin = usdt.copy(contractAddress = "", isNativeToken = true))

        EvmSwapTxGuard.check(swap)
        assertEquals(ROUTER, swap.swapKitDepositRecipient(Chain.Ethereum))

        shouldThrow<IllegalArgumentException> {
            EvmSwapTxGuard.check(swap.copy(fromAmount = SOLD + BigInteger.ONE))
        }
        shouldThrow<IllegalArgumentException> {
            EvmSwapTxGuard.check(swap.copy(quote = swap.quote.copy(tx = tx.copy(gas = 22_000))))
        }
    }

    @Test
    fun `refuses a SwapKit transfer call on a token the swap does not sell`() {
        val differentToken = depositTx().copy(to = USDC)

        shouldThrow<IllegalArgumentException> { EvmSwapTxGuard.check(payload(differentToken)) }
    }

    @Test
    fun `refuses a deposit that transfers more than the swap sells`() {
        shouldThrow<IllegalArgumentException> {
            EvmSwapTxGuard.check(payload(depositTx(), fromAmount = SOLD - BigInteger.ONE))
        }
    }

    @Test
    fun `refuses a deposit that attaches native value`() {
        shouldThrow<IllegalArgumentException> {
            EvmSwapTxGuard.check(payload(depositTx().copy(value = "1")))
        }
    }

    private fun payload(tx: OneInchSwapTxJson, fromAmount: BigInteger = SOLD) =
        EVMSwapPayloadJson(
            fromCoin = usdt,
            toCoin = sol,
            fromAmount = fromAmount,
            toAmountDecimal = BigDecimal("0.165993965"),
            quote = EVMSwapQuoteJson(dstAmount = "165993965", tx = tx),
            provider = "swapkit",
        )

    private fun depositTx() =
        OneInchSwapTxJson(
            from = VAULT,
            to = USDT,
            gas = 76_837,
            data = DEPOSIT_DATA,
            value = "0",
            gasPrice = "86448690",
        )

    private val usdt =
        Coin(
            chain = Chain.Ethereum,
            ticker = "USDT",
            logo = "",
            address = VAULT,
            decimal = 6,
            hexPublicKey = "pub",
            priceProviderID = "tether",
            contractAddress = USDT,
            isNativeToken = false,
        )

    private val sol =
        Coin(
            chain = Chain.Solana,
            ticker = "SOL",
            logo = "",
            address = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM",
            decimal = 9,
            hexPublicKey = "pub",
            priceProviderID = "solana",
            contractAddress = "",
            isNativeToken = true,
        )

    private companion object {
        const val VAULT = "0x28C6c06298d514Db089934071355E5743bf21d60"
        const val USDT = "0xdAC17F958D2ee523a2206206994597C13D831ec7"
        const val USDC = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"
        const val ROUTER = "0x9025b8ff35ca44f7018c3a37fe0f69e63dbb0743"
        const val DEPOSIT_DATA =
            "0xa9059cbb000000000000000000000000cb2ac797eff13ee982453f5722b74ab5c56741af" +
                "0000000000000000000000000000000000000000000000000000000001312d00"
        val SOLD: BigInteger = BigInteger.valueOf(20_000_000)
    }
}
