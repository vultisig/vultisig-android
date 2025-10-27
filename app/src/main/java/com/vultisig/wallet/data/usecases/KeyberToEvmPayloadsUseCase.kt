/*
package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.calculateAddressesTotalFiatValue
import com.vultisig.wallet.data.models.payload.KyberSwapPayloadJson
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject


internal interface KyberPayloadToEvmPayloadUseCase :
    suspend (KyberSwapPayloadJson) -> EVMSwapPayloadJson

internal class KyberPayloadToEvmPayloadUseCaseImpl @Inject constructor() :
    KyberPayloadToEvmPayloadUseCase {
    override suspend fun invoke(payload: KyberSwapPayloadJson): EVMSwapPayloadJson {
        return EVMSwapPayloadJson(
            fromCoin = payload.fromCoin,
            toCoin = payload.toCoin,
            fromAmount = payload.fromAmount,
            toAmountDecimal = payload.toAmountDecimal,
            quote = payload.quote,
            provider = "Kyber Network",
        )
    }
}
*/
