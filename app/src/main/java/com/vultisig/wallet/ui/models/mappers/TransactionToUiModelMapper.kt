package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.tron.TRON_STAKING_MEMO_REGEX
import com.vultisig.wallet.data.blockchain.tron.TronStakingOperation
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
        // TRON freeze/unfreeze is routed through the Send form and carries its operation only as an
        // internal memo prefix ("FREEZE:<resource>" / "UNFREEZE:<resource>"). Surface that as an
        // operation-aware Verify header, but keep the captured resource (BANDWIDTH/ENERGY) in the
        // Memo row so the signed contract field stays visible. Gate on the native token so a TRC20
        // send that merely echoes a matching memo isn't mislabeled as a staking operation.
        val tronStakingMatch =
            if (from.token.chain == Chain.Tron && from.token.isNativeToken)
                from.memo?.let { TRON_STAKING_MEMO_REGEX.matchEntire(it) }
            else null
        val tronStakingOperation =
            tronStakingMatch?.groupValues?.get(1)?.let { prefix ->
                TronStakingOperation.entries.firstOrNull { it.memoPrefix == prefix }
            }
        val tronStakingResource = tronStakingMatch?.groupValues?.get(2)

        val memo =
            when {
                from.token.chain == Chain.Ripple && from.memo == destinationTag -> null
                tronStakingOperation != null -> tronStakingResource
                else -> from.memo
            }

        val headerTitleRes =
            when (tronStakingOperation) {
                TronStakingOperation.FREEZE -> R.string.tron_freeze_screen_title
                TronStakingOperation.UNFREEZE -> R.string.tron_unfreeze_screen_title
                null -> null
            }

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
            headerTitleRes = headerTitleRes,
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
