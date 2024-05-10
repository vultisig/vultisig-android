package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.Mapper
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.ui.models.TokenBalanceUiModel
import javax.inject.Inject

internal interface AccountToTokenBalanceUiModelMapper : Mapper<Account, TokenBalanceUiModel>

internal class AccountToTokenBalanceUiModelMapperImpl @Inject constructor() :
    AccountToTokenBalanceUiModelMapper {

    override fun map(from: Account) = TokenBalanceUiModel(
        title = from.token.ticker,
        balance = from.tokenAmount,
        logo = from.logo,
        model = from,
    )

}