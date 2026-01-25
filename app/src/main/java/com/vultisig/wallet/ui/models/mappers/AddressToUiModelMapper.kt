package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.SuspendMapperFunc
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.calculateAccountsTotalFiatValue
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.models.toDefi
import com.vultisig.wallet.ui.models.AccountUiModel
import javax.inject.Inject

internal interface AddressToUiModelMapper :
    SuspendMapperFunc<Address, AccountUiModel>

internal class AddressToUiModelMapperImpl @Inject constructor(
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val mapTokenValueToStringWithUnitMapper: TokenValueToStringWithUnitMapper,
) : AddressToUiModelMapper {

    override suspend fun invoke(from: Address): AccountUiModel {
        val nativeAccount = from.accounts.first { it.token.isNativeToken }
        val isDefiProvider = from.isDefiProvider
        return AccountUiModel(
            model = from,
            chainName = if (isDefiProvider) from.chain.toDefi.ticker else from.chain.raw,
            logo = if (isDefiProvider) from.chain.toDefi.logo else from.chain.logo,
            address = from.address,
            nativeTokenAmount = nativeAccount
                .tokenValue?.let(mapTokenValueToStringWithUnitMapper),
            fiatAmount = from.accounts.calculateAccountsTotalFiatValue()
                ?.let { fiatValueToStringMapper(it) },
            assetsSize = from.accounts.size,
            nativeTokenTicker = nativeAccount.token.ticker,
        )
    }
}
