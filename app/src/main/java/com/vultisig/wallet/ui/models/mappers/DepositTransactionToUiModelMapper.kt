package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.SuspendMapperFunc
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.ui.models.deposit.DepositTransactionUiModel
import com.vultisig.wallet.ui.models.swap.ValuedToken
import javax.inject.Inject
import kotlinx.coroutines.flow.first

internal interface DepositTransactionToUiModelMapper :
    SuspendMapperFunc<DepositTransaction, DepositTransactionUiModel>

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
        )
    }
}
