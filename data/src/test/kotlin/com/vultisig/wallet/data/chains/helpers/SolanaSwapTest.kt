package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wallet.core.jni.proto.Solana

class SolanaSwapTest {

    private val sol =
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
        )

    private fun swapPayload(base64Tx: String): EVMSwapPayloadJson =
        EVMSwapPayloadJson(
            fromCoin = sol,
            toCoin = sol,
            fromAmount = BigInteger.ONE,
            toAmountDecimal = BigDecimal.ONE,
            quote =
                EVMSwapQuoteJson(
                    dstAmount = "1",
                    tx =
                        OneInchSwapTxJson(
                            from = "",
                            to = "",
                            gas = 0,
                            data = base64Tx,
                            value = "0",
                            gasPrice = "0",
                        ),
                ),
            provider = "jupiter",
        )

    private fun keysignPayload(): KeysignPayload =
        KeysignPayload(
            coin = sol,
            toAddress = "3xM8c79mk7fvcz5ENZgMbChPJGWZAjFqwdDzZp4R2gHR",
            toAmount = BigInteger.ONE,
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

    /**
     * The Jupiter-swap dispatch (issue #5117): the provider's pre-built transaction must be hashed
     * verbatim — its message extracted straight from the wire bytes — rather than re-serialized
     * through WalletCore's compiler (which yields `AccountLoadedTwice` on v0 lookup-table txs). A
     * single-signature tx is `[0x01][64-byte sig slot][message]`, so the hash is exactly the
     * message bytes.
     */
    @Test
    fun `getPreSignedImageHash extracts the provider message verbatim`() {
        val message = byteArrayOf(0x09, 0x08, 0x07, 0x06, 0x05)
        val tx = ByteArray(1 + 64 + message.size)
        tx[0] = 0x01 // shortvec: one signature
        message.copyInto(tx, 1 + 64)
        val base64Tx = Base64.getEncoder().encodeToString(tx)

        val hashes = SolanaSwap("").getPreSignedImageHash(swapPayload(base64Tx), keysignPayload())

        assertEquals(listOf("0908070605"), hashes)
    }

    /**
     * Builds a v0 [Solana.RawMessage] with [staticKeys] static account keys and one address-table
     * lookup referencing [writableRefs] writable and [readonlyRefs] readonly indexes.
     */
    private fun v0Message(
        staticKeys: Int,
        writableRefs: Int,
        readonlyRefs: Int,
    ): Solana.RawMessage {
        val lookup =
            Solana.RawMessage.MessageAddressTableLookup.newBuilder()
                .setAccountKey("lookupTable")
                .addAllWritableIndexes((0 until writableRefs).toList())
                .addAllReadonlyIndexes((0 until readonlyRefs).toList())
                .build()
        val v0 =
            Solana.RawMessage.MessageV0.newBuilder()
                .addAllAccountKeys((0 until staticKeys).map { "key$it" })
                .addAddressTableLookups(lookup)
                .build()
        return Solana.RawMessage.newBuilder().setV0(v0).build()
    }

    private fun legacyMessage(staticKeys: Int): Solana.RawMessage {
        val legacy =
            Solana.RawMessage.MessageLegacy.newBuilder()
                .addAllAccountKeys((0 until staticKeys).map { "key$it" })
                .build()
        return Solana.RawMessage.newBuilder().setLegacy(legacy).build()
    }

    @Test
    fun `v0 lock count sums static keys and both lookup index kinds`() {
        // The #5131 repro: 16 static keys + 35 writable-refs + 15 readonly-refs = 66.
        val message = v0Message(staticKeys = 16, writableRefs = 35, readonlyRefs = 15)
        assertEquals(66, SolanaSwap.countAccountLocks(message))
    }

    @Test
    fun `v0 tx above the cap is flagged as exceeding the limit`() {
        val message = v0Message(staticKeys = 16, writableRefs = 35, readonlyRefs = 15)
        assertTrue(SolanaSwap.countAccountLocks(message) > SolanaSwap.MAX_TX_ACCOUNT_LOCKS)
    }

    @Test
    fun `v0 tx at exactly the cap is not flagged`() {
        val message = v0Message(staticKeys = 16, writableRefs = 33, readonlyRefs = 15)
        assertEquals(64, SolanaSwap.countAccountLocks(message))
        assertFalse(SolanaSwap.countAccountLocks(message) > SolanaSwap.MAX_TX_ACCOUNT_LOCKS)
    }

    @Test
    fun `legacy tx locks only its static account keys`() {
        val message = legacyMessage(staticKeys = 20)
        assertEquals(20, SolanaSwap.countAccountLocks(message))
        assertFalse(SolanaSwap.countAccountLocks(message) > SolanaSwap.MAX_TX_ACCOUNT_LOCKS)
    }
}
