package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.TransactionType

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

    private val runeCoin =
        Coin(
            chain = Chain.ThorChain,
            ticker = "RUNE",
            logo = "rune",
            address = "thoraddr",
            decimal = 8,
            hexPublicKey = "hex",
            priceProviderID = "thorchain",
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

    /** THORChain: fee must come from blockChainSpecific.fee, not the fallback. */
    @Test
    fun `thorchain uses fee from blockChainSpecific`() {
        val fee = BigInteger.valueOf(2_000_000)
        val specific =
            BlockChainSpecific.THORChain(
                accountNumber = BigInteger.ONE,
                sequence = BigInteger.ZERO,
                fee = fee,
                isDeposit = false,
                transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
            )

        val result =
            computeJoinKeysignNetworkFee(
                blockChainSpecific = specific,
                nativeCoin = runeCoin,
                fallbackFeeAmount = BigInteger.valueOf(99_999),
            )

        result shouldBe TokenValue(value = fee, token = runeCoin)
    }

    /**
     * Swap helper: Ethereum case must apply [EthereumFeeService.DEFAULT_SWAP_LIMIT] regardless of
     * the payload's gasLimit, so joiner output matches initiator output.
     */
    @Test
    fun `evm swap helper uses default swap limit`() {
        val payloadGasLimit = BigInteger.valueOf(21_000)
        val maxFeePerGasWei = BigInteger.valueOf(30_000_000_000L)
        val specific =
            BlockChainSpecific.Ethereum(
                maxFeePerGasWei = maxFeePerGasWei,
                priorityFeeWei = BigInteger.valueOf(1_000_000_000L),
                nonce = BigInteger.ZERO,
                gasLimit = payloadGasLimit,
            )

        val result =
            computeJoinKeysignSwapNetworkFee(
                blockChainSpecific = specific,
                nativeCoin = ethCoin,
                chain = Chain.Ethereum,
            )

        result shouldBe
            TokenValue(
                value = maxFeePerGasWei * EthereumFeeService.DEFAULT_SWAP_LIMIT,
                token = ethCoin,
            )
    }

    /**
     * defaultEvmSwapGasLimit must return DEFAULT_MANTLE_SWAP_LIMIT for Mantle and
     * DEFAULT_SWAP_LIMIT for every other EVM chain (Mantle has a much higher per-gas limit, so this
     * branch is the only thing keeping joiner output aligned with the initiator on Mantle swaps).
     */
    @Test
    fun `defaultEvmSwapGasLimit selects Mantle limit for Mantle and default elsewhere`() {
        defaultEvmSwapGasLimit(Chain.Mantle) shouldBe EthereumFeeService.DEFAULT_MANTLE_SWAP_LIMIT
        defaultEvmSwapGasLimit(Chain.Ethereum) shouldBe EthereumFeeService.DEFAULT_SWAP_LIMIT
    }

    /** Swap helper: THORChain returns blockChainSpecific.fee. */
    @Test
    fun `thorchain swap helper uses blockChainSpecific fee`() {
        val fee = BigInteger.valueOf(2_000_000)
        val specific =
            BlockChainSpecific.THORChain(
                accountNumber = BigInteger.ONE,
                sequence = BigInteger.ZERO,
                fee = fee,
                isDeposit = false,
                transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
            )

        val result =
            computeJoinKeysignSwapNetworkFee(
                blockChainSpecific = specific,
                nativeCoin = runeCoin,
                chain = Chain.ThorChain,
            )

        result shouldBe TokenValue(value = fee, token = runeCoin)
    }

    /**
     * Swap helper must reject subtypes the swap branch in [JoinKeysignViewModel.loadTransaction]
     * never reaches — guards against a future extension silently shipping a zero fee.
     */
    @Test
    fun `swap helper throws for unsupported subtype`() {
        val specific =
            BlockChainSpecific.Solana(
                recentBlockHash = "hash",
                priorityFee = BigInteger.ZERO,
                priorityLimit = BigInteger.ZERO,
            )

        shouldThrow<IllegalStateException> {
            computeJoinKeysignSwapNetworkFee(
                blockChainSpecific = specific,
                nativeCoin = solCoin,
                chain = Chain.Solana,
            )
        }
    }
}
