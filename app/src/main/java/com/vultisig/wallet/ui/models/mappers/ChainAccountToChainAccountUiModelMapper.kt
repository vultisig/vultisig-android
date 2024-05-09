package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.Mapper
import com.vultisig.wallet.data.models.ChainAccount
import com.vultisig.wallet.ui.models.ChainAccountUiModel
import javax.inject.Inject

internal interface ChainAccountToChainAccountUiModelMapper :
    Mapper<ChainAccount, ChainAccountUiModel>

internal class ChainAccountToChainAccountUiModelMapperImpl @Inject constructor(
    private val fiatValueToStringMapper: FiatValueToStringMapper,
) : ChainAccountToChainAccountUiModelMapper {

    override fun map(from: ChainAccount) = ChainAccountUiModel(
        chainName = from.chainName,
        logo = from.logo,
        address = from.address,
        nativeTokenAmount = from.nativeTokenAmount,
        fiatAmount = from.fiatValue?.let(fiatValueToStringMapper::map),
        coins = from.coins,
    )

}