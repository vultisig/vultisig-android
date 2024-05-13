package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.Mapper
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.ui.models.ChainAccountUiModel
import javax.inject.Inject

internal interface ChainAccountToChainAccountUiModelMapper :
    Mapper<Account, ChainAccountUiModel>

internal class ChainAccountToChainAccountUiModelMapperImpl @Inject constructor(
    private val fiatValueToStringMapper: FiatValueToStringMapper,
) : ChainAccountToChainAccountUiModelMapper {

    override fun map(from: Account) = ChainAccountUiModel(
        chainName = from.chainName,
        logo = from.logo,
        address = from.address,
        nativeTokenAmount = from.tokenAmount?.toPlainString(),
        fiatAmount = from.fiatValue?.let(fiatValueToStringMapper::map),
    )

}