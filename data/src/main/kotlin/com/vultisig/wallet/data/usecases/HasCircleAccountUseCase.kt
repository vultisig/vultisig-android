package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.CircleApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.ScaCircleAccountRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Resolves whether a vault already has a Circle (USDC yield) MSCA account.
 *
 * Checks the local cache first; if empty, discovers the account on-chain via [CircleApi] and caches
 * the result. Discovery keeps withdraw access working for existing accounts even on a fresh install
 * where the local cache is empty. New Circle deposits are disabled, so this is used to hide the
 * Circle entry for vaults that never opened an account.
 */
interface HasCircleAccountUseCase : suspend (String) -> Boolean

internal class HasCircleAccountUseCaseImpl
@Inject
constructor(
    private val scaCircleAccountRepository: ScaCircleAccountRepository,
    private val circleApi: CircleApi,
    private val vaultRepository: VaultRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
) : HasCircleAccountUseCase {

    override suspend fun invoke(vaultId: String): Boolean {
        if (scaCircleAccountRepository.getAccount(vaultId) != null) {
            return true
        }

        return try {
            val vault = vaultRepository.get(vaultId) ?: return false
            val (evmAddress, _) = chainAccountAddressRepository.getAddress(Chain.Ethereum, vault)
            val mscaAddress = circleApi.getScAccount(evmAddress)
            if (mscaAddress != null) {
                scaCircleAccountRepository.saveAccount(vaultId, mscaAddress)
                true
            } else {
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // A network blip must not wrongly hide an existing account; fall back to the cache.
            Timber.e(
                t,
                "HasCircleAccountUseCase: failed to discover Circle account for %s",
                vaultId,
            )
            false
        }
    }
}
