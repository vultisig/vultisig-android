package com.vultisig.wallet.ui.navigation.util

import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.ui.models.keysign.KeysignInitType
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import javax.inject.Inject

internal fun interface LaunchKeysignUseCase {
    suspend operator fun invoke(
        keysignInitType: KeysignInitType,
        transactionId: String,
        password: String?,
        txType: Route.Keysign.Keysign.TxType,
        vaultId: VaultId,
    )
}

internal class LaunchKeysignUseCaseImpl @Inject constructor(
    private val navigator: Navigator<Destination>,
) : LaunchKeysignUseCase {

    override suspend fun invoke(
        keysignInitType: KeysignInitType,
        transactionId: String,
        password: String?,
        txType: Route.Keysign.Keysign.TxType,
        vaultId: VaultId,
    ) {
        navigator.route(
            when (keysignInitType) {
                KeysignInitType.BIOMETRY -> {
                    Route.Keysign.Keysign(
                        transactionId = transactionId,
                        password = password,
                        txType = txType,
                    )
                }

                KeysignInitType.PASSWORD -> {
                    Route.Keysign.Password(
                        transactionId = transactionId,
                        txType = txType,
                        vaultId = vaultId,
                    )
                }

                KeysignInitType.QR_CODE -> {
                    Route.Keysign.Keysign(
                        transactionId = transactionId,
                        password = null,
                        txType = txType,
                    )
                }
            }
        )
    }

}