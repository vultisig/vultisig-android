package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.MapperFunc
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.ui.models.deposit.DepositTransactionUiModel
import com.vultisig.wallet.ui.models.swap.ValuedToken
import javax.inject.Inject

internal interface DepositTransactionToUiModelMapper :
    MapperFunc<DepositTransaction, DepositTransactionUiModel>

internal class DepositTransactionUiModelMapperImpl @Inject constructor(
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper,
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
) : DepositTransactionToUiModelMapper {
    override fun invoke(from: DepositTransaction): DepositTransactionUiModel =
        DepositTransactionUiModel(
            token = ValuedToken(
                token = from.srcToken,
                value = mapTokenValueToDecimalUiString(from.srcTokenValue),
                fiatValue = "",
            ),
            fromAddress = from.srcAddress,
            srcTokenValue = mapTokenValueToStringWithUnit(from.srcTokenValue),
            estimatedFees = mapTokenValueToStringWithUnit(from.estimatedFees),
            estimateFeesFiat = from.estimateFeesFiat,
            memo = from.memo,
            nodeAddress = from.dstAddress,
        )
}