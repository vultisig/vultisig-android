package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.common.DeepLinkHelper
import com.vultisig.wallet.data.common.JOIN_SEND_ON_ADDRESS_FLOW
import com.vultisig.wallet.data.common.UNKNOWN_FLOW
import com.vultisig.wallet.data.common.isJson
import timber.log.Timber
import javax.inject.Inject

interface GetFlowTypeUseCase : (String) -> String

internal class GetFlowTypeUseCaseImpl @Inject constructor() : GetFlowTypeUseCase {
    override fun invoke(qr: String): String {
        return try {
            DeepLinkHelper(qr).getFlowType()?: throw IllegalArgumentException("No flowType found")
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse QR-code via DeepLinkHelper")
            if (qr.isJson()) {
                UNKNOWN_FLOW
            } else {
                JOIN_SEND_ON_ADDRESS_FLOW
            }
        }
    }
}