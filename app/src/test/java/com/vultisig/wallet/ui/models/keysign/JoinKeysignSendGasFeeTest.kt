package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import org.junit.jupiter.api.Test

internal class JoinKeysignSendGasFeeTest {

    private val ethCoin =
        Coin(
            chain = Chain.Ethereum,
            ticker = "ETH",
            logo = "eth",
            address = "0xaddr",
            decimal = 18,
            hexPublicKey = "hex",
            priceProviderID = "ethereum",
            contractAddress = "",
            isNativeToken = true,
        )

    private val solCoin =
        Coin(
            chain = Chain.Solana,
            ticker = "SOL",
            logo = "sol",
            address = "soladdr",
            decimal = 9,
            hexPublicKey = "hex",
            priceProviderID = "solana",
            contractAddress = "",
            isNativeToken = true,
        )

    /** Contract-call QR: gasLimit from payload (100,000) must be used, not the transfer default. */
    @Test
    fun `evm contract call uses gasLimit from payload`() {
        val gasLimit = BigInteger.valueOf(100_000)
        val maxFeePerGasWei = BigInteger.valueOf(30_000_000_000L)
        val specific =
            BlockChainSpecific.Ethereum(
                maxFeePerGasWei = maxFeePerGasWei,
                priorityFeeWei = BigInteger.valueOf(1_000_000_000L),
                nonce = BigInteger.ZERO,
                gasLimit = gasLimit,
            )
        val fallback = BigInteger.valueOf(21_000) * maxFeePerGasWei

        val result =
            computeJoinKeysignNetworkFee(
                blockChainSpecific = specific,
                nativeCoin = ethCoin,
                fallbackFeeAmount = fallback,
            )

        result shouldBe TokenValue(value = maxFeePerGasWei * gasLimit, token = ethCoin)
    }

    /** Plain ETH transfer: gasLimit = 21,000 in payload → fee must match that limit exactly. */
    @Test
    fun `evm plain transfer uses gasLimit 21000 from payload`() {
        val gasLimit = BigInteger.valueOf(21_000)
        val maxFeePerGasWei = BigInteger.valueOf(30_000_000_000L)
        val specific =
            BlockChainSpecific.Ethereum(
                maxFeePerGasWei = maxFeePerGasWei,
                priorityFeeWei = BigInteger.valueOf(1_000_000_000L),
                nonce = BigInteger.ZERO,
                gasLimit = gasLimit,
            )

        val result =
            computeJoinKeysignNetworkFee(
                blockChainSpecific = specific,
                nativeCoin = ethCoin,
                fallbackFeeAmount = BigInteger.ZERO,
            )

        result shouldBe TokenValue(value = maxFeePerGasWei * gasLimit, token = ethCoin)
    }

    /** Non-EVM chain (Solana): fallback fee amount from the fee service must be passed through. */
    @Test
    fun `non-evm chain passes fallback fee through`() {
        val fallback = BigInteger.valueOf(5_000)
        val specific =
            BlockChainSpecific.Solana(
                recentBlockHash = "hash",
                priorityFee = BigInteger.ZERO,
                priorityLimit = BigInteger.ZERO,
            )

        val result =
            computeJoinKeysignNetworkFee(
                blockChainSpecific = specific,
                nativeCoin = solCoin,
                fallbackFeeAmount = fallback,
            )

        result shouldBe TokenValue(value = fallback, token = solCoin)
    }
}
