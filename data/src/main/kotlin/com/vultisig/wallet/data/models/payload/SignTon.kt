package com.vultisig.wallet.data.models.payload

/**
 * A single message within a TonConnect multi-message sendTransaction payload.
 *
 * @param toAddress Destination address in TON format.
 * @param toAmount Amount in nanotons (must be positive).
 * @param comment Optional plaintext comment attached to the transfer.
 * @param stateInit Optional base64-encoded state-init BoC.
 * @param payload Optional base64-encoded arbitrary cell payload.
 */
data class TonMessage(
    val toAddress: String,
    val toAmount: Long,
    val comment: String = "",
    val stateInit: String = "",
    val payload: String = "",
)

/**
 * TonConnect `sendTransaction` payload carrying one or more TON messages (up to 4).
 *
 * @param messages Non-empty list of [TonMessage] entries (max 4).
 */
data class SignTon(val messages: List<TonMessage>) {
    /**
     * Validates the payload.
     *
     * @throws IllegalArgumentException if [messages] is empty, contains more than 4 entries, or any
     *   [TonMessage.toAmount] is not positive.
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
