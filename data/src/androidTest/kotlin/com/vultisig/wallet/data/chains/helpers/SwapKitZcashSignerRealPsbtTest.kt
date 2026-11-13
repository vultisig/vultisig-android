package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.WalletCoreNative
import com.vultisig.wallet.data.utils.Numeric
import java.math.BigInteger
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import tss.KeysignResponse
import wallet.core.jni.PrivateKey

/** Real SwapKit v5 ZEC PSBT through [SwapKitZcashSigner] with real WalletCore (JNI). */
class SwapKitZcashSignerRealPsbtTest {

    @Test
    fun realSwapKitV5PsbtSignsToIndependentZip244SighashAndTxid() {
        WalletCoreNative.ensureLoaded()
            // Live SwapKit ZEC.ZEC -> ETH.ETH (Maya route: deposit + OP_RETURN memo + change). The
            // input/change script is rebound to a known test key; expected values come from an
            // independent Python ZIP-244 implementation over the rebound bytes.
            val owner = Numeric.hexStringToByteArray("76a9147b187e37a86ebb966feb9e83b2fd489e1601b71788ac")
            val bound = Numeric.hexStringToByteArray("76a914${REAL_KEY_HASH}88ac")
            val psbt = replaceAll(Base64.getDecoder().decode(REAL_SWAPKIT_PSBT), owner, bound)
            val key = PrivateKey(Numeric.hexStringToByteArray(REAL_PRIV_KEY))
            val realSigner = SwapKitZcashSigner(REAL_PUB_KEY, "")

            val hashes = realSigner.getPreSignedImageHash(psbt, "", BigInteger.valueOf(1_000_000), REAL_BRANCH_ID)
            assertEquals(listOf(REAL_SIGHASH), hashes)

            val signatures =
                hashes.associateWith {
                    KeysignResponse().apply {
                        derSignature =
                            Numeric.toHexStringNoPrefix(
                                key.signAsDER(Numeric.hexStringToByteArray(it))
                            )
                    }
                }
            val signed =
                realSigner.getSignedTransaction(psbt, "", BigInteger.valueOf(1_000_000), signatures, REAL_BRANCH_ID)
            assertEquals(REAL_TXID, signed.transactionHash)
            assertTrue(signed.rawTransaction.startsWith("050000800a27a726"))
    }

    private fun replaceAll(data: ByteArray, from: ByteArray, to: ByteArray): ByteArray {
        val out = data.copyOf()
        for (i in 0..out.size - from.size) {
            if ((from.indices).all { out[i + it] == from[it] }) to.copyInto(out, i)
        }
        return out
    }

    private companion object {
        private const val REAL_PRIV_KEY =
            "1111111111111111111111111111111111111111111111111111111111111111"
        private const val REAL_PUB_KEY =
            "034f355bdcb7cc0af728ef3cceb9615d90684bb5b2ca5f859ab0f0b704075871aa"
        private const val REAL_KEY_HASH = "fc7250a211deddc70ee5a2738de5f07817351cef"
        private const val REAL_BRANCH_ID = "d90a1977"
        private const val REAL_SIGHASH =
            "338cdbe13d0a3baf7bcef01bab4d330c39c97a456fa03fda703d975a9f5c4763"
        private const val REAL_TXID =
            "2a39ede726863bcd3186a14b6740732340364761c485d8ef6d72468f6b35a812"
        private const val REAL_SWAPKIT_PSBT =
            "cHNidP8BANQFAACACienJtkKGXcAAAAAAAAAAAHBhxHO/gmDCTDgLQc45ijjsKPdkeVnXemTRy2yVsqSpQEAAAAA/////wOghgEAAAAAABl2qRR7FnCoiPxqhDeRe7/zt/nGQJHvzoisAAAAAAAAAABFakM9OmU6MHhkOGRBNkJGMjY5NjRhRjlEN2VFZDllMDNFNTM0MTVEMzdhQTk2MDQ1OjQyNjM2LzMvMDpfL3ZpOjE1LzUwUP8CAAAAAAAZdqkUexh+N6huu5Zv656Dsv1InhYBtxeIrAAAAAABASKY5wQAAAAAABl2qRR7GH43qG67lm/rnoOy/UieFgG3F4isAAAAAA=="
    }
}
