package com.vultisig.wallet.data.crypto.ton

import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage parity with the iOS `TonOperationExtractorTests` and the SDK's
 * `messageBody/decode.test.ts`. The base64 BOC fixtures were emitted from the SDK's `@ton/core`
 * builders, so this decoder is validated against bit-for-bit-identical inputs.
 *
 * Addresses are asserted in raw `workchain:hex` form (this decoder is JNI-free); the raw values
 * were derived from the fixtures' user-friendly addresses.
 */
internal class TonMessageBodyDecoderTest {

    // Raw forms of the fixture addresses (user-friendly -> workchain:hex).
    private val recipient = "0:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    private val response = "0:2f0df5851b4a185f5f63c0d0cd0412f5aca353f577da18ff47c936f99dbd849a"
    private val stonfiV2Router =
        "0:222d5ebbec1807357114b832770b0f0ae563a22523bceb187c610ab62ed84912"

    // BOC fixtures (base64, from @ton/core builders).
    private val jettonTransfer =
        "te6cckEBAQEAWQAArg+KfqUAAAAAAAAwOUBfXhAIAf//////////////////////////////////////////AAvDfWFG0oYX19jwNDNBBL1rKNT9XfaGP9HyTb5nb2Emhh6EgOvlFRU="
    private val jettonTransferNoResponse =
        "te6cckEBAQEAMgAAXw+KfqUAAAAAAAAAARKoAf/////////////////////////////////////////+ATfRtbw="
    private val jettonTransferToStonfiRouterWithSwap =
        "te6cckEBAwEA+gABrg+KfqUAAAAAAAAwOUBfXhAIAERavXfYMA5q4ilwZO4WHhXKx0RKR3nWMPjCFWxdsJIlAAvDfWFG0oYX19jwNDNBBL1rKNT9XfaGP9HyTb5nb2Emhh6EgQEB4WZk3iqAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABQALw31hRtKGF9fY8DQzQQS9ayjU/V32hj/R8k2+Z29hJqAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABgAAAAAAAAD3AAgBTREaPhQgB//////////////////////////////////////////4AAAUQRrdCMQ=="
    private val nftTransfer =
        "te6cckEBAQEAVAAAo1/MPRQAAAAAAAAAY4Af//////////////////////////////////////////AAvDfWFG0oYX19jwNDNBBL1rKNT9XfaGP9HyTb5nb2EmhYagiVJsdh"
    private val excessesBody = "te6cckEBAQEADgAAGNUydtsAAAAAAAAABxylUgg="
    private val unknownOpcode = "te6cckEBAQEADgAAGN6tvu8AAAAAAAAAAGer470="
    private val jettonTransferTruncated = "te6cckEBAQEADgAAGA+KfqUAAAAAAAAAAdhtSVg="
    private val jettonTransferEitherCellNoRef =
        "te6cckEBAQEAUwAAog+KfqUAAAAAAAAAARKoAf//////////////////////////////////////////AAvDfWFG0oYX19jwNDNBBL1rKNT9XfaGP9HyTb5nb2EmgZqiypM="
    private val jettonTransferTruncatedMidEitherCell =
        "te6cckEBAQEAUwAAoQ+KfqUAAAAAAAAAARKoAf//////////////////////////////////////////AAvDfWFG0oYX19jwNDNBBL1rKNT9XfaGP9HyTb5nb2EmgbZISzw="
    private val nftTransferTruncated =
        "te6cckEBAQEAUgAAn1/MPRQAAAAAAAAAAYAf//////////////////////////////////////////AAvDfWFG0oYX19jwNDNBBL1rKNT9XfaGP9HyTb5nb2EmgQzDtOhw=="
    private val jettonTransferTextCommentPrefix =
        "te6cckEBAQEAXQAAtgAAAAAPin6lAAAAAAAAMDlAX14QCAH//////////////////////////////////////////wALw31hRtKGF9fY8DQzQQS9ayjU/V32hj/R8k2+Z29hJoYehIDi8JQZ"

    // Same body as [jettonTransfer], hex-encoded with the BOC magic prefix.
    private val jettonTransferHex =
        "b5ee9c724101010100590000ae0f8a7ea50000000000003039405f5e100801ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff000bc37d6146d28617d7d8f034334104bd6b28d4fd5df6863fd1f24dbe676f6126861e8480ebe51515"

    @Test
    fun `decode returns null for null and empty input`() {
        assertNull(TonMessageBodyDecoder.decode(null))
        assertNull(TonMessageBodyDecoder.decode(""))
        assertNull(TonMessageBodyDecoder.decode("   "))
    }

    @Test
    fun `decode returns null for non-BOC garbage`() {
        assertNull(TonMessageBodyDecoder.decode("not-a-boc"))
    }

    @Test
    fun `decode parses a jetton transfer body`() {
        val intent = TonMessageBodyDecoder.decode(jettonTransfer)
        assertTrue(intent is TonMessageBodyIntent.JettonTransfer)
        assertEquals(BigInteger("12345"), intent.queryId)
        assertEquals(BigInteger("100000000"), intent.amount)
        assertEquals(recipient, intent.destination)
        assertEquals(response, intent.responseDestination)
        assertEquals(BigInteger("1000000"), intent.forwardTonAmount)
    }

    @Test
    fun `decode parses a jetton transfer without response destination`() {
        val intent = TonMessageBodyDecoder.decode(jettonTransferNoResponse)
        assertTrue(intent is TonMessageBodyIntent.JettonTransfer)
        assertNull(intent.responseDestination)
        assertEquals(BigInteger.ZERO, intent.forwardTonAmount)
    }

    @Test
    fun `decode classifies a STON-fi v2 jetton swap`() {
        // A jetton transfer whose forward payload is a STON.fi v2 swap (0x6664de2a) is now surfaced
        // as a Swap, carrying the jetton-transfer destination (the router) as inputRouterAddress
        // for
        // the runtime allow-list gate. The offer amount is the jetton transfer amount.
        val intent = TonMessageBodyDecoder.decode(jettonTransferToStonfiRouterWithSwap)
        assertTrue(intent is TonMessageBodyIntent.Swap)
        assertEquals(TonMessageBodyIntent.Provider.STONFI, intent.provider)
        assertEquals(TonMessageBodyIntent.OfferAsset.JETTON, intent.offerAsset)
        assertEquals(stonfiV2Router, intent.inputRouterAddress)
        assertEquals(BigInteger("100000000"), intent.offerAmount)
        assertNotNull(intent.minOut)
    }

    @Test
    fun `decode parses an NFT transfer body`() {
        val intent = TonMessageBodyDecoder.decode(nftTransfer)
        assertTrue(intent is TonMessageBodyIntent.NftTransfer)
        assertEquals(BigInteger("99"), intent.queryId)
        assertEquals(recipient, intent.newOwner)
        assertEquals(response, intent.responseDestination)
        assertEquals(BigInteger("50000"), intent.forwardAmount)
    }

    @Test
    fun `decode parses an excesses body`() {
        val intent = TonMessageBodyDecoder.decode(excessesBody)
        assertTrue(intent is TonMessageBodyIntent.Excesses)
        assertEquals(BigInteger("7"), intent.queryId)
    }

    @Test
    fun `decode peels a 0x00000000 text-comment prefix before the opcode`() {
        val intent = TonMessageBodyDecoder.decode(jettonTransferTextCommentPrefix)
        assertTrue(intent is TonMessageBodyIntent.JettonTransfer)
        assertEquals(BigInteger("100000000"), intent.amount)
        assertEquals(recipient, intent.destination)
    }

    @Test
    fun `decode accepts a hex-encoded BOC payload`() {
        val intent = TonMessageBodyDecoder.decode(jettonTransferHex)
        assertTrue(intent is TonMessageBodyIntent.JettonTransfer)
        assertEquals(BigInteger("100000000"), intent.amount)
        assertEquals(recipient, intent.destination)
    }

    @Test
    fun `decode returns null for an unknown opcode`() {
        assertNull(TonMessageBodyDecoder.decode(unknownOpcode))
    }

    @Test
    fun `decode returns null when the jetton transfer body is truncated`() {
        assertNull(TonMessageBodyDecoder.decode(jettonTransferTruncated))
    }

    @Test
    fun `decode returns null when the forward payload claims a ref it does not have`() {
        assertNull(TonMessageBodyDecoder.decode(jettonTransferEitherCellNoRef))
    }

    @Test
    fun `decode returns null when truncated mid forward-payload discriminator`() {
        assertNull(TonMessageBodyDecoder.decode(jettonTransferTruncatedMidEitherCell))
    }

    @Test
    fun `decode returns null when the NFT transfer body is truncated`() {
        assertNull(TonMessageBodyDecoder.decode(nftTransferTruncated))
    }
}
