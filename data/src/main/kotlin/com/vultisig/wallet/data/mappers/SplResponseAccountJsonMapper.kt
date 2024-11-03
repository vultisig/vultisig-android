package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.api.models.SplResponseAccountJson
import com.vultisig.wallet.data.repositories.SplTokenResponse
import javax.inject.Inject

internal interface SplResponseAccountJsonMapper :
    MapperFunc<SplResponseAccountJson, SplTokenResponse>

internal class SplResponseAccountJsonMapperImpl @Inject constructor() :
    SplResponseAccountJsonMapper {
    override fun invoke(response: SplResponseAccountJson): SplTokenResponse {
        val info = response.account.data.parsed.info
        val amount = info.tokenAmount.amount.toBigInteger()
        val mint = info.mint
        return SplTokenResponse(mint, amount)
    }
}