package com.vultisig.wallet.data.models.payload

/**
 * A single transfer message within a TON transaction.
 *
 * @property toAddress destination wallet address
 * @property toAmount amount in nanotons (must be positive)
 * @property comment optional transfer comment
 * @property stateInit optional base64-encoded state-init cell
 * @property payload optional base64-encoded custom payload cell
 */
data class TonMessage(
    val toAddress: String,
    val toAmount: Long,
    val comment: String = "",
    val stateInit: String = "",
    val payload: String = "",
)

/**
 * Signing payload for a TON transaction carrying one to four transfer messages.
 *
 * @property messages list of [TonMessage]s to include in the transaction
 */
data class SignTon(val messages: List<TonMessage>) {
    /**
     * Validates the payload, throwing [IllegalArgumentException] on any violation: the list must be
     * non-empty, contain at most 4 messages, and every amount must be positive.
     */
    fun validate() {
        require(messages.isNotEmpty()) { "SignTon must have at least one message" }
        require(messages.size <= 4) { "SignTon supports at most 4 messages, got ${messages.size}" }
        messages.forEach { msg ->
            require(msg.toAmount > 0) {
                "TonMessage toAmount must be positive, got ${msg.toAmount}"
            }
        }
    }
}
