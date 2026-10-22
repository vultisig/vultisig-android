package com.vultisig.wallet.data.crypto.ton

import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

/**
 * Expected addresses are the SDK's cross-encoder golden vectors
 * (`testdata/cross-encoder-golden/ton-w5-*.json`), so the account id derived here is pinned to the
 * one every other co-signer derives.
 */
internal class TonWalletVersionTest {

    @Test
    fun `an empty cell hashes to the well-known empty cell hash`() {
        assertEquals(
            "96a296d224f285c67bee93c30f8a309157f0daa35dc5b87e410b78630a09cfc7",
            TonCellBuilder().build().hash.toHexString(),
        )
    }

    @Test
    fun `the W5R1 code cell hashes to the published mainnet code hash`() {
        val code = TonBocParser.parse(W5R1_CODE_BOC)

        assertEquals(
            "20834b7b72b112147e1b2fb457b84e74d1a30f04f737d4f62a668e9552d2b72f",
            code.hash.toHexString(),
        )
        assertEquals(6, code.depth)
    }

    @Test
    fun `a W5R1 StateInit hashes to the golden sender address`() {
        val stateInit = w5StateInit(GOLDEN_PUBLIC_KEY)

        assertEquals(GOLDEN_W5_RAW, tonAccountAddress(stateInit))
        assertEquals(GOLDEN_W5_RAW, tonFriendlyToRaw(GOLDEN_W5_FRIENDLY))
    }

    @Test
    fun `resolves V4R2 when the sender is the key's V4R2 address`() {
        assertEquals(TonWalletVersion.V4R2, TonWalletVersion.resolve(V4R2_FRIENDLY, ::addressOf))
    }

    @Test
    fun `resolves V5R1 when the sender is the key's W5 address in any form`() {
        listOf(GOLDEN_W5_FRIENDLY, GOLDEN_W5_BOUNCEABLE, GOLDEN_W5_RAW).forEach { sender ->
            assertEquals(TonWalletVersion.V5R1, TonWalletVersion.resolve(sender, ::addressOf))
        }
    }

    @Test
    fun `refuses a sender that is neither of the key's wallets`() {
        assertFailsWith<IllegalStateException> {
            TonWalletVersion.resolve(
                "0:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                ::addressOf,
            )
        }
    }

    @Test
    fun `refuses a malformed sender address`() {
        assertFailsWith<IllegalArgumentException> {
            TonWalletVersion.resolve("not-an-address", ::addressOf)
        }
    }

    @Test
    fun `W5 allows 255 messages per request and V4R2 four`() {
        assertEquals(4, TonWalletVersion.V4R2.maxMessagesPerRequest)
        assertEquals(255, TonWalletVersion.V5R1.maxMessagesPerRequest)
    }

    private fun addressOf(version: TonWalletVersion): String =
        when (version) {
            TonWalletVersion.V4R2 -> V4R2_FRIENDLY
            TonWalletVersion.V5R1 -> tonAccountAddress(w5StateInit(GOLDEN_PUBLIC_KEY))
        }

    /** `is_signature_allowed(1)=1 ‖ seqno(32)=0 ‖ wallet_id(32) ‖ publicKey(256) ‖ bit(0)`. */
    private fun w5StateInit(publicKeyHex: String): TonCell {
        val data =
            TonCellBuilder()
                .storeBit(true)
                .storeUInt(0, 32)
                .storeUInt(TonWalletVersion.V5R1_WALLET_ID.toLong(), 32)
                .storeUIntBig(BigInteger(publicKeyHex, 16), 256)
                .storeBit(false)
                .build()
        // split_depth, special: none; code, data: present; library: empty.
        return TonCellBuilder()
            .storeBit(false)
            .storeBit(false)
            .storeMaybeRef(TonBocParser.parse(W5R1_CODE_BOC))
            .storeMaybeRef(data)
            .storeBit(false)
            .build()
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val GOLDEN_PUBLIC_KEY =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val GOLDEN_W5_FRIENDLY = "UQCvaZohosTA0ak9ZFMs-cvL1JrXqogqJH8sI2uO6k8clJpn"
        const val GOLDEN_W5_BOUNCEABLE = "EQCvaZohosTA0ak9ZFMs-cvL1JrXqogqJH8sI2uO6k8clMei"
        const val GOLDEN_W5_RAW =
            "0:af699a21a2c4c0d1a93d64532cf9cbcbd49ad7aa882a247f2c236b8eea4f1c94"

        /** WalletCore's V4R2 derivation for [GOLDEN_PUBLIC_KEY]. */
        const val V4R2_FRIENDLY = "UQCf6aQfV3vc8KLtPI_lROY64hUeR1oyNfdbwXB-gwDaKZmi"

        /** WalletCore's `wallet_v5r1.code`, byte-identical to SDK `walletV5R1CodeBase64`. */
        const val W5R1_CODE_BOC =
            "te6cckECFAEAAoEAART/APSkE/S88sgLAQIBIAINAgFIAwQC3NAg10nBIJFbj2Mg1wsfIIIQZXh0br0hghBzaW50vbCSXwPgghBleHRuuo60gCDXIQHQdNch+kAw+kT4KPpEMFi9kVvg7UTQgQFB1yH0BYMH9A5voTGRMOGAQNchcH/bPOAxINdJgQKAuZEw4HDiEA8CASAFDAIBIAYJAgFuBwgAGa3OdqJoQCDrkOuF/8AAGa8d9qJoQBDrkOuFj8ACAUgKCwAXsyX7UTQcdch1wsfgABGyYvtRNDXCgCAAGb5fD2omhAgKDrkPoCwBAvIOAR4g1wsfghBzaWduuvLgin8PAeaO8O2i7fshgwjXIgKDCNcjIIAg1yHTH9Mf0x/tRNDSANMfINMf0//XCgAK+QFAzPkQmiiUXwrbMeHywIffArNQB7Dy0IRRJbry4IVQNrry4Ib4I7vy0IgikvgA3gGkf8jKAMsfAc8Wye1UIJL4D95w2zzYEAP27aLt+wL0BCFukmwhjkwCIdc5MHCUIccAs44tAdcoIHYeQ2wg10nACPLgkyDXSsAC8uCTINcdBscSwgBSMLDy0InXTNc5MAGk6GwShAe78uCT10rAAPLgk+1V4tIAAcAAkVvg69csCBQgkXCWAdcsCBwS4lIQseMPINdKERITAJYB+kAB+kT4KPpEMFi68uCR7UTQgQFB1xj0BQSdf8jKAEAEgwf0U/Lgi44UA4MH9Fvy4Iwi1woAIW4Bs7Dy0JDiyFADzxYS9ADJ7VQAcjDXLAgkji0h8uCS0gDtRNDSAFETuvLQj1RQMJExnAGBAUDXIdcKAPLgjuLIygBYzxbJ7VST8sCN4gAQk1vbMeHXTNC01sNe"
    }
}
