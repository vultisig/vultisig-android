package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.MapperFunc
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.DefiChainUiModel
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.models.toDefi
import javax.inject.Inject

internal interface ChainToDefiChainUiMapper : MapperFunc<Chain, DefiChainUiModel>

internal class ChainToDefiChainUiMapperImpl @Inject constructor() : ChainToDefiChainUiMapper {

    override fun invoke(from: Chain): DefiChainUiModel {
        val defiChain = from.toDefi
        return DefiChainUiModel(
            chain = from,
            raw = defiChain.raw,
            logo = defiChain.logo,
            displayName = defiChain.displayName,
        )
    }
}
