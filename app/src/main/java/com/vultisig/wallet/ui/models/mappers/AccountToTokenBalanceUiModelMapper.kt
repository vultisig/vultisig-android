package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.Mapper
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.models.Coins
import com.vultisig.wallet.ui.models.send.TokenBalanceUiModel
import javax.inject.Inject

internal interface AccountToTokenBalanceUiModelMapper : Mapper<Account, TokenBalanceUiModel>

internal class AccountToTokenBalanceUiModelMapperImpl @Inject constructor(
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
) :
    AccountToTokenBalanceUiModelMapper {

    override fun map(from: Account) = TokenBalanceUiModel(
        title = from.token.ticker,
        balance = from.tokenValue
            ?.let(mapTokenValueToDecimalUiString),
        logo = Coins.getCoinLogo(from.token.logo),
        model = from,
    )

}