package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import javax.inject.Inject

/**
 * Patches a Solana [KeysignPayload] with a fresh recent block hash before signing.
 *
 * Extracted verbatim from `KeysignFlowViewModel.updateSolanaKeysignPayload`. Non-Solana payloads
 * (and `null`) are returned unchanged.
 */
internal class UpdateSolanaKeysignPayloadUseCase
@Inject
constructor(private val solanaApi: SolanaApi) {
    suspend operator fun invoke(keysignPayload: KeysignPayload?): KeysignPayload? =
        keysignPayload
            ?.takeIf { it.blockChainSpecific is BlockChainSpecific.Solana }
            ?.let { payload ->
                payload.copy(
                    blockChainSpecific =
                        (payload.blockChainSpecific as BlockChainSpecific.Solana).copy(
                            recentBlockHash = solanaApi.getRecentBlockHash()
                        )
                )
            } ?: keysignPayload
}
