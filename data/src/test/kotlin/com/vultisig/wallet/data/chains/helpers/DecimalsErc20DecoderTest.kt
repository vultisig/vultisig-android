package com.vultisig.wallet.data.chains.helpers

import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * The `decimals()` `eth_call` results that carry no decodable word at all. A contract that does not
 * implement `decimals()` — or an address that is not a token — answers `0x`, which used to reach
 * `BigInteger("", 16)` and throw `NumberFormatException` out of the repository.
 *
 * These resolve before the ABI decoder, so they run on the JVM. The decoding itself needs
 * wallet-core's native library, which only loads on a device — that half is covered by
 * `DecimalsErc20DecoderAndroidTest`.
 */
internal class DecimalsErc20DecoderTest {

    @Test
    fun `returns null for an empty eth_call result`() {
        assertNull(EthereumFunction.decimalsErc20Decoder("0x"))
        assertNull(EthereumFunction.decimalsErc20Decoder(""))
    }

    @Test
    fun `returns null for an odd-length result`() {
        assertNull(EthereumFunction.decimalsErc20Decoder("0x123"))
    }

    @Test
    fun `returns null for a non-hex result`() {
        assertNull(EthereumFunction.decimalsErc20Decoder("0xzzzz"))
    }
}
