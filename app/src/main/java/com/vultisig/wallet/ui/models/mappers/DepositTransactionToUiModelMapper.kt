package com.vultisig.wallet.ui.models.mappers

import androidx.annotation.StringRes
import com.vultisig.wallet.R
import com.vultisig.wallet.data.mappers.SuspendMapperFunc
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.OPERATION_UNBOND
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.ui.models.deposit.DepositTransactionUiModel
import com.vultisig.wallet.ui.models.swap.ValuedToken
import javax.inject.Inject
import kotlinx.coroutines.flow.first

internal interface DepositTransactionToUiModelMapper :
    SuspendMapperFunc<DepositTransaction, DepositTransactionUiModel>

/**
 * Resolves the "Function overview" header for a deposit: an Unbond reads "Unbonding" rather than
 * the generic "You're sending", since it isn't a send (issue #5301). Keyed off the structured
 * [operation] alone — never the raw memo — so a Custom deposit whose free-text memo happens to
 * start with "unbond" is not mislabeled. Every unbond producer sets the operation: the two
 * UnbondStrategy paths on the initiator, and the joiner via ThorchainMemoParser.
 */
@StringRes
internal fun depositVerifyTitleRes(operation: String): Int =
    if (operation == OPERATION_UNBOND) {
        R.string.verify_deposit_unbonding
    } else {
        R.string.verify_deposit_sending
    }

internal class DepositTransactionUiModelMapperImpl
@Inject
constructor(
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper,
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase,
    private val appCurrencyRepository: AppCurrencyRepository,
) : DepositTransactionToUiModelMapper {

    override suspend fun invoke(from: DepositTransaction): DepositTransactionUiModel {
        val currency = appCurrencyRepository.currency.first()
        return DepositTransactionUiModel(
            srcAddress = from.srcAddress,
            token =
                ValuedToken(
                    token = from.srcToken,
                    value = mapTokenValueToDecimalUiString(from.srcTokenValue),
                    fiatValue =
                        fiatValueToStringMapper(
                            convertTokenValueToFiat(from.srcToken, from.srcTokenValue, currency)
                        ),
                ),
            // Cosmos staking VMs leave estimateFeesFiat blank (the fee denom equals the native
            // staking token); derive the fiat from the native fee so deposits show it like sends
            // do, on both the initiator and a joining device (issue #4939).
            networkFeeFiatValue =
                from.estimateFeesFiat.ifBlank {
                    fiatValueToStringMapper(
                        convertTokenValueToFiat(from.srcToken, from.estimatedFees, currency),
                        asFee = true,
                    )
                },
            networkFeeTokenValue = mapTokenValueToStringWithUnit(from.estimatedFees),
            memo = from.memo,
            dstAddress = from.dstAddress,
            operation = from.operation,
            thorAddress = from.thorAddress,
            nodeAddress = from.nodeAddress,
            pairedAddress = from.pairedAddress,
            pool = from.pool,
            validatorName = from.validatorName.orEmpty(),
            titleRes = depositVerifyTitleRes(from.operation),
        )
    }
}
