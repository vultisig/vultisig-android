package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.MapperFunc
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.ui.models.deposit.DepositTransactionUiModel
import javax.inject.Inject

internal interface DepositTransactionToUiModelMapper :
    MapperFunc<DepositTransaction, DepositTransactionUiModel>


internal class DepositTransactionUiModelMapperImpl @Inject constructor(
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
) : DepositTransactionToUiModelMapper {
    override fun invoke(from: DepositTransaction): DepositTransactionUiModel =
        DepositTransactionUiModel(
            fromAddress = from.srcAddress,
            srcTokenValue = mapTokenValueToStringWithUnit(from.srcTokenValue),
            estimatedFees = mapTokenValueToStringWithUnit(from.estimatedFees),
            estimateFeesFiat = "0",
            memo = from.memo,
            nodeAddress = from.dstAddress,
        )
}