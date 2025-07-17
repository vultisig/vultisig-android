package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.models.SwapTransaction
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.repositories.OnChainSecurityScannerRepository
import timber.log.Timber
import java.util.concurrent.ConcurrentSkipListSet

class SecurityScannerService(
    private val providers: List<ProviderScannerServiceContract>,
    private val repository: OnChainSecurityScannerRepository,
    private val factory: SecurityScannerTransactionFactoryContract,
) : SecurityScannerContract {
    private val disabledProvidersNames: MutableSet<String> = ConcurrentSkipListSet()

    override suspend fun scanTransaction(transaction: SecurityScannerTransaction): SecurityScannerResult {
        val enabledProviders = providers.filter { it.getProviderName() !in disabledProvidersNames }
        return enabledProviders.firstOrNull()?.scanTransaction(transaction)
            ?: run {
                val errorMessage =
                    "SecurityScanner: No enabled provider available for scanning ${transaction.chain.name} tx"
                Timber.w(errorMessage)
                throw SecurityScannerException(
                    message = errorMessage,
                    chain = transaction.chain
                )
            }
    }

    override suspend fun isSecurityServiceEnabled(): Boolean {
        return repository.getSecurityScannerStatus()
    }

    override suspend fun createSecurityScannerTransaction(transaction: Transaction): SecurityScannerTransaction {
        return factory.createSecurityScannerTransaction(transaction)
    }

    override suspend fun createSecurityScannerTransaction(transaction: SwapTransaction): SecurityScannerTransaction {
        return factory.createSecurityScannerTransaction(transaction)
    }

    override fun getDisabledProviders(): List<String> {
        return disabledProvidersNames.toList()
    }

    override fun getEnabledProviders(): List<String> {
        return providers.map { it.getProviderName() }.filter { it !in disabledProvidersNames }
    }

    override fun disableProviders(providersToDisable: List<String>) {
        val actualProviders = providers.map { it.getProviderName() }.toSet()
        val validProviders = providersToDisable.filter { it in actualProviders }
        val invalidProviders = providersToDisable.filter { it !in actualProviders }
        if (invalidProviders.isNotEmpty()) {
            Timber.w("SecurityScanner: Invalid provider names: ${invalidProviders.joinToString()}")
        }

        val disabledCount = validProviders.count { providerName ->
            disabledProvidersNames.add(providerName)
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
            SecurityScannerSupport(
                provider = provider.getProviderName(),
                feature = provider.getSupportedChains().map { (featureType, chains) ->
                    SecurityScannerSupport.Feature(
                        chains = chains,
                        featureType = featureType
                    )
                }
            )
        }
    }
}