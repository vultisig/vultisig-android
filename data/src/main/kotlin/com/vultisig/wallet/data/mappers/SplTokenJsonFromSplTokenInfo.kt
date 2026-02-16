package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.api.models.SplTokenInfo
import com.vultisig.wallet.data.api.models.SplTokenJson
import com.vultisig.wallet.data.api.models.SplTokenListJson
import javax.inject.Inject


interface SplTokenJsonFromSplTokenInfoMapper : MapperFunc<SplTokenInfo, SplTokenJson>

internal class SplTokenJsonFromSplTokenInfoImpl @Inject constructor(
) : SplTokenJsonFromSplTokenInfoMapper {

    override fun invoke(from: SplTokenInfo): SplTokenJson = SplTokenJson(
        decimals = from.decimals,
        tokenList = SplTokenListJson(
            logo = from.logoURI,
            ticker = from.symbol,
            extensions = from.extensions
        ),
        mint = from.address,
        usdPrice = from.usdPrice
    )
}