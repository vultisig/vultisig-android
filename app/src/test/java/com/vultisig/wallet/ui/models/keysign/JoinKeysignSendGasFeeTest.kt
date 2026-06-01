package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.cosmosNativeDenom
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.proto.v1.SignDirectProto
import com.vultisig.wallet.data.usecases.Amount
import com.vultisig.wallet.data.usecases.CosmosMessage
import com.vultisig.wallet.data.usecases.Fee
import com.vultisig.wallet.data.usecases.ParseCosmosMessageUseCase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.CosmosCoin
import vultisig.keysign.v1.CosmosFee
import vultisig.keysign.v1.SignAmino
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

    /** dApp Amino fee in the chain's native denom is summed and returned. */
    @Test
    fun `dapp fee reads native-denom amount from signAmino`() {
        val payload = cosmosPayload(signAmino = aminoFee("rune" to "2000000"))

        payload.dappSuppliedNativeFee(Chain.ThorChain, failingParseCosmos) shouldBe
            BigInteger.valueOf(2_000_000)
    }

    /**
     * The Rujira bug (#4390): a dApp that signs `fee.amount = 0` must surface `0`, not `null` — so
     * the caller shows the real zero fee instead of falling back to the wallet's estimate.
     */
    @Test
    fun `dapp fee of zero is returned as zero, not null`() {
        val payload = cosmosPayload(signAmino = aminoFee("rune" to "0"))

        payload.dappSuppliedNativeFee(Chain.ThorChain, failingParseCosmos) shouldBe BigInteger.ZERO
    }

    /** Only the native-denom entries are summed; a co-listed foreign denom is ignored. */
    @Test
    fun `dapp fee sums only native-denom entries`() {
        val payload = cosmosPayload(signAmino = aminoFee("rune" to "100", "tcy" to "50"))

        payload.dappSuppliedNativeFee(Chain.ThorChain, failingParseCosmos) shouldBe
            BigInteger.valueOf(100)
    }

    /** A fee specified purely in a non-native denom is treated as "no dApp fee" (fall back). */
    @Test
    fun `dapp fee returns null when no native-denom entry present`() {
        val payload = cosmosPayload(signAmino = aminoFee("tcy" to "50"))

        payload.dappSuppliedNativeFee(Chain.ThorChain, failingParseCosmos) shouldBe null
    }

    /**
     * An unparseable amount must not be silently summed to zero — return null to use the estimate.
     */
    @Test
    fun `dapp fee returns null when a matched amount is unparseable`() {
        val payload = cosmosPayload(signAmino = aminoFee("rune" to "not-a-number"))

        payload.dappSuppliedNativeFee(Chain.ThorChain, failingParseCosmos) shouldBe null
    }

    /**
     * signDirect requests have no Amino block; the fee is decoded from authInfo via the use case.
     */
    @Test
    fun `dapp fee reads native-denom amount from signDirect authInfo`() {
        val payload = cosmosPayload(signDirect = signDirect())
        val parse = parseReturning(directFee("rune" to "5000"))

        payload.dappSuppliedNativeFee(Chain.ThorChain, parse) shouldBe BigInteger.valueOf(5_000)
    }

    /** A malformed signDirect payload (parse throws) falls back rather than crashing. */
    @Test
    fun `dapp fee returns null when signDirect cannot be parsed`() {
        val payload = cosmosPayload(signDirect = signDirect())
        val parse = ParseCosmosMessageUseCase { error("malformed authInfo") }

        payload.dappSuppliedNativeFee(Chain.ThorChain, parse) shouldBe null
    }

    /** Amino is consulted first; signDirect is only parsed when Amino has no native-denom entry. */
    @Test
    fun `dapp fee prefers signAmino over signDirect`() {
        val payload = cosmosPayload(signAmino = aminoFee("rune" to "7"), signDirect = signDirect())

        payload.dappSuppliedNativeFee(Chain.ThorChain, failingParseCosmos) shouldBe
            BigInteger.valueOf(7)
    }

    /**
     * Non-Cosmos chains have no native denom, so a stray Amino block is never interpreted as a fee.
     */
    @Test
    fun `dapp fee returns null for non-cosmos chains`() {
        val payload = cosmosPayload(signAmino = aminoFee("rune" to "2000000"))

        payload.dappSuppliedNativeFee(Chain.Ethereum, failingParseCosmos) shouldBe null
    }

    /** A wallet-built native tx (no signAmino/signDirect) has no dApp fee. */
    @Test
    fun `dapp fee returns null when neither signAmino nor signDirect is present`() {
        cosmosPayload().dappSuppliedNativeFee(Chain.ThorChain, failingParseCosmos) shouldBe null
    }

    @Test
    fun `cosmosNativeDenom maps cosmos chains and is null for others`() {
        Chain.ThorChain.cosmosNativeDenom shouldBe "rune"
        Chain.MayaChain.cosmosNativeDenom shouldBe "cacao"
        Chain.GaiaChain.cosmosNativeDenom shouldBe "uatom"
        Chain.TerraClassic.cosmosNativeDenom shouldBe "uluna"
        Chain.Ethereum.cosmosNativeDenom shouldBe null
        Chain.Bitcoin.cosmosNativeDenom shouldBe null
    }

    private val failingParseCosmos = ParseCosmosMessageUseCase {
        error("parseCosmosMessage should not be called")
    }

    private fun parseReturning(message: CosmosMessage) = ParseCosmosMessageUseCase { message }

    private fun aminoFee(vararg amounts: Pair<String, String>) =
        SignAmino(
            fee =
                CosmosFee(amount = amounts.map { CosmosCoin(denom = it.first, amount = it.second) })
        )

    private fun directFee(vararg amounts: Pair<String, String>) =
        CosmosMessage(
            chainId = "thorchain-1",
            accountNumber = "1",
            sequence = "0",
            memo = "",
            messages = emptyList(),
            authInfoFee = Fee(amount = amounts.map { Amount(denom = it.first, amount = it.second) }),
        )

    private fun signDirect() =
        SignDirectProto(
            bodyBytes = "",
            authInfoBytes = "",
            chainId = "thorchain-1",
            accountNumber = "1",
        )

    private fun cosmosPayload(signAmino: SignAmino? = null, signDirect: SignDirectProto? = null) =
        KeysignPayload(
            coin = runeCoin,
            toAddress = "thoraddr",
            toAmount = BigInteger.ZERO,
            blockChainSpecific =
                BlockChainSpecific.THORChain(
                    accountNumber = BigInteger.ONE,
                    sequence = BigInteger.ZERO,
                    fee = BigInteger.valueOf(2_000_000),
                    isDeposit = false,
                    transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
                ),
            vaultPublicKeyECDSA = "pub",
            vaultLocalPartyID = "party",
            libType = null,
            wasmExecuteContractPayload = null,
            signAmino = signAmino,
            signDirect = signDirect,
        )
}
