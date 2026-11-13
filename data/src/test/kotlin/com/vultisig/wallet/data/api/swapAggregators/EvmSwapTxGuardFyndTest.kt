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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class EvmSwapTxGuardFyndTest {

    @Test
    fun `accepts a Fynd swap to the pinned router on each supported chain`() {
        mapOf(
                Chain.Ethereum to "0x1644D2477f809cc2C71bCCFd6Dc9497E3F83210d",
                Chain.Base to "0xAbA5B53b03eAfaD1C5fc8BD5Fc765fC85Bb3de67",
                Chain.Arbitrum to "0x924F147c50eA59f5180a26031A8b65B2aA1e81Cd",
            )
            .forEach { (chain, router) ->
                EvmSwapTxGuard.check(payload(chain, tx(to = router, value = SOLD.toString())))
            }
    }

    @Test
    fun `refuses a Fynd swap to the JIT router or another contract`() {
        listOf(JIT_ROUTER, KYBER_ROUTER).forEach { to ->
            shouldThrow<IllegalArgumentException> {
                EvmSwapTxGuard.check(payload(Chain.Ethereum, tx(to = to, value = SOLD.toString())))
            }
        }
    }

    @Test
    fun `refuses another chain's Fynd router`() {
        shouldThrow<IllegalArgumentException> {
            EvmSwapTxGuard.check(payload(Chain.Base, tx(to = ETH_ROUTER, value = SOLD.toString())))
        }
    }

    @Test
    fun `refuses native value above the quoted amount`() {
        shouldThrow<IllegalArgumentException> {
            EvmSwapTxGuard.check(
                payload(Chain.Ethereum, tx(to = ETH_ROUTER, value = (SOLD + BigInteger.ONE).toString()))
            )
        }
    }

    @Test
    fun `refuses native value on an ERC-20 sale`() {
        shouldThrow<IllegalArgumentException> {
            EvmSwapTxGuard.check(
                payload(Chain.Ethereum, tx(to = ETH_ROUTER, value = "1"), from = usdc(Chain.Ethereum))
            )
        }
    }

    @Test
    fun `accepts an ERC-20 sale with no native value`() {
        EvmSwapTxGuard.check(
            payload(Chain.Ethereum, tx(to = ETH_ROUTER, value = "0"), from = usdc(Chain.Ethereum))
        )
    }

    @Test
    fun `knows no Fynd router outside Ethereum, Base and Arbitrum`() {
        assertNull(EvmSwapTxGuard.fyndRouter(Chain.Polygon))
        assertEquals(ETH_ROUTER.lowercase(), EvmSwapTxGuard.fyndRouter(Chain.Ethereum))
    }

    private fun payload(chain: Chain, tx: OneInchSwapTxJson, from: Coin = eth(chain)) =
        EVMSwapPayloadJson(
            fromCoin = from,
            toCoin = usdc(chain),
            fromAmount = SOLD,
            toAmountDecimal = BigDecimal("4.5"),
            quote = EVMSwapQuoteJson(dstAmount = "4500000", tx = tx),
            provider = "fynd",
        )

    private fun tx(to: String, value: String) =
        OneInchSwapTxJson(
            from = VAULT,
            to = to,
            gas = 300_000,
            data = "0xce25e49e",
            value = value,
            gasPrice = "1000000",
        )

    private fun eth(chain: Chain) =
        Coin(
            chain = chain,
            ticker = "ETH",
            logo = "",
            address = VAULT,
            decimal = 18,
            hexPublicKey = "pub",
            priceProviderID = "ethereum",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun usdc(chain: Chain) =
        eth(chain)
            .copy(
                ticker = "USDC",
                decimal = 6,
                contractAddress = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
                isNativeToken = false,
            )

    private companion object {
        val SOLD: BigInteger = BigInteger("1500000000000000")
        const val VAULT = "0x00000000000000000000000000000000000000aa"
        const val ETH_ROUTER = "0x1644D2477f809cc2C71bCCFd6Dc9497E3F83210d"
        const val JIT_ROUTER = "0xdaa2fe509de861d53e81940fdb8353b33126c930"
        const val KYBER_ROUTER = "0x6131b5fae19ea4f9d964eac0408e4408b66337b5"
    }
}
