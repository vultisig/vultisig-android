package com.vultisig.wallet.data.utils

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.coinType
import org.junit.Assert.assertEquals
import org.junit.Test
import wallet.core.jni.CoinType

/**
 * Pins the SEI EVM coin-type contract.
 *
 * SEI is EVM-compatible and reuses the Ethereum coin type (secp256k1, derivation path
 * `m/44'/60'/0'/0/0`, 0x address format), exactly like Hyperliquid — matching iOS and desktop. Its
 * real EVM chainId (1329) is applied through [compatibleChainId] rather than WalletCore's default.
 * This guards against SEI regressing to WalletCore's native SEI coin type (Cosmos path
 * `m/44'/118'/…`, chainId defaulting to Ethereum's `1`), which would derive a different key and
 * break cross-platform co-signing.
 */
class SeiEvmCoinTypeTest {

    @Test
    fun sei_mapsToEthereumCoinType() {
        assertEquals(CoinType.ETHEREUM, Chain.Sei.coinType)
    }

    @Test
    fun sei_usesEthereumDerivationPath() {
        assertEquals("m/44'/60'/0'/0/0", Chain.Sei.coinType.derivationPath())
    }

    @Test
    fun sei_usesSeiEvmChainId() {
        // 1329 is applied via the override, not WalletCore's Ethereum default of "1".
        assertEquals("1329", Chain.Sei.coinType.compatibleChainId(Chain.Sei))
    }

    @Test
    fun ethereumAndHyperliquid_chainIdsUnaffected() {
        // Regression guard for the shared ETHEREUM branch in compatibleChainId.
        assertEquals("1", Chain.Ethereum.coinType.compatibleChainId(Chain.Ethereum))
        assertEquals("999", Chain.Hyperliquid.coinType.compatibleChainId(Chain.Hyperliquid))
    }
}
