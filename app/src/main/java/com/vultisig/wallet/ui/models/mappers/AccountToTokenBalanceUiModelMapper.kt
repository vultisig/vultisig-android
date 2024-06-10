package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.Mapper
import com.vultisig.wallet.models.Coins
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.models.send.TokenBalanceUiModel
import javax.inject.Inject

internal interface AccountToTokenBalanceUiModelMapper : Mapper<SendSrc, TokenBalanceUiModel>

internal class AccountToTokenBalanceUiModelMapperImpl @Inject constructor(
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
) :
    AccountToTokenBalanceUiModelMapper {

    override fun map(from: SendSrc): TokenBalanceUiModel {
        val (_, fromAccount) = from
        return TokenBalanceUiModel(
            title = fromAccount.token.ticker,
            balance = fromAccount.tokenValue
                ?.let(mapTokenValueToDecimalUiString),
            logo = Coins.getCoinLogo(fromAccount.token.logo),
            model = from,
        )
    }

}