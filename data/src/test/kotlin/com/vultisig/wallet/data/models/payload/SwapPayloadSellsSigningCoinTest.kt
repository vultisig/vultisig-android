package com.vultisig.wallet.data.models.payload

import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.THORChainSwapPayload
import io.kotest.assertions.throwables.shouldThrow
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Test

class SwapPayloadSellsSigningCoinTest {

    @Test
    fun `swap selling the signing coin is accepted by every provider`() {
        providers(sold = usdt).forEach { it.requireSellsSigningCoin(usdt) }
    }

    @Test
    fun `EVM contract differing only by case is accepted`() {
        providers(sold = usdt.copy(contractAddress = usdt.contractAddress.lowercase()))
            .forEach { it.requireSellsSigningCoin(usdt) }
    }

    @Test
    fun `swap selling a coin on another chain than the signing coin is rejected`() {
        providers(sold = btc).forEach { shouldThrow<IllegalArgumentException> { it.requireSellsSigningCoin(usdt) } }
    }

    @Test
    fun `swap selling the native coin while signing a token is rejected`() {
        providers(sold = eth).forEach { shouldThrow<IllegalArgumentException> { it.requireSellsSigningCoin(usdt) } }
    }

    @Test
    fun `swap selling another contract is rejected`() {
        providers(sold = usdt.copy(contractAddress = "0x1111111111111111111111111111111111111111"))
            .forEach { shouldThrow<IllegalArgumentException> { it.requireSellsSigningCoin(usdt) } }
    }

    @Test
    fun `non-EVM contracts compare exactly`() {
        val mint = sol.copy(contractAddress = "So11111111111111111111111111111111111111112", isNativeToken = false)
        providers(sold = mint.copy(contractAddress = mint.contractAddress.lowercase()))
            .forEach { shouldThrow<IllegalArgumentException> { it.requireSellsSigningCoin(mint) } }
    }

    private fun providers(sold: Coin): List<SwapPayload> {
        val thor =
            THORChainSwapPayload(
                fromAddress = sold.address,
                fromCoin = sold,
                toCoin = eth,
                vaultAddress = "",
                routerAddress = null,
                fromAmount = BigInteger.ONE,
                toAmountDecimal = BigDecimal.ONE,
                toAmountLimit = "0",
                streamingInterval = "1",
                streamingQuantity = "0",
                expirationTime = 0u,
                isAffiliate = false,
            )
        val evm =
            EVMSwapPayloadJson(
                fromCoin = sold,
                toCoin = eth,
                fromAmount = BigInteger.ONE,
                toAmountDecimal = BigDecimal.ONE,
                quote =
                    EVMSwapQuoteJson(
                        dstAmount = "1",
                        tx =
                            OneInchSwapTxJson(
                                from = sold.address,
                                to = sold.address,
                                gas = 1,
                                data = "0x",
                                value = "0",
                                gasPrice = "1",
                            ),
                    ),
                provider = "1inch",
            )
        return listOf(
            SwapPayload.EVM(evm),
            SwapPayload.ThorChain(thor),
            SwapPayload.MayaChain(thor),
        )
    }

    private fun coin(chain: Chain, ticker: String, contract: String, native: Boolean) =
        Coin(
            chain = chain,
            ticker = ticker,
            logo = ticker,
            address = "addr-$ticker",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = contract,
            isNativeToken = native,
        )

    private val usdt = coin(Chain.Ethereum, "USDT", "0xdAC17F958D2ee523a2206206994597C13D831ec7", false)
    private val eth = coin(Chain.Ethereum, "ETH", "", true)
    private val btc = coin(Chain.Bitcoin, "BTC", "", true)
    private val sol = coin(Chain.Solana, "SOL", "", true)
}
