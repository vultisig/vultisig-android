package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.chains.helpers.THORChainSwaps
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.usecases.ConvertBpsToFiatUseCase
import com.vultisig.wallet.data.usecases.getTierType
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.screens.settings.TierType
import javax.inject.Inject

internal data class VultDiscountResult(
    val vultBpsDiscount: Int?,
    val vultBpsDiscountFiatValue: String?,
    val tierType: TierType?,
)

internal data class ReferralDiscountResult(
    val referralBpsDiscount: Int?,
    val referralBpsDiscountFiatValue: String?,
    val referralCode: String?,
)

internal class SwapDiscountChecker
@Inject
constructor(
    private val convertBpsToFiat: ConvertBpsToFiatUseCase,
    private val fiatValueToString: FiatValueToStringMapper,
) {

    suspend fun checkVultBpsDiscount(
        srcToken: Coin,
        tokenValue: TokenValue,
        vultBPSDiscount: Int?,
    ): VultDiscountResult {
        if (vultBPSDiscount == null) {
            return VultDiscountResult(
                vultBpsDiscount = null,
                vultBpsDiscountFiatValue = null,
                tierType = null,
            )
        }
        val vultBpsDiscountFiat =
            convertBpsToFiat(token = srcToken, tokenValue = tokenValue, bps = vultBPSDiscount)
        val vultBpsDiscountFiatValue = fiatValueToString(vultBpsDiscountFiat)
        val tierType = vultBPSDiscount.getTierType()
        return VultDiscountResult(
            vultBpsDiscount = vultBPSDiscount,
            vultBpsDiscountFiatValue = vultBpsDiscountFiatValue,
            tierType = tierType,
        )
    }

    suspend fun checkReferralBpsDiscount(
        tierType: TierType?,
        srcToken: Coin,
        tokenValue: TokenValue,
        code: String,
    ): ReferralDiscountResult {
        val referralBpsDiscount =
            THORChainSwaps.REFERRED_USER_FEE_RATE_BP.takeUnless { tierType == TierType.ULTIMATE }
                ?: return ReferralDiscountResult(
                    referralBpsDiscount = null,
                    referralBpsDiscountFiatValue = null,
                    referralCode = null,
                )
        val referralBpsDiscountFiat =
            convertBpsToFiat(token = srcToken, tokenValue = tokenValue, bps = referralBpsDiscount)
        val referralBpsDiscountFiatFormatted = fiatValueToString(referralBpsDiscountFiat)
        return ReferralDiscountResult(
            referralBpsDiscount = referralBpsDiscount,
            referralBpsDiscountFiatValue = referralBpsDiscountFiatFormatted,
            referralCode = code,
        )
    }
}
