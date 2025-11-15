package com.vultisig.wallet.data.models

import kotlinx.serialization.Serializable

@Serializable
data class SendDeeplinkData(
    val assetChain: String?,
    val assetTicker: String?,
    val toAddress: String?,
    val amount: String?,
    val memo: String?,
) {
    val isValid: Boolean
        get() = !assetChain.isNullOrBlank() &&
                !assetTicker.isNullOrBlank() &&
                !toAddress.isNullOrBlank()
}