package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.WalletCoreNative
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Instrumented because the decode goes through wallet-core's `EthereumAbi` JNI, which never loads
 * in a JVM unit test. The off-device half — the results that are rejected before the decoder runs —
 * lives in `SymbolErc20DecoderTest`.
 */
class SymbolErc20DecoderAndroidTest {

    @Before
    fun setUp() {
        WalletCoreNative.ensureLoaded()
    }

    @Test
    fun decodesAStandardAbiString() {
        assertEquals("USDC", EthereumFunction.symbolErc20Decoder(abiString("USDC")))
    }

    @Test
    fun honoursAHeadOffsetOtherThan32() {
        // The ABI spec only promises that word[0] points at the length word; the old hand-rolled
        // slice assumed it was always 32 and read the wrong words for anything else.
        val shifted =
            "0x" +
                word(0x40) +
                word(0xdeadbeef) + // an extra head word the offset skips over
                word(4) +
                "55534443".padEnd(64, '0')
        assertEquals("USDC", EthereumFunction.symbolErc20Decoder(shifted))
    }

    @Test
    fun doesNotMangleAShortTickerWhoseLettersHappenToBeHexDigits() {
        // "FACE" is valid hex, but as a 4-char string it is not a padded bytes32, so it must
        // survive untouched (guard: only 64-char hex values are treated as bytes32).
        assertEquals("FACE", EthereumFunction.symbolErc20Decoder(abiString("FACE")))
    }

    @Test
    fun decodesABridgedBytes32AsHexStringBackToText() {
        // Real eth_call response captured from MKR on Arbitrum (0x2e9a…2879). MKR declares
        // symbol() as bytes32; the bridged deployment returns it as an ABI string whose content is
        // the 64-char hex of the bytes32, right-padded with zeros (issue #4873).
        val mkrOnArbitrum =
            "0x0000000000000000000000000000000000000000000000000000000000000020" +
                "0000000000000000000000000000000000000000000000000000000000000040" +
                "3464346235323030303030303030303030303030303030303030303030303030" +
                "3030303030303030303030303030303030303030303030303030303030303030"
        assertEquals("MKR", EthereumFunction.symbolErc20Decoder(mkrOnArbitrum))
    }

    @Test
    fun decodesARawBytes32WordFromALegacyDsToken() {
        // Mainnet MKR (0x9f8f…79a2) and SAI answer symbol() with a single bytes32 word. The string
        // decoder rejects it, and the old slice ran off the end of the 32 bytes and returned null.
        val mkrOnMainnet = "0x" + "4d4b52".padEnd(64, '0')
        assertEquals("MKR", EthereumFunction.symbolErc20Decoder(mkrOnMainnet))
    }

    @Test
    fun returnsNullForAWordThatIsNeitherAStringHeadNorBytes32() {
        assertNull(EthereumFunction.symbolErc20Decoder("0xdeadbeef"))
    }

    @Test
    fun returnsNullForABytes32WordWithNoPrintableText() {
        assertNull(EthereumFunction.symbolErc20Decoder("0x" + word(0)))
        assertNull(EthereumFunction.symbolErc20Decoder("0x" + "ff".repeat(32)))
    }

    @Test
    fun returnsNullForABlankString() {
        assertNull(EthereumFunction.symbolErc20Decoder(abiString("")))
        assertNull(EthereumFunction.symbolErc20Decoder(abiString("   ")))
    }

    @Test
    fun returnsNullForAnEmptyResult() {
        assertNull(EthereumFunction.symbolErc20Decoder("0x"))
    }

    /** Standard ABI `string` return: offset word, length word, then the bytes right-padded. */
    private fun abiString(value: String): String {
        val bytes = value.toByteArray(Charsets.US_ASCII)
        val data = bytes.joinToString("") { "%02x".format(it) }
        val padded = data.padEnd(((data.length + 63) / 64) * 64, '0')
        return "0x" + word(0x20) + word(bytes.size.toLong()) + padded
    }

    private fun word(value: Long): String = "%064x".format(value)
}
