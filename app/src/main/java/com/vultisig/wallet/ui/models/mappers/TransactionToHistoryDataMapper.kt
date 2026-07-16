package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.chains.helpers.RippleDappTransactionDecoder
import com.vultisig.wallet.data.chains.helpers.RippleDappTxFieldKey
import com.vultisig.wallet.data.mappers.MapperFunc
import com.vultisig.wallet.data.models.SendTransactionHistoryData
import com.vultisig.wallet.ui.models.TransactionDetailsUiModel
import javax.inject.Inject

internal interface SendTransactionHistoryDataMapper :
    MapperFunc<TransactionDetailsUiModel, SendTransactionHistoryData>

internal class SendTransactionHistoryDataMapperImpl @Inject constructor() :
    SendTransactionHistoryDataMapper {

    override fun invoke(from: TransactionDetailsUiModel): SendTransactionHistoryData {
        // A dApp XRPL tx (signRipple) has native amount 0 and — for offers/trust lines — no wire
        // recipient. Persist the decoded one-line summary and the JSON's real Destination so the
        // history row keeps its true meaning after a restart instead of downgrading to "0 XRP → ".
        val rippleDapp = from.signRipple
        val dappSummary = rippleDapp?.let { RippleDappTransactionDecoder.summarize(it.rawJson) }
        val toAddress =
            rippleDapp?.value(RippleDappTxFieldKey.TO)?.takeIf { it.isNotBlank() }
                ?: from.dstAddress

        return SendTransactionHistoryData(
            fromAddress = from.srcAddress,
            toAddress = toAddress,
            amount = from.token.value,
            token = from.token.token.ticker,
            tokenLogo = from.token.token.logo,
            feeEstimate = from.networkFeeTokenValue,
            memo = from.memo.orEmpty(),
            fiatValue = from.token.fiatValue,
            dappSummary = dappSummary,
        )
    }
}
