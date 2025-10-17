package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.crypto.ThorChainHelper
import com.vultisig.wallet.data.repositories.ThorChainRepository
import timber.log.Timber
import javax.inject.Inject

interface InitializeThorChainNetworkIdUseCase : suspend () -> Unit

internal class InitializeThorChainNetworkIdUseCaseImpl @Inject constructor(
    private val thorChainRepository: ThorChainRepository,
) : InitializeThorChainNetworkIdUseCase {

    override suspend fun invoke() {
        Timber.d("Initializing THORChain network id")

        val cachedNetworkChainId = thorChainRepository.getCachedNetworkChainId()
        if (!cachedNetworkChainId.isNullOrBlank()) {
            ThorChainHelper.THORCHAIN_NETWORK_ID = cachedNetworkChainId
            Timber.d("THORChain network id initialized from cache with: $cachedNetworkChainId")
        }

        try {
            ThorChainHelper.THORCHAIN_NETWORK_ID = thorChainRepository.fetchNetworkChainId()
            Timber.d("THORChain network id initialized with: ${ThorChainHelper.THORCHAIN_NETWORK_ID}")
        } catch (e: Exception) {
            Timber.e("Failed to fetch network chain id", e)
        }
    }

}