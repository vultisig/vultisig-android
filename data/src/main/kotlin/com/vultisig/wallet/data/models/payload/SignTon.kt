package com.vultisig.wallet.data.models.payload

data class TonMessage(
    val toAddress: String,
    val toAmount: Long,
    val comment: String = "",
    val stateInit: String = "",
    val payload: String = "",
)

data class SignTon(val messages: List<TonMessage>) {
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
