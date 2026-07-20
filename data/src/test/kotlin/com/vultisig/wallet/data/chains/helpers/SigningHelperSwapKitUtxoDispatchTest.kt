package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression coverage for the join-side SwapKit UTXO signer dispatch: the signer must be picked
 * from the payload's own `chain`, not from which `txType` literal happened to match. A peer that
 * doesn't share Android/iOS's invented per-chain PSBT_DOGE/PSBT_BCH/PSBT_DASH/PSBT_ZEC convention
 * sends the documented generic "PSBT" (sometimes blank) for every UTXO source chain.
 *
 * Every case below feeds an empty PSBT payload: each per-chain signer validates payload emptiness
 * in pure JVM before touching the WalletCore JNI, so asserting the exact exception type thrown is
 * enough to prove which signer was actually dispatched to, without needing the native library.
 */
class SigningHelperSwapKitUtxoDispatchTest {

    private val vault = Vault(id = "v1", name = "Test Vault")

    private fun swapKitKeysignPayload(chain: Chain, txType: String) =
        KeysignPayload(
            coin = Coin.EMPTY.copy(chain = chain),
            toAddress = "",
            toAmount = BigInteger.ZERO,
            blockChainSpecific =
                BlockChainSpecific.UTXO(byteFee = BigInteger.ONE, sendMaxAmount = false),
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "",
            libType = SigningLibType.GG20,
            wasmExecuteContractPayload = null,
            swapPayload =
                SwapPayload.SwapKit(
                    SwapKitSwapPayloadJson(
                        fromCoin = Coin.EMPTY.copy(chain = chain),
                        toCoin = Coin.EMPTY.copy(chain = Chain.Ethereum),
                        fromAmount = BigInteger.ONE,
                        toAmountDecimal = BigDecimal.ONE,
                        txType = txType,
                        txPayload = ByteArray(0),
                        targetAddress = "addr",
                    )
                ),
        )

    @Test
    fun `Zcash source with the documented generic PSBT txType dispatches to the Zcash signer`() {
        val e =
            assertThrows(SwapKitZcashSignerException::class.java) {
                SigningHelper.getKeysignMessages(
                    swapKitKeysignPayload(Chain.Zcash, SwapKitSwapPayloadJson.TX_TYPE_PSBT),
                    vault,
                )
            }
        assertTrue(e.message!!.contains("empty"))
    }

    @Test
    fun `Zcash source with a blank txType still dispatches to the Zcash signer`() {
        // Mirrors a peer's SDK omitting `meta.txType` for the non-Bitcoin PSBT variants.
        assertThrows(SwapKitZcashSignerException::class.java) {
            SigningHelper.getKeysignMessages(swapKitKeysignPayload(Chain.Zcash, ""), vault)
        }
    }

    @Test
    fun `BitcoinCash source with the generic PSBT txType dispatches to the legacy P2PKH signer`() {
        assertThrows(SwapKitLegacyP2PKHSignerException::class.java) {
            SigningHelper.getKeysignMessages(
                swapKitKeysignPayload(Chain.BitcoinCash, SwapKitSwapPayloadJson.TX_TYPE_PSBT),
                vault,
            )
        }
    }

    @Test
    fun `Dogecoin source with a blank txType dispatches to the legacy P2PKH signer`() {
        assertThrows(SwapKitLegacyP2PKHSignerException::class.java) {
            SigningHelper.getKeysignMessages(swapKitKeysignPayload(Chain.Dogecoin, ""), vault)
        }
    }

    @Test
    fun `Dash source with the legacy PSBT_DASH txType still dispatches to the legacy P2PKH signer`() {
        // Backward compatibility: Android/iOS-initiated swaps still stamp this literal.
        assertThrows(SwapKitLegacyP2PKHSignerException::class.java) {
            SigningHelper.getKeysignMessages(
                swapKitKeysignPayload(Chain.Dash, SwapKitSwapPayloadJson.TX_TYPE_PSBT_DASH),
                vault,
            )
        }
    }

    @Test
    fun `getSignedTransaction dispatches a blank txType Zcash source to the Zcash signer too`() {
        assertThrows(SwapKitZcashSignerException::class.java) {
            SigningHelper.getSignedTransaction(
                keysignPayload = swapKitKeysignPayload(Chain.Zcash, ""),
                vault = vault,
                signatures = emptyMap(),
                nonceAcc = BigInteger.ZERO,
            )
        }
    }

    @Test
    fun `an unrecognized non-blank txType on a non-UTXO chain still fails loudly`() {
        val e =
            assertThrows(IllegalStateException::class.java) {
                SigningHelper.getKeysignMessages(
                    swapKitKeysignPayload(Chain.Ethereum, "SOMETHING_ELSE"),
                    vault,
                )
            }
        assertTrue(e.message!!.contains("Unsupported SwapKit txType for signing"))
    }

    @Test
    fun `a PSBT-family txType on a non-UTXO chain still fails loudly rather than misdispatching`() {
        val e =
            assertThrows(IllegalStateException::class.java) {
                SigningHelper.getKeysignMessages(
                    swapKitKeysignPayload(Chain.Ethereum, SwapKitSwapPayloadJson.TX_TYPE_PSBT),
                    vault,
                )
            }
        assertTrue(e.message!!.contains("Unsupported SwapKit txType for signing"))
    }
}
