package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.data.common.toHexByteArray
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.SubstrateSignerPayload
import com.vultisig.wallet.data.tss.getSignature
import com.vultisig.wallet.data.utils.Numeric
import java.io.ByteArrayOutputStream
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType

/**
 * Signs a dApp's Substrate `SignerPayloadJSON` the way the extension does
 * (`constructSigningPayload.ts`): the extrinsic payload v4 is assembled from the fields the dApp
 * supplied and signed as-is, bypassing WalletCore, which only knows how to build a transfer. The
 * signature is the whole deliverable — the dApp assembles and submits the extrinsic — so nothing is
 * compiled or broadcast on this side.
 */
object SubstrateDappSigner {

    /**
     * Past this many bytes the runtime signs `blake2b_256(payload)` instead of the payload itself
     * (`sp_runtime::generic::SignedPayload`).
     */
    private const val HASH_THRESHOLD_BYTES = 256

    /**
     * `method ‖ era ‖ compact(nonce) ‖ compact(tip) ‖ u32le(specVersion) ‖
     * u32le(transactionVersion) ‖ genesisHash ‖ blockHash`, blake2b-256'd when longer than 256
     * bytes. Every co-signer and the extension must produce the same bytes here or the ceremony
     * signs different messages.
     */
    fun signingBytes(payload: SubstrateSignerPayload): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(payload.methodBytes())
        out.write(payload.eraBytes())
        out.write(SubstrateScale.compact(payload.nonceValue().toBigInteger()))
        out.write(SubstrateScale.compact(payload.tipValue()))
        out.write(SubstrateScale.u32LE(payload.specVersionValue()))
        out.write(SubstrateScale.u32LE(payload.transactionVersionValue()))
        out.write(payload.genesisHashBytes())
        out.write(payload.blockHashBytes())
        val raw = out.toByteArray()
        return if (raw.size > HASH_THRESHOLD_BYTES) Utils.blake2bHash(raw) else raw
    }

    fun getPreSignedImageHash(payload: SubstrateSignerPayload): List<String> =
        listOf(Numeric.toHexStringNoPrefix(signingBytes(payload)))

    /**
     * The raw ed25519 signature over [signingBytes], verified against the vault's key. There is no
     * transaction hash: the extrinsic does not exist until the dApp wraps this signature around its
     * own call, so [SignedTransactionResult.transactionHash] is empty and nothing gets broadcast.
     */
    fun getSignedTransaction(
        vaultHexPublicKey: String,
        payload: SubstrateSignerPayload,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        val message = signingBytes(payload)
        val key = Numeric.toHexStringNoPrefix(message)
        val signature = signatures[key]?.getSignature() ?: error("Signature not found")
        val publicKey = PublicKey(vaultHexPublicKey.toHexByteArray(), PublicKeyType.ED25519)
        check(publicKey.verify(signature, message)) { "Signature verification failed" }
        val signatureHex = Numeric.toHexStringNoPrefix(signature)
        return SignedTransactionResult(
            rawTransaction = signatureHex,
            transactionHash = "",
            signature = signatureHex,
        )
    }
}
