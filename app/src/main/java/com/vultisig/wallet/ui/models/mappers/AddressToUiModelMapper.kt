package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.Mapper
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.calculateAccountsTotalFiatValue
import com.vultisig.wallet.models.logo
import com.vultisig.wallet.ui.models.AccountUiModel
import javax.inject.Inject

internal interface AddressToUiModelMapper :
    Mapper<Address, AccountUiModel>

internal class AddressToUiModelMapperImpl @Inject constructor(
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
) : AddressToUiModelMapper {

    override fun map(from: Address) = AccountUiModel(
        model = from,
        chainName = from.chain.uiName,
        logo = from.chain.logo,
        address = from.address,
        nativeTokenAmount = from.accounts.first { it.token.isNativeToken }
            .tokenValue?.let(mapTokenValueToDecimalUiString),
        fiatAmount = from.accounts.calculateAccountsTotalFiatValue()
            ?.let(fiatValueToStringMapper::map),
        assetsSize = from.accounts.size,
    )

}