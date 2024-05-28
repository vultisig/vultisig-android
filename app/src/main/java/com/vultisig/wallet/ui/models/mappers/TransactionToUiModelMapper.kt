package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.MapperFunc
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.ui.models.TransactionUiModel
import javax.inject.Inject

internal interface TransactionToUiModelMapper : MapperFunc<Transaction, TransactionUiModel>

internal class TransactionToUiModelMapperImpl @Inject constructor(
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val mapTokenValueToString: TokenValueToStringWithUnitMapper,
) : TransactionToUiModelMapper {

    override fun invoke(from: Transaction): TransactionUiModel {
        val fiatValue = from.fiatValue

        val tokenValueString = mapTokenValueToString(from.tokenValue)
        val fiatValueString = fiatValueToStringMapper.map(from.fiatValue)
        val gasFeeString = mapTokenValueToString(from.gasFee)

        return TransactionUiModel(
            srcAddress = from.srcAddress,
            dstAddress = from.dstAddress,
            tokenValue = tokenValueString,
            fiatValue = fiatValueString,
            fiatCurrency = fiatValue.currency,
            gasValue = gasFeeString,
            showGasField = from.gasFee.value > 0.toBigInteger()
        )
    }

}