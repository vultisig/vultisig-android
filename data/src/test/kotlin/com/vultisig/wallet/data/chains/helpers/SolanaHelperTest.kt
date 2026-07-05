package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wallet.core.jni.proto.Solana

class SolanaHelperTest {

    /**
     * An SPL send whose sender holds no associated token account must fail with a clear,
     * ticker-bearing error rather than passing `null` into `CreateAndTransferToken` and crashing
     * with an NPE. The guard runs before any WalletCore call, so it is exercised without the native
     * library.
     */
    @Test
    fun `SPL transfer without a sender token account fails with a ticker-bearing error`() {
        val usdc =
            Coin(
                chain = Chain.Solana,
                ticker = "USDC",
                logo = "",
                address = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
                isNativeToken = false,
            )
        val payload =
            KeysignPayload(
                coin = usdc,
                toAddress = "3xM8c79mk7fvcz5ENZgMbChPJGWZAjFqwdDzZp4R2gHR",
                toAmount = BigInteger.valueOf(1_000_000L),
                blockChainSpecific =
                    BlockChainSpecific.Solana(
                        recentBlockHash = "",
                        priorityFee = BigInteger.ZERO,
                        priorityLimit = SOLANA_PRIORITY_FEE_LIMIT.toBigInteger(),
                        fromAddressPubKey = null,
                        toAddressPubKey = null,
                        programId = false,
                    ),
                vaultPublicKeyECDSA = "",
                vaultLocalPartyID = "",
                libType = SigningLibType.GG20,
                wasmExecuteContractPayload = null,
            )

        val error =
            assertThrows<IllegalStateException> { SolanaHelper("").getPreSignedImageHash(payload) }
        assertEquals(SOLANA_MISSING_TOKEN_ACCOUNT_PREFIX + usdc.ticker, error.message)
    }

    /**
     * A zero compute-limit override (e.g. a joining device reconstructing a payload with a missing
     * value) must fall back to the app's default limit rather than encoding a zero compute budget.
     * Uses [SolanaHelper.getSwapPreSignedInputData] since it is the only public entry point that
     * returns the raw signing-input bytes without invoking a WalletCore signing/hashing call.
     */
    @Test
    fun `zero priorityLimit falls back to the default compute-unit limit`() {
        try {
            val inputData =
                SolanaHelper("").getSwapPreSignedInputData(solanaSwapPayload(BigInteger.ZERO))
            val signingInput = Solana.SigningInput.parseFrom(inputData)
            assertEquals(SOLANA_PRIORITY_FEE_LIMIT, signingInput.priorityFeeLimit.limit)
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `positive priorityLimit is passed through unchanged`() {
        try {
            val inputData =
                SolanaHelper("")
                    .getSwapPreSignedInputData(solanaSwapPayload(BigInteger.valueOf(42_000L)))
            val signingInput = Solana.SigningInput.parseFrom(inputData)
            assertEquals(42_000, signingInput.priorityFeeLimit.limit)
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    private fun solanaSwapPayload(priorityLimit: BigInteger): KeysignPayload =
        KeysignPayload(
            coin =
                Coin(
                    chain = Chain.Solana,
                    ticker = "SOL",
                    logo = "",
                    address = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM",
                    decimal = 9,
                    hexPublicKey = "",
                    priceProviderID = "",
                    contractAddress = "",
                    isNativeToken = true,
                ),
            toAddress = "3xM8c79mk7fvcz5ENZgMbChPJGWZAjFqwdDzZp4R2gHR",
            toAmount = BigInteger.valueOf(1_000_000L),
            blockChainSpecific =
                BlockChainSpecific.Solana(
                    recentBlockHash = "",
                    priorityFee = BigInteger.ZERO,
                    priorityLimit = priorityLimit,
                    fromAddressPubKey = null,
                    toAddressPubKey = null,
                    programId = false,
                ),
            memo = "SWAP:THOR.RUNE:thor1abc:0",
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "",
            libType = SigningLibType.GG20,
            wasmExecuteContractPayload = null,
        )

    private fun skipIfJniUnavailable(e: Throwable) {
        if (
            e is UnsatisfiedLinkError ||
                e is ExceptionInInitializerError ||
                e is NoClassDefFoundError
        ) {
            assumeTrue(false, "WalletCore JNI not available: ${e.message}")
        } else throw e
    }
}
