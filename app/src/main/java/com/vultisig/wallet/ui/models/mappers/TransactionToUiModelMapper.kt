package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.SuspendMapperFunc
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.ui.models.SendTxUiModel
import com.vultisig.wallet.ui.models.swap.ValuedToken
import kotlinx.serialization.json.Json
import vultisig.keysign.v1.SignAmino
import vultisig.keysign.v1.SignDirect
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
            signAmino = from.signAmino,
            signDirect = from.signDirect,
            networkFeeFiatValue = from.estimatedFee,
            networkFeeTokenValue = from.totalGas,
        )
    }
}
