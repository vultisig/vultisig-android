package com.vultisig.wallet.data.chains.helpers

import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * The `symbol()` `eth_call` results that are rejected before any ABI word is read. A contract that
 * does not implement `symbol()` — or an address that is not a contract — answers `0x`.
 *
 * These resolve before the ABI decoder, so they run on the JVM. The decoding itself needs
 * wallet-core's native library, which only loads on a device — that half, including the `bytes32`
 * fallback and the bridged hex-in-a-string case, is covered by `SymbolErc20DecoderAndroidTest`.
 */
internal class SymbolErc20DecoderTest {

    @Test
    fun `returns null for an empty eth_call result`() {
        assertNull(EthereumFunction.symbolErc20Decoder("0x"))
        assertNull(EthereumFunction.symbolErc20Decoder(""))
    }

    @Test
    fun `returns null for an odd-length result`() {
        assertNull(EthereumFunction.symbolErc20Decoder("0x123"))
    }

    @Test
    fun `returns null for a non-hex result`() {
        assertNull(EthereumFunction.symbolErc20Decoder("0xzzzz"))
    }
}
