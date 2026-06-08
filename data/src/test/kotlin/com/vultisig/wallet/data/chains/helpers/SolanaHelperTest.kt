package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

/**
 * Covers the SPL token branch of [SolanaHelper.getPreSignedInputData] across the three
 * sender/recipient associated-token-account (ATA) combinations. The missing-sender-ATA case used to
 * pass `null` to `CreateAndTransferToken.setSenderTokenAddress` and crash with an NPE; it must now
 * abort with a clear error, matching iOS. The JNI-dependent assertions are skipped when the
 * WalletCore native library is unavailable, as in [UtxoHelperTest].
 */
class SolanaHelperTest {

    private val usdcCoin =
        Coin(
            chain = Chain.Solana,
            ticker = "USDC",
            logo = "",
            address = SENDER_ADDRESS,
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = USDC_MINT,
            isNativeToken = false,
        )

    private fun splPayload(fromAddressPubKey: String?, toAddressPubKey: String?): KeysignPayload =
        KeysignPayload(
            coin = usdcCoin,
            toAddress = RECIPIENT_ADDRESS,
            toAmount = BigInteger.valueOf(1_000_000L),
            blockChainSpecific =
                BlockChainSpecific.Solana(
                    recentBlockHash = RECENT_BLOCK_HASH,
                    priorityFee = BigInteger.ZERO,
                    priorityLimit = SOLANA_PRIORITY_FEE_LIMIT.toBigInteger(),
                    fromAddressPubKey = fromAddressPubKey,
                    toAddressPubKey = toAddressPubKey,
                    programId = false,
                ),
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "",
            libType = SigningLibType.GG20,
            wasmExecuteContractPayload = null,
        )

    @Test
    fun `SPL transfer aborts with a clear error when the sender has no associated token account`() {
        val helper = SolanaHelper(vaultHexPublicKey = "")
        val payload = splPayload(fromAddressPubKey = null, toAddressPubKey = null)

        try {
            helper.getPreSignedImageHash(payload)
            fail("Expected a missing sender ATA to abort signing")
        } catch (e: IllegalStateException) {
            assertEquals(MISSING_SENDER_ATA_ERROR, e.message)
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `SPL transfer builds a create-and-transfer tx when only the recipient ATA is missing`() {
        val helper = SolanaHelper(vaultHexPublicKey = "")
        val payload = splPayload(fromAddressPubKey = SENDER_TOKEN_ACCOUNT, toAddressPubKey = null)

        try {
            assertTrue(helper.getPreSignedImageHash(payload).isNotEmpty())
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `SPL transfer builds a token transfer when both associated token accounts exist`() {
        val helper = SolanaHelper(vaultHexPublicKey = "")
        val payload =
            splPayload(
                fromAddressPubKey = SENDER_TOKEN_ACCOUNT,
                toAddressPubKey = RECIPIENT_TOKEN_ACCOUNT,
            )

        try {
            assertTrue(helper.getPreSignedImageHash(payload).isNotEmpty())
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    private fun skipIfJniUnavailable(e: Throwable) {
        if (
            e is UnsatisfiedLinkError ||
                e is ExceptionInInitializerError ||
                e is NoClassDefFoundError
        ) {
            assumeTrue(false, "WalletCore JNI not available: ${e.message}")
        } else throw e
    }

    private companion object {
        const val SENDER_ADDRESS = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM"
        const val RECIPIENT_ADDRESS = "3xM8c79mk7fvcz5ENZgMbChPJGWZAjFqwdDzZp4R2gHR"
        const val USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
        const val SENDER_TOKEN_ACCOUNT = "2Z6WGEsKfycoKAnTogC1M35dMgMtnLjwVZVLXcGQsFpA"
        const val RECIPIENT_TOKEN_ACCOUNT = "8vdCT37Lc6MLXfep8K5XFyYU6HTPpaqjJ7hyXJjUCc12"
        const val RECENT_BLOCK_HASH = "4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R"

        const val MISSING_SENDER_ATA_ERROR =
            "SPL token transfer failed: sender's associated token account not found. " +
                "Please ensure you have this token in your wallet."
    }
}
