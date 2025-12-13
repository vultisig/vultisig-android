package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.SuspendMapperFunc
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.ui.models.deposit.DepositTransactionUiModel
import com.vultisig.wallet.ui.models.swap.ValuedToken
import kotlinx.coroutines.flow.first
import javax.inject.Inject

internal interface DepositTransactionToUiModelMapper :
    SuspendMapperFunc<DepositTransaction, DepositTransactionUiModel>

internal class DepositTransactionUiModelMapperImpl @Inject constructor(
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
            token = ValuedToken(
                token = from.srcToken,
                value = mapTokenValueToDecimalUiString(from.srcTokenValue),
                fiatValue = fiatValueToStringMapper(
                    convertTokenValueToFiat(
                        from.srcToken,
                        from.srcTokenValue,
                        currency
                    )
                ),
            ),
            networkFeeFiatValue = from.estimateFeesFiat,
            networkFeeTokenValue = mapTokenValueToStringWithUnit(from.estimatedFees),
            memo = from.memo,
            dstAddress = from.dstAddress,
            operation = from.operation,
            thorAddress = from.thorAddress,
        )
    }
}