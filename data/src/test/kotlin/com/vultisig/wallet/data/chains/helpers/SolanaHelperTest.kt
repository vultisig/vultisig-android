package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.utils.Numeric
import java.math.BigInteger
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import vultisig.keysign.v1.SignSolana

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

    // dApp co-signing path (issue #5223): a dApp-supplied raw transaction must be hashed over its
    // original message bytes, not a WalletCore decode/re-encode of them. The re-encode is not
    // guaranteed to reproduce the original bytes for a v0 message referencing an Address Lookup
    // Table, so co-signing devices would compute mismatching pre-image hashes and stall the
    // ceremony. The wire envelope is `[shortvec signature count][count × 64-byte slot][message]`.
    // Each test builds an envelope whose message it controls, then asserts the pre-image hash is
    // the hex of exactly those message bytes. This path is pure Kotlin (no WalletCore JNI).

    @Test
    fun `raw v0 transaction with an address lookup table hashes the original message verbatim`() {
        val message = v0MessageWithLookupTable()
        val tx = base64Of(rawTransaction(signatureCount = 1, message = message))

        val hashes = SolanaHelper("").getPreSignedImageHash(rawTxPayload(tx))

        assertEquals(listOf(Numeric.toHexStringNoPrefix(message)), hashes)
    }

    @Test
    fun `raw legacy transaction hashes the original message verbatim`() {
        // Legacy (non-versioned) message: first byte < 0x80, handled the same way.
        val message = byteArrayOf(1, 0, 1) + key(0x41) + key(0x42) + key(0x43) + byteArrayOf(0)
        val tx = base64Of(rawTransaction(signatureCount = 1, message = message))

        val hashes = SolanaHelper("").getPreSignedImageHash(rawTxPayload(tx))

        assertEquals(listOf(Numeric.toHexStringNoPrefix(message)), hashes)
    }

    @Test
    fun `raw transaction message begins after every declared signature slot`() {
        // Two declared signers: the message must start after both 64-byte slots, not just the
        // first.
        val message = ByteArray(40) { 0xAB.toByte() }
        val tx = base64Of(rawTransaction(signatureCount = 2, message = message))

        val hashes = SolanaHelper("").getPreSignedImageHash(rawTxPayload(tx))

        assertEquals(listOf(Numeric.toHexStringNoPrefix(message)), hashes)
    }

    @Test
    fun `multiple raw transactions produce one hash each in order`() {
        val first = v0MessageWithLookupTable()
        val second = ByteArray(48) { 0xCD.toByte() }
        val txs =
            arrayOf(
                base64Of(rawTransaction(signatureCount = 1, message = first)),
                base64Of(rawTransaction(signatureCount = 1, message = second)),
            )

        val hashes = SolanaHelper("").getPreSignedImageHash(rawTxPayload(*txs))

        assertEquals(
            listOf(Numeric.toHexStringNoPrefix(first), Numeric.toHexStringNoPrefix(second)),
            hashes,
        )
    }

    @Test
    fun `raw transaction declaring no signatures is rejected`() {
        // 0x00 shortvec: zero signature slots.
        val error =
            assertThrows<IllegalStateException> {
                SolanaHelper("").getPreSignedImageHash(rawTxPayload(base64Of(byteArrayOf(0x00))))
            }

        assertEquals("Solana transaction declares no signatures", error.message)
    }

    @Test
    fun `raw transaction shorter than its declared signatures is rejected`() {
        // Declares one signature but carries neither the 64-byte slot nor a message.
        val error =
            assertThrows<IllegalStateException> {
                SolanaHelper("").getPreSignedImageHash(rawTxPayload(base64Of(byteArrayOf(0x01))))
            }

        assertEquals("Solana transaction too short for its 1 declared signature(s)", error.message)
    }

    @Test
    fun `raw transaction with an unterminated signature-count prefix is rejected`() {
        // Every byte sets the continuation bit, so the shortvec never terminates.
        val error =
            assertThrows<IllegalStateException> {
                SolanaHelper("")
                    .getPreSignedImageHash(rawTxPayload(base64Of(ByteArray(4) { 0xFF.toByte() })))
            }

        assertEquals("Malformed compact-u16 in Solana transaction", error.message)
    }

    /**
     * A `signSolana` payload with an empty batch has nothing to assemble; it must fail with a clear
     * message instead of returning an empty result. The guard runs before any WalletCore call, so
     * it is exercised without the native library.
     */
    @Test
    fun `signSolana batch with no raw transactions is rejected before assembly`() {
        val payload = solPayload(SignSolana(rawTransactions = emptyList()))

        val error =
            assertThrows<IllegalArgumentException> {
                SolanaHelper("").getSignedTransactions(payload, signatures = emptyMap())
            }
        assertEquals("signSolana payload carries no raw transactions", error.message)
    }

    /**
     * The single-transaction assembly keeps its strict contract: a `signAllTransactions` batch must
     * go through [SolanaHelper.getSignedTransactions], which delivers every transaction
     * (issue #5238).
     */
    @Test
    fun `single-transaction assembly rejects a multi-transaction batch`() {
        val payload = solPayload(SignSolana(rawTransactions = listOf("AAA=", "BBB=")))

        val error =
            assertThrows<IllegalArgumentException> {
                SolanaHelper("").getSignedTransaction(payload, signatures = emptyMap())
            }
        assertEquals("Expected exactly one Solana raw transaction", error.message)
    }

    /**
     * A raw Solana transaction envelope: `[shortvec count][count × zeroed 64-byte slot][message]`.
     */
    private fun rawTransaction(signatureCount: Int, message: ByteArray): ByteArray {
        require(signatureCount in 1..127) {
            "test helper only encodes a single-byte shortvec count"
        }
        return byteArrayOf(signatureCount.toByte()) + ByteArray(signatureCount * 64) + message
    }

    /**
     * A v0 (versioned) Solana message referencing an Address Lookup Table — the shape a
     * DEX/aggregator swap produces, and the case where WalletCore's decode/re-encode is not
     * guaranteed to round-trip the original bytes.
     */
    private fun v0MessageWithLookupTable(): ByteArray =
        byteArrayOf(0x80.toByte()) + // version prefix: v0
            byteArrayOf(1, 0, 1) + // header: 1 required sig, 0 readonly-signed, 1 readonly-unsigned
            byteArrayOf(3) +
            key(0x11) +
            key(0x12) +
            key(0x13) + // 3 static account keys
            key(0x22) + // recent blockhash
            byteArrayOf(1) + // 1 instruction:
            byteArrayOf(0x02) + //   program id index
            byteArrayOf(2, 0x00, 0x03) + //   2 account indexes (one supplied by the lookup table)
            byteArrayOf(
                5,
                0x09,
                0xDE.toByte(),
                0xAD.toByte(),
                0xBE.toByte(),
                0xEF.toByte(),
            ) + // data
            byteArrayOf(1) + // 1 address table lookup:
            key(0x33) + //   lookup table account key
            byteArrayOf(2, 0x05, 0x06) + //   2 writable indexes
            byteArrayOf(1, 0x07) //   1 readonly index

    private fun key(fill: Int): ByteArray = ByteArray(32) { fill.toByte() }

    private fun rawTxPayload(vararg rawTransactions: String): KeysignPayload =
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
            toAddress = "",
            toAmount = BigInteger.ZERO,
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
            signSolana = SignSolana(rawTransactions = rawTransactions.toList()),
        )

    private fun base64Of(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun solPayload(signSolana: SignSolana) =
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
            toAmount = BigInteger.ZERO,
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
            signSolana = signSolana,
        )
}
