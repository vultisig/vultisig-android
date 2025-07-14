package com.vultisig.wallet.data.api.models

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

sealed class KyberSwapError(message: String) : Exception(message) {
    class ApiError(val code: Int, message: String, val details: List<String>?) :
        KyberSwapError(message)

    class TransactionWillRevert(message: String) : KyberSwapError(message)
    class InsufficientAllowance(message: String) : KyberSwapError(message)
    class InsufficientFunds(message: String) : KyberSwapError(message)
}

@Serializable
data class KyberSwapRouteResponse(
    val code: Int, val message: String, val data: RouteData, val requestId: String
) {
    @Serializable
    data class RouteData(
        val routeSummary: RouteSummary, val routerAddress: String
    )

    @Serializable
    data class RouteSummary(
        val gas: String,
        val gasPrice: String,
        val extraFee: ExtraFee? = null,
        val timestamp: Int
    ) {
        @Serializable
        data class ExtraFee(
            val feeAmount: String,
            val chargeFeeBy: String,
            val isInBps: Boolean,
            val feeReceiver: String
        )

    }
}

@Serializable
data class KyberSwapBuildRequest(
    val routeSummary: KyberSwapRouteResponse.RouteSummary,
    val sender: String,
    val referral: String? = null,
    val recipient: String, val slippageTolerance: Double,
    val deadline: Int,
    val enableGasEstimation: Boolean,
    val source: String,
    val ignoreCappedSlippage: Boolean
)

@Serializable
data class KyberSwapErrorResponse(
    val code: Int,
    val message: String,
    val details: List<String>? = null,
    val requestId: String? = null
)

@Serializable
data class KyberSwapToken(
    val address: String,
    val symbol: String,
    val name: String,
    val decimals: Int,
    val logoURI: String? = null
) {
    val logoUrl: String?
        get() = logoURI
}

fun KyberSwapToken.toCoinMeta(chain: Chain): Coin {
    return Coin(
        chain = chain,
        ticker = this.symbol,
        logo = this.logoURI ?: "",
        decimal = this.decimals,
        priceProviderID = "",
        // ?!  address = this.address
        contractAddress = this.address,
        isNativeToken = false,
        address = "",
        hexPublicKey = ""
    )
}

@Serializable
data class KyberSwapTokensResponse(
    val data: TokensData
) {
    @Serializable
    data class TokensData(
        val tokens: List<KyberSwapToken>
    )
}