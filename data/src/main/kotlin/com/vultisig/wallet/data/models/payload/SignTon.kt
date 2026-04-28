package com.vultisig.wallet.data.models.payload

import kotlinx.serialization.Serializable

/**
 * A single transfer message within a multi-message TON transaction.
 *
 * @property toAddress destination wallet address
 * @property toAmount amount in nanotons (must be positive)
 * @property payload optional base64-encoded custom payload cell (BoC)
 * @property stateInit optional base64-encoded state-init cell (BoC), used for contract deployments
 */
@Serializable
data class TonMessage(
    val toAddress: String,
    val toAmount: Long,
    val payload: String = "",
    val stateInit: String = "",
)

/**
 * Signing payload for a TonConnect-originated TON transaction carrying one to four transfer
 * messages. Mirrors the iOS `SignTon` value type and the on-the-wire `vultisig.keysign.v1.SignTon`
 * proto.
 *
 * @property messages list of [TonMessage]s to include in the transaction
 */
@Serializable
data class SignTon(val messages: List<TonMessage>) {
    /**
     * Validates the payload, throwing [IllegalArgumentException] on any violation: the list must be
     * non-empty, contain at most 4 messages, and every amount must be positive.
     */
    fun validate() {
        require(messages.isNotEmpty()) { "SignTon must have at least one message" }
        require(messages.size <= MAX_MESSAGES) {
            "SignTon supports at most $MAX_MESSAGES messages, got ${messages.size}"
        }
        messages.forEach { msg ->
            require(msg.toAmount > 0) {
                "TonMessage toAmount must be positive, got ${msg.toAmount}"
            }
        }
    }

    companion object {
        const val MAX_MESSAGES = 4
    }
}
