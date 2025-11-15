package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.common.DeepLinkHelper
import com.vultisig.wallet.data.common.JOIN_SEND_ON_ADDRESS_FLOW
import com.vultisig.wallet.data.common.UNKNOWN_FLOW
import com.vultisig.wallet.data.common.SEND_FLOW
import com.vultisig.wallet.data.common.isJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.ui.utils.getAddressFromQrCode
import timber.log.Timber
import javax.inject.Inject

interface GetFlowTypeUseCase : (String) -> String

internal class GetFlowTypeUseCaseImpl @Inject constructor(
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
) : GetFlowTypeUseCase {
    override fun invoke(qr: String): String {
        return try {
            val deepLinkHelper = DeepLinkHelper(qr)
            return if(deepLinkHelper.isSendDeeplink())
                SEND_FLOW
            else deepLinkHelper.getFlowType() ?: throw IllegalArgumentException("No flowType found")
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse QR-code via DeepLinkHelper")
            if (qr.isJson()) {
                UNKNOWN_FLOW
            } else {
                val address = qr.getAddressFromQrCode()
                if (Chain.entries.any { chain ->
                        chainAccountAddressRepository.isValid(chain, address)
                    }) {
                    JOIN_SEND_ON_ADDRESS_FLOW
                } else {
                    UNKNOWN_FLOW
                }
            }
        }
    }
}