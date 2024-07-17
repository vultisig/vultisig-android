package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.mappers.ChainAndTokens
import com.vultisig.wallet.data.mappers.ChainAndTokensToAddressMapper
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.TokenBalance
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Vault
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
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
    private val vaultRepository: VaultRepository,
    private val balanceRepository: BalanceRepository,
    private val tokenPriceRepository: TokenPriceRepository,
    private val chainAndTokensToAddressMapper: ChainAndTokensToAddressMapper,
) : AccountsRepository {

    private suspend fun getVault(vaultId: String): Vault =
        checkNotNull(vaultRepository.get(vaultId)) {
            "No vault with id $vaultId"
        }

    override fun loadAddresses(vaultId: String): Flow<List<Address>> = channelFlow {
        val vault = getVault(vaultId)
        val vaultCoins = vault.coins
        val coins = vaultCoins.groupBy { it.chain }

        val addresses = coins.mapTo(mutableListOf()) { (chain, coins) ->
            chainAndTokensToAddressMapper.map(ChainAndTokens(chain, coins))
        }

        val loadPrices = supervisorScope {
            async { tokenPriceRepository.refresh(vaultCoins) }
        }

        coroutineScope {
            addresses.mapIndexed { index, account ->
                async {
                    val address = account.address

                    val cachedAccounts = coroutineScope {
                        account.accounts.map { acc ->
                            async {
                                val balance = balanceRepository.getCachedTokenBalance(
                                    address,
                                    acc.token,
                                )

                                acc.applyBalance(balance)
                            }
                        }.awaitAll()
                    }

                    addresses[index] = account.copy(accounts = cachedAccounts)

                }
            }.awaitAll()
            send(addresses)
        }

        coroutineScope {
            addresses.mapIndexed { index, account ->
                async {
                    try {
                        val address = account.address
                        loadPrices.await()

                        val newAccounts = supervisorScope {
                            account.accounts.map {
                                async {
                                    val balance = balanceRepository.getTokenBalance(address, it.token)
                                        .first()

                                    it.applyBalance(balance)
                                }
                            }.awaitAll()
                        }

                        addresses[index] = account.copy(accounts = newAccounts)

                    } catch (e: Exception) {
                        Timber.e(e)
                        // ignore
                    }
                }
            }.awaitAll()
            send(addresses)
        }
        awaitClose()
    }

    override fun loadAddress(
        vaultId: String,
        chain: Chain,
    ): Flow<Address> = flow {
        val vault = getVault(vaultId)
        val coins = vault.coins.filter { it.chain == chain }

        val account = chainAndTokensToAddressMapper.map(ChainAndTokens(chain, coins))

        emit(account)

        val loadPrices = supervisorScope {
            async { tokenPriceRepository.refresh(coins) }
        }

        val address = account.address

        emit(
            account.copy(
                accounts = account.accounts.map {
                    val balance = balanceRepository.getCachedTokenBalance(
                        address,
                        it.token,
                    )

                    it.applyBalance(balance)
                }
            )
        )

        loadPrices.await()

        emit(account.copy(
            accounts = account.accounts.map {
                val balance = balanceRepository.getTokenBalance(address, it.token)
                    .first()

                it.applyBalance(balance)
            }
        ))
    }

    private fun Account.applyBalance(balance: TokenBalance): Account = copy(
        tokenValue = balance.tokenValue,
        fiatValue = balance.fiatValue,
    )

}