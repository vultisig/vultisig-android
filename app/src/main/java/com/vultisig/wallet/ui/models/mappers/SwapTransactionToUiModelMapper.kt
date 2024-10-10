package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.SuspendMapperFunc
import com.vultisig.wallet.data.models.SwapTransaction
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import kotlinx.coroutines.flow.first
import javax.inject.Inject

internal interface SwapTransactionToUiModelMapper :
    SuspendMapperFunc<SwapTransaction, SwapTransactionUiModel>

internal class SwapTransactionToUiModelMapperImpl @Inject constructor(
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase,
    private val appCurrencyRepository: AppCurrencyRepository,
) :
    SwapTransactionToUiModelMapper {
    override suspend fun invoke(from: SwapTransaction): SwapTransactionUiModel {
        val currency = appCurrencyRepository.currency.first()
        val fiatFees = convertTokenValueToFiat(
            from.dstToken,
            from.estimatedFees, currency
        )
        return SwapTransactionUiModel(
            srcTokenValue = mapTokenValueToStringWithUnit(from.srcTokenValue),
            dstTokenValue = mapTokenValueToStringWithUnit(from.expectedDstTokenValue),
            hasConsentAllowance = from.isApprovalRequired,
            estimatedFees = fiatValueToStringMapper.map(fiatFees),
        )
    }

}
