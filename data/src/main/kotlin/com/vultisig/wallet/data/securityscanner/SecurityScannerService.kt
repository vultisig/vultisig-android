package com.vultisig.wallet.data.securityscanner

import timber.log.Timber
import java.util.concurrent.ConcurrentSkipListSet

class SecurityScannerService(
    private val providers: List<ProviderScannerServiceContract>
) : SecurityScannerContract {
    private val disabledProvidersNames: MutableSet<String> = ConcurrentSkipListSet()

    override suspend fun scanTransaction(transaction: SecurityScannerTransaction): SecurityScannerResult {
        // For now we'll stick to first one (blockaid). Perform proper selection when having
        // multiple providers
        return providers.firstOrNull()?.scanTransaction(transaction)
            ?: run {
                val errorMessage =
                    "SecurityScanner: No provider available for scanning ${transaction.chain.name} tx"
                Timber.w(errorMessage)
                throw SecurityScannerException(
                    message = errorMessage,
                    chain = transaction.chain
                )
            }
    }

    override fun getDisabledProviders(): List<String> {
        return disabledProvidersNames.toList()
    }

    override fun getEnabledProviders(): List<String> {
        return providers.map { it.getProviderName() }.filter { it !in disabledProvidersNames }
    }

    override fun disableProviders(providersToDisable: List<String>) {
        val actualProviders = providers.map { it.getProviderName() }.toSet()
        val disabledCount = providersToDisable.count { providerName ->
            providerName in actualProviders && disabledProvidersNames.add(providerName)
        }
        if (disabledCount > 0) {
            Timber.i("SecurityScanner: Disabled $disabledCount providers.")
        } else {
            Timber.d("SecurityScanner: No new providers were disabled.")
        }
    }

    override fun enableProviders(providersToEnable: List<String>) {
        val enabledCount = providersToEnable.count { providerName ->
            disabledProvidersNames.remove(providerName)
        }
        if (enabledCount > 0) {
            Timber.i("SecurityScanner: Enabled $enabledCount providers.")
        } else {
            Timber.d("SecurityScanner: No new providers were enabled.")
        }
    }


    override fun getSupportedChainsByFeature(): List<SecurityScannerSupport> {
        return providers.map { provider ->
            val supportedFeaturesByChain = provider.getSupportedChains()

            val features = supportedFeaturesByChain.map { (featureType, chains) ->
                SecurityScannerSupport.Feature(
                    chains = chains,
                    featureType = featureType
                )
            }
            SecurityScannerSupport(
                provider = provider.getProviderName(),
                feature = features
            )
        }
    }
}