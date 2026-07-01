package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
     * A single-signature transaction has a one-byte shortvec prefix, so the signer-0 slot begins at
     * offset 1 and the message follows the 64-byte signature slot. Getting either offset wrong
     * would corrupt every raw (Jupiter swap / dApp) broadcast.
     */
    @Test
    fun `extractRawMessage locates the signer-0 slot and message for a single-signature tx`() {
        val message = byteArrayOf(0x09, 0x08, 0x07, 0x06, 0x05)
        val tx = ByteArray(1 + 64 + message.size)
        tx[0] = 0x01 // shortvec: one signature
        message.copyInto(tx, 1 + 64)

        val raw = SolanaHelper("").extractRawMessage(tx)

        assertEquals(1, raw.signatureOffset)
        assertArrayEquals(message, raw.messageBytes)
    }

    /** shortvec (compact-u16) must decode multi-byte values with continuation bits correctly. */
    @Test
    fun `decodeShortVec decodes single and multi byte values`() {
        val helper = SolanaHelper("")

        val (single, singleLen) = helper.decodeShortVec(byteArrayOf(0x01), 0)
        assertEquals(1, single)
        assertEquals(1, singleLen)

        // 0x80, 0x01 => (0 & 0x7F) | (1 << 7) = 128, consuming two bytes.
        val (multi, multiLen) = helper.decodeShortVec(byteArrayOf(0x80.toByte(), 0x01), 0)
        assertEquals(128, multi)
        assertEquals(2, multiLen)
    }

    /**
     * A compact-u16 is at most 3 bytes; a crafted continuation-byte run must be rejected rather
     * than inflating the count until `signatureCount * 64` overflows Int and collapses the message
     * offset onto attacker-chosen bytes.
     */
    @Test
    fun `decodeShortVec rejects an over-long continuation run`() {
        val helper = SolanaHelper("")
        val crafted = byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x01)

        assertThrows<IllegalArgumentException> { helper.decodeShortVec(crafted, 0) }
    }

    /**
     * A truncated buffer whose signature section consumes every byte leaves a zero-length message.
     * `extractRawMessage` must refuse it instead of handing an empty pre-image to signing.
     */
    @Test
    fun `extractRawMessage rejects a zero-length message`() {
        val tx = ByteArray(1 + 64)
        tx[0] = 0x01 // shortvec: one signature, no message bytes follow

        assertThrows<IllegalArgumentException> { SolanaHelper("").extractRawMessage(tx) }
    }
}
