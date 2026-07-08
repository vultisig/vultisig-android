package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.isFastVault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import javax.inject.Inject

/**
 * Patches a Solana [KeysignPayload] with a fresh block hash right before the keysign ceremony
 * starts.
 *
 * A Fast Vault ceremony auto-co-signs on the server with no human wait, so the fetch-to-broadcast
 * gap is short but still widened by the extra device-server round-trip — enough for a `confirmed`
 * blockhash to sometimes be unknown to the load-balanced RPC node that ends up broadcasting it.
 * [SolanaApi.getFinalizedBlockHash] fixes that: `finalized` is guaranteed to be known everywhere,
 * at the cost of ~13s of the blockhash's ~60-90s validity window, which a Fast Vault ceremony never
 * gets close to spending.
 *
 * A Secure Vault ceremony instead waits on a human to approve on another personal device, which can
 * take much longer and has no such guaranteed margin — so it stays on
 * [SolanaApi.getRecentBlockHash]'s `confirmed` commitment to keep the fullest validity window
 * available for that wait.
 *
 * Originally extracted from `KeysignFlowViewModel.updateSolanaKeysignPayload`, later extended with
 * vault-aware commitment selection. Non-Solana payloads (and `null`) are returned unchanged.
 */
internal class UpdateSolanaKeysignPayloadUseCase
@Inject
constructor(private val solanaApi: SolanaApi) {
    suspend operator fun invoke(keysignPayload: KeysignPayload?, vault: Vault): KeysignPayload? =
        keysignPayload
            ?.takeIf { it.blockChainSpecific is BlockChainSpecific.Solana }
            ?.let { payload ->
                payload.copy(
                    blockChainSpecific =
                        (payload.blockChainSpecific as BlockChainSpecific.Solana).copy(
                            recentBlockHash =
                                if (vault.isFastVault()) {
                                    solanaApi.getFinalizedBlockHash()
                                } else {
                                    solanaApi.getRecentBlockHash()
                                }
                        )
                )
            } ?: keysignPayload
}
