package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.WalletCoreNative
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Instrumented because the decode goes through wallet-core's `EthereumAbi` JNI, which never loads
 * in a JVM unit test. The off-device half — the results that are rejected before the decoder runs —
 * lives in `DecimalsErc20DecoderTest`.
 */
class DecimalsErc20DecoderAndroidTest {

    @Before
    fun setUp() {
        WalletCoreNative.ensureLoaded()
    }

    @Test
    fun decodesAStandardDecimalsWord() {
        assertEquals(18, EthereumFunction.decimalsErc20Decoder(word(18)))
        assertEquals(6, EthereumFunction.decimalsErc20Decoder(word(6)))
    }

    @Test
    fun narrowsAFullWidthWordToUint8() {
        // BigInteger(hex, 16).toInt() wrapped this to -1, which passed the repository's non-zero
        // check and only died at the caller's 0..MAX_DECIMALS range test.
        assertEquals(255, EthereumFunction.decimalsErc20Decoder("0x" + "ff".repeat(32)))
    }

    @Test
    fun returnsNullForAWordShorterThan32Bytes() {
        assertNull(EthereumFunction.decimalsErc20Decoder("0x00000012"))
    }

    @Test
    fun returnsNullForAnEmptyResult() {
        assertNull(EthereumFunction.decimalsErc20Decoder("0x"))
    }

    private fun word(value: Int): String = "0x" + "%064x".format(value)
}
