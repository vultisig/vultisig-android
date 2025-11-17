package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.SuspendMapperFunc
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.models.isLayer2
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.models.send.TokenBalanceUiModel
import javax.inject.Inject

internal interface AccountToTokenBalanceUiModelMapper : SuspendMapperFunc<SendSrc, TokenBalanceUiModel>

internal class AccountToTokenBalanceUiModelMapperImpl @Inject constructor(
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
    private val mapFiatValueToString: FiatValueToStringMapper,
) : AccountToTokenBalanceUiModelMapper {

    override suspend fun invoke(from: SendSrc): TokenBalanceUiModel {
        val (_, fromAccount) = from
        val tokenValue = fromAccount.tokenValue
        
        return TokenBalanceUiModel(
            title = fromAccount.token.ticker,
            balance = tokenValue?.let(mapTokenValueToDecimalUiString),
            fiatValue = fromAccount.fiatValue?.let { mapFiatValueToString(it) },
            tokenLogo = getCoinLogo(fromAccount.token.logo),
            chainLogo = fromAccount.token.chain.logo,
            isNativeToken = fromAccount.token.isNativeToken,
            isLayer2 = fromAccount.token.chain.isLayer2,
            tokenStandard = fromAccount.token.chain.tokenStandard,
            model = from,
        )
    }

    private val Chain.tokenStandard: String?
        get() = when (this) {
            Chain.Ethereum -> "ERC20"
            Chain.BscChain -> "BEP20"
            else -> null
        }
}