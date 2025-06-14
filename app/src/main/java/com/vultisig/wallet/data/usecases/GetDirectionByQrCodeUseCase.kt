package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.common.JOIN_KEYGEN_FLOW
import com.vultisig.wallet.data.common.JOIN_KEYSIGN_FLOW
import com.vultisig.wallet.data.common.JOIN_SEND_ON_ADDRESS_FLOW
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.getAddressFromQrCode
import com.vultisig.wallet.ui.utils.isReshare
import timber.log.Timber
import javax.inject.Inject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal interface GetDirectionByQrCodeUseCase : suspend (String, String?) -> Any

internal class GetDirectionByQrCodeUseCaseImpl @Inject constructor(
    private val getFlowType: GetFlowTypeUseCase,
) : GetDirectionByQrCodeUseCase {
    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun invoke(qr: String, vaultId: String?): Any {
        Timber.d("joinOrSend(qr = $qr)")
        val flowType = getFlowType(qr)
        val qrBase64 = Base64.UrlSafe.encode(qr.toByteArray())
        return try {
            when (flowType) {
                JOIN_KEYSIGN_FLOW -> {
                    Route.Keysign.Join(
                        vaultId = requireNotNull(vaultId),
                        qr = qrBase64,
                    )
                }

                JOIN_KEYGEN_FLOW -> {
                    Route.Keygen.Join(
                        qr = qrBase64,
                    )
                }

                JOIN_SEND_ON_ADDRESS_FLOW -> {
                    val address = qr.getAddressFromQrCode()
                    Route.Send(
                        vaultId = requireNotNull(vaultId),
                        address = address,
                    )
                }

                else -> Route.ScanError
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to navigate to destination")
            Route.ScanError
        }
    }
}