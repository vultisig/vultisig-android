package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.mappers.ChainAndTokens
import com.vultisig.wallet.data.mappers.ChainAndTokensToAddressMapper
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Vault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

internal interface AccountsRepository {

    fun loadAddresses(
        vaultId: String,
    ): Flow<List<Address>>

    fun loadAddress(
        vaultId: String,
        chain: Chain,
    ): Flow<Address>

}

internal class AccountsRepositoryImpl @Inject constructor(
    private val vaultDb: VaultDB,
    private val balanceRepository: BalanceRepository,
    private val tokenPriceRepository: TokenPriceRepository,
    private val chainAndTokensToAddressMapper: ChainAndTokensToAddressMapper,
) : AccountsRepository {

    private fun getVault(vaultId: String): Vault = checkNotNull(vaultDb.select(vaultId)) {
        "No vault with id $vaultId"
    }

    override fun loadAddresses(vaultId: String): Flow<List<Address>> = flow {
        val vault = getVault(vaultId)
        val vaultCoins = vault.coins
        val coins = vaultCoins.groupBy { it.chain }

        val addresses = coins.mapTo(mutableListOf()) { (chain, coins) ->
            chainAndTokensToAddressMapper.map(ChainAndTokens(chain, coins))
        }

        emit(addresses)

        tokenPriceRepository.refresh(vaultCoins.map { it.priceProviderID })

        addresses.forEachIndexed { index, account ->
            val address = account.address

            addresses[index] = account.copy(
                accounts = account.accounts.map {
                    it.fetchAndUpdateBalance(address)
                }
            )

            emit(addresses)
        }
    }

    override fun loadAddress(
        vaultId: String,
        chain: Chain,
    ): Flow<Address> = flow {
        val vault = getVault(vaultId)
        val coins = vault.coins.filter { it.chain == chain }

        val account = chainAndTokensToAddressMapper.map(ChainAndTokens(chain, coins))

        emit(account)

        tokenPriceRepository.refresh(coins.map { it.priceProviderID })

        val address = account.address

        emit(account.copy(
            accounts = account.accounts.map {
                it.fetchAndUpdateBalance(address)
            }
        ))
    }

    private suspend fun Account.fetchAndUpdateBalance(address: String): Account {
        val balance = balanceRepository.getTokenBalance(address, token)
            .first()

        return copy(
            tokenValue = balance.tokenValue,
            fiatValue = balance.fiatValue,
        )
    }

}