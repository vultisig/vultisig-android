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
import org.junit.jupiter.api.Test

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
}
