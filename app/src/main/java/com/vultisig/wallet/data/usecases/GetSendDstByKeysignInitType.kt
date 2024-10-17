package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.ui.models.keysign.KeysignInitType
import com.vultisig.wallet.ui.navigation.SendDst

internal interface GetSendDstByKeysignInitType : suspend (KeysignInitType, String, String?) -> SendDst

internal class GetSendDstByKeysignInitTypeImpl : GetSendDstByKeysignInitType {
    override suspend fun invoke(
        keysignInitType: KeysignInitType,
        transactionId: String,
        password: String?
    ): SendDst {
        return when (keysignInitType) {
            KeysignInitType.BIOMETRY -> {
                SendDst.Keysign(
                    transactionId = transactionId,
                    password = password,
                )
            }
            KeysignInitType.PASSWORD -> {
                SendDst.Password(
                    transactionId = transactionId,
                )
            }
            KeysignInitType.QR_CODE -> {
                SendDst.Keysign(
                    transactionId = transactionId,
                    password = null,
                )
            }
        }
    }
}