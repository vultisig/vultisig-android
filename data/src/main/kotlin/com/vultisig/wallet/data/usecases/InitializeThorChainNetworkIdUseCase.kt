package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.crypto.ThorChainHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.ThorChainRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

interface InitializeThorChainNetworkIdUseCase : suspend () -> Unit

internal class InitializeThorChainNetworkIdUseCaseImpl
@Inject
constructor(
    private val thorChainRepository: ThorChainRepository,
    private val vaultRepository: VaultRepository,
) : InitializeThorChainNetworkIdUseCase {

    override suspend fun invoke() {
        val vaults = vaultRepository.getAll()
        if (vaults.isEmpty()) {
            Timber.d("Skipping THORChain network id init: no vaults")
            return
        }

        if (vaults.none { vault -> vault.coins.any { it.chain == Chain.ThorChain } }) {
            Timber.d("Skipping THORChain network id init: no vault uses THORChain")
            return
        }

        Timber.d("Initializing THORChain network id")

        val cachedNetworkChainId = thorChainRepository.getCachedNetworkChainId()
        if (!cachedNetworkChainId.isNullOrBlank()) {
            ThorChainHelper.THORCHAIN_NETWORK_ID = cachedNetworkChainId
            Timber.d("THORChain network id initialized from cache with: %s", cachedNetworkChainId)
        }

        try {
            ThorChainHelper.THORCHAIN_NETWORK_ID = thorChainRepository.fetchNetworkChainId()
            Timber.d(
                "THORChain network id initialized with: %s",
                ThorChainHelper.THORCHAIN_NETWORK_ID,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch network chain id")
        }
    }
}
