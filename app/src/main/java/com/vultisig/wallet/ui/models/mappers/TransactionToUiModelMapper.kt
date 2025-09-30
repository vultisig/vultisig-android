package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.SuspendMapperFunc
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.ui.models.SendTxUiModel
import com.vultisig.wallet.ui.models.swap.ValuedToken
import javax.inject.Inject

internal interface TransactionToUiModelMapper : SuspendMapperFunc<Transaction, SendTxUiModel>

internal class TransactionToUiModelMapperImpl @Inject constructor(
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
) : TransactionToUiModelMapper {

    override suspend fun invoke(from: Transaction): SendTxUiModel {
        return SendTxUiModel(
            token = ValuedToken(
                value = mapTokenValueToDecimalUiString(from.tokenValue),
                token = from.token,
                fiatValue = fiatValueToStringMapper(from.fiatValue),
            ),
            srcAddress = from.srcAddress,
            dstAddress = from.dstAddress,
            memo = from.memo,
            networkFeeFiatValue = from.estimatedFee,
            networkFeeTokenValue = from.totalGas,
        )
    }
}