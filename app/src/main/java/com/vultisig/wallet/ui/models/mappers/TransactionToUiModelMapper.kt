package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.chains.helpers.RippleDestinationTag
import com.vultisig.wallet.data.mappers.SuspendMapperFunc
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.ui.models.TransactionDetailsUiModel
import com.vultisig.wallet.ui.models.swap.ValuedToken
import javax.inject.Inject

internal interface TransactionToUiModelMapper :
    SuspendMapperFunc<Transaction, TransactionDetailsUiModel>

internal class TransactionToUiModelMapperImpl
@Inject
constructor(
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
) : TransactionToUiModelMapper {

    override suspend fun invoke(from: Transaction): TransactionDetailsUiModel {
        // XRP: surface the destination tag whether it rides in the first-class proto field or the
        // legacy canonical-numeric memo carrier, and suppress that memo when it merely echoes the
        // tag (a dual-write / legacy carrier) so it isn't shown twice — once as tag, once as memo.
        val destinationTag =
            (from.blockChainSpecific as? BlockChainSpecific.Ripple)?.destinationTag?.toString()
                ?: if (from.token.chain == Chain.Ripple)
                    RippleDestinationTag.parseCanonicalDestinationTag(from.memo)?.toString()
                else null
        val memo =
            if (from.token.chain == Chain.Ripple && from.memo == destinationTag) null else from.memo

        return TransactionDetailsUiModel(
            token =
                ValuedToken(
                    value = mapTokenValueToDecimalUiString(from.tokenValue),
                    token = from.token,
                    fiatValue = fiatValueToStringMapper(from.fiatValue),
                ),
            srcAddress = from.srcAddress,
            dstAddress = from.dstAddress,
            dstLabel = from.dstLabel,
            memo = memo,
            destinationTag = destinationTag,
            signAmino = from.signAmino,
            signDirect = from.signDirect,
            signSolana = from.signSolana,
            signSui = from.signSui,
            networkFeeFiatValue = from.estimatedFee,
            networkFeeTokenValue = from.totalGas,
        )
    }
}
