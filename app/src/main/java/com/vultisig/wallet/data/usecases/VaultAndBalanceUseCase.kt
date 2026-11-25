package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.HomeBalanceDisplay
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.calculateAddressesTotalFiatValue
import com.vultisig.wallet.data.models.combinedWalletAndDeFiTotal
import com.vultisig.wallet.data.models.deFiNotInWalletTotal
import com.vultisig.wallet.data.models.selectedDeFiAddresses
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.DefaultDeFiChainsRepository
import com.vultisig.wallet.data.repositories.HomeDisplayRepository
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import javax.inject.Inject
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import timber.log.Timber

internal data class VaultAndBalance(
    val vault: Vault,
    val balance: String?,
    val balanceFiatValue: FiatValue?,
)

/**
 * A vault with its cached balance under the current [HomeBalanceDisplay]: the wallet total, plus
 * the DeFi value not held in the wallet when [HomeBalanceDisplay.Combined]. Reads caches only; a
 * DeFi side with nothing cached, or whose cache read fails, adds nothing, as on the header.
 */
internal interface VaultAndBalanceUseCase : suspend (Vault) -> VaultAndBalance

internal class VaultAndBalanceUseCaseImpl
@Inject
constructor(
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val accountsRepository: AccountsRepository,
    private val homeDisplayRepository: HomeDisplayRepository,
    private val defaultDeFiChainsRepository: DefaultDeFiChainsRepository,
    private val hasCircleAccount: HasCircleAccountUseCase,
) : VaultAndBalanceUseCase {
    override suspend fun invoke(vault: Vault): VaultAndBalance {
        val wallet =
            accountsRepository
                .loadCachedAddresses(vault.id)
                .map { addresses -> addresses.calculateAddressesTotalFiatValue() }
                .firstOrNull()
        val balance =
            when (homeDisplayRepository.balanceDisplay.first()) {
                HomeBalanceDisplay.Separate -> wallet
                HomeBalanceDisplay.Combined ->
                    combinedWalletAndDeFiTotal(wallet, cachedDeFiNotInWalletTotal(vault.id))
            }
        return VaultAndBalance(
            vault = vault,
            balance = balance?.let { fiatValueToStringMapper(it) },
            balanceFiatValue = balance,
        )
    }

    private suspend fun cachedDeFiNotInWalletTotal(vaultId: VaultId): FiatValue? {
        val addresses =
            accountsRepository
                .loadDeFiAddresses(vaultId, isRefresh = false)
                .catch { error ->
                    Timber.e(error, "Error loading cached DeFi balances for vault: %s", vaultId)
                }
                .firstOrNull() ?: return null
        return addresses
            .selectedDeFiAddresses(
                selectedChains = defaultDeFiChainsRepository.getDefaultChains(vaultId).first(),
                hasCircleAccount = hasCircleAccount(vaultId),
            )
            .deFiNotInWalletTotal()
    }
}
