package com.vultisig.wallet.ui.utils

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves the display name of a locally-stored vault that owns [dstAddress] on [chain], if any.
 *
 * Matches against each vault's enabled coins first (cheap). When no enabled coin matches, falls
 * back to deriving each vault's pubkey-based address for [chain] so a known destination still
 * resolves even when that coin was never enabled in the local vault copy — e.g. on a joined
 * co-signer device that can sign the chain without having added it.
 *
 * @param allVaults every vault stored locally.
 * @param chainAccountAddressRepository derives a vault's per-chain address from its public key.
 * @param dispatcher dispatcher the pubkey-derivation fallback runs on; overridable for tests.
 * @return the matching vault's name, or null when no local vault owns the address.
 */
internal suspend fun resolveDstVaultName(
    allVaults: List<Vault>,
    chain: Chain,
    dstAddress: String,
    chainAccountAddressRepository: ChainAccountAddressRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
): String? {
    val normalizedDstAddress = normalizeAddressForLookup(dstAddress)

    allVaults
        .firstOrNull { v ->
            v.coins.any {
                it.chain == chain && normalizeAddressForLookup(it.address) == normalizedDstAddress
            }
        }
        ?.let {
            return it.name
        }

    return withContext(dispatcher) {
        allVaults
            .firstOrNull { v ->
                try {
                    val (derivedAddress, _) = chainAccountAddressRepository.getAddress(chain, v)
                    normalizeAddressForLookup(derivedAddress) == normalizedDstAddress
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
            }
            ?.name
    }
}
