package com.vultisig.wallet.data.crypto

import com.vultisig.wallet.data.WalletCoreNative
import com.vultisig.wallet.data.models.CosmoSignature
import com.vultisig.wallet.data.models.transactionHash
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import wallet.core.jni.CoinType
import wallet.core.jni.TransactionUtil

/**
 * Pins which of our locally computed transaction ids WalletCore's `TransactionUtil.calcTxHash`
 * could and could not replace.
 *
 * The JNI binding declares a bare `String`, but the native returns null for every coin without a
 * `TransactionUtil` in WalletCore's Rust registry — and for a supported coin whose input it cannot
 * parse. Read it into a declared `String?` and null-check; letting it flow as a platform type turns
 * the null into an NPE at the first non-null use.
 *
 * If a WalletCore bump makes one of the null cases start returning a hash, that coin's hand-rolled
 * hash in `CardanoUtils` / `CosmoSignature.transactionHash` becomes a candidate for removal.
 */
class WalletCoreCalcTxHashTest {

    @Before
    fun loadWalletCore() {
        WalletCoreNative.ensureLoaded()
    }

    @Test
    fun cardano_is_not_in_the_registry() {
        val hash: String? = TransactionUtil.calcTxHash(CoinType.CARDANO, signedCardanoEnvelopeHex)
        assertNull(hash)
    }

    @Test
    fun thorchain_is_not_in_the_registry() {
        val hash: String? = TransactionUtil.calcTxHash(CoinType.THORCHAIN, cosmosTxBytesBase64)
        assertNull(hash)
    }

    @Test
    fun cosmos_matches_the_local_sha256_over_tx_bytes() {
        val local = CosmoSignature(mode = "BROADCAST_MODE_SYNC", txBytes = cosmosTxBytesBase64)
        val hash: String? = TransactionUtil.calcTxHash(CoinType.COSMOS, cosmosTxBytesBase64)
        assertEquals(local.transactionHash().uppercase(), hash)
    }

    // Real signed 3-element envelope [body, witness_set, auxiliary_data] from a native ADA send.
    private val signedCardanoEnvelopeHex =
        "83a4008182582052e86a3e604ef6600f45d1cdb434eacbbaeffc763b02dd6d83bdbd8c825a01d2" +
            "01018282581d61df45a39eb0282d2f6d0c1e46ea30452ba18eb86723739cdfbede9cbb1a001e8480" +
            "82581d6150574c50e2c665f998457296a5d7ea1d789949d5e4e25adeee1a18251a026723d2021a00" +
            "028900031a0b736d01a1008182582075be85178816db3bc71a4f3e64e5c89866d8b7daae827ba9cf" +
            "4ecd1ed9e645d55840f802b0a258b5af9c78dd5cc062bfb8d7984a432af1808a0dd7282051613ed1" +
            "fc62d7e1953443177354137b29d6bf34bd6cf10c7eee442d1a448e3b497f12b00bf6"

    // Any bytes do: the Cosmos util is sha256 over the base64-decoded input, and the unsupported
    // check runs before the input is looked at.
    private val cosmosTxBytesBase64: String =
        Base64.getEncoder().encodeToString(ByteArray(64) { it.toByte() })
}
