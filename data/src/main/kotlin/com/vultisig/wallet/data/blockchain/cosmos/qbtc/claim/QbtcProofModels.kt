package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Left-zero-pads a signature component hex string to [byteLength] bytes. A value that is already at
 * or beyond the target width is returned unchanged — never truncated, since truncating a real
 * signature would corrupt it. Mirrors iOS `ClaimProofRequest.padSigHex` and vultisig-windows.
 */
internal fun padSigHex(hex: String, byteLength: Int): String {
    val target = byteLength * 2
    return if (hex.length < target) "0".repeat(target - hex.length) + hex else hex
}

@Serializable data class ClaimProofUtxoRef(val txid: String, val vout: Int)

/**
 * Request body for `POST /prove`. `broadcast = true` asks the proof service to sign
 * `MsgClaimWithProof` with its own MLDSA-44 key and broadcast it (the post-qbtc#158 flow),
 * returning `tx_hash`. Field order matches the wire contract.
 */
@Serializable
data class ClaimProofRequest(
    @SerialName("signature_r") val signatureR: String,
    @SerialName("signature_s") val signatureS: String,
    @SerialName("public_key") val publicKey: String,
    val utxos: List<ClaimProofUtxoRef>,
    @SerialName("claimer_address") val claimerAddress: String,
    @SerialName("chain_id") val chainId: String,
    // No serialization default: the wire must always carry `broadcast` so the
    // proof service knows to self-sign and broadcast the claim (post-qbtc#158).
    val broadcast: Boolean,
) {
    companion object {
        /** Builds a request, zero-padding `r`/`s` to the proof circuit's witness widths. */
        fun create(
            rHex: String,
            sHex: String,
            compressedPubkeyHex: String,
            utxos: List<ClaimableUtxo>,
            claimerAddress: String,
            chainId: String,
            broadcast: Boolean = true,
        ): ClaimProofRequest =
            ClaimProofRequest(
                signatureR = padSigHex(rHex, QbtcClaimConfig.PROOF_SERVICE_R_BYTES),
                signatureS = padSigHex(sHex, QbtcClaimConfig.PROOF_SERVICE_S_BYTES),
                publicKey = compressedPubkeyHex,
                utxos = utxos.map { ClaimProofUtxoRef(it.txid, it.vout) },
                claimerAddress = claimerAddress,
                chainId = chainId,
                broadcast = broadcast,
            )
    }
}

/**
 * Response from `POST /prove`. Under the post-qbtc#158 flow the proof service broadcasts the claim
 * tx itself and returns its [txHash]; the echoed hashes are cross-checked against locally-computed
 * values before trusting the result.
 */
@Serializable
data class ClaimProofResponse(
    val proof: String,
    @SerialName("message_hash") val messageHash: String,
    @SerialName("address_hash") val addressHash: String,
    @SerialName("qbtc_address_hash") val qbtcAddressHash: String,
    @SerialName("tx_hash") val txHash: String? = null,
)

@Serializable
data class ProofServiceHealth(
    val status: String,
    @SerialName("setup_loaded") val setupLoaded: Boolean = false,
) {
    val isHealthy: Boolean
        get() = status.equals("healthy", ignoreCase = true) && setupLoaded
}
