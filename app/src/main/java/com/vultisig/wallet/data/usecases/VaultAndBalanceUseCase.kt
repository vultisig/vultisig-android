package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.calculateAddressesTotalFiatValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

internal data class VaultAndBalance(
    val vault: Vault,
    val balance: String?,
    val balanceFiatValue: FiatValue?,
)

internal interface VaultAndBalanceUseCase : suspend (Vault) -> VaultAndBalance

internal class VaultAndBalanceUseCaseImpl
@Inject
constructor(
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val accountsRepository: AccountsRepository,
) : VaultAndBalanceUseCase {
    override suspend fun invoke(vault: Vault): VaultAndBalance {
        val balance =
            accountsRepository
                .loadCachedAddresses(vault.id)
                .map { addresses -> addresses.calculateAddressesTotalFiatValue() }
                .firstOrNull()
        return VaultAndBalance(
            vault = vault,
            balance = balance?.let { fiatValueToStringMapper(it) },
            balanceFiatValue = balance,
        )
    }
}
