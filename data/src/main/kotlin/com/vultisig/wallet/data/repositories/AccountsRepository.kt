package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.mappers.ChainAndTokens
import com.vultisig.wallet.data.mappers.ChainAndTokensToAddressMapper
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenBalance
import com.vultisig.wallet.data.models.Vault
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import javax.inject.Inject

interface AccountsRepository {

    fun loadAddresses(
        vaultId: String,
        isRefresh: Boolean = false,
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
    private val splTokenRepository: SplTokenRepository,
) : AccountsRepository {

    private suspend fun getVault(vaultId: String): Vault =
        checkNotNull(vaultRepository.get(vaultId)) {
            "No vault with id $vaultId"
        }

    override fun loadAddresses(vaultId: String, isRefresh: Boolean): Flow<List<Address>> = channelFlow {
        supervisorScope {
            val vault = getVault(vaultId)
            val vaultCoins = vault.coins
            val coins = vaultCoins.groupBy { it.chain }
            val addresses = coins.mapNotNullTo(mutableListOf()) { (chain, coins) ->
                chainAndTokensToAddressMapper.map(ChainAndTokens(chain, coins))
            }
            val loadPrices =
                async { tokenPriceRepository.refresh(vaultCoins) }

            if (!isRefresh) {
                addresses.fetchAccountFromDb()
                send(addresses)
            }

            addresses.mapIndexed { index, account ->
                async {
                    try {
                        val address = account.address
                        loadPrices.await()

                        val newAccounts = supervisorScope {
                            account.accounts.map {
                                async {
                                    val balance =
                                        balanceRepository.getTokenBalance(address, it.token)
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

    private suspend fun MutableList<Address>.fetchAccountFromDb(){

            val balances = balanceRepository.getCachedTokenBalances(
                this.map { adr -> adr.address },
                this.map { adr -> adr.accounts.map { it.token } }.flatten()
            )

            mapIndexed { index, account ->
                val newAccounts = account.accounts.map { acc ->
                    val balance = balances.firstOrNull {
                        it.address == account.address && it.coinId == acc.token.id
                    }
                    if (balance != null) {
                        acc.applyBalance(balance.tokenBalance)
                    } else {
                        acc
                    }
                }
                this@fetchAccountFromDb[index] = account.copy(accounts = newAccounts)
            }

    }
    private suspend fun getSPLCoins(
        solanaCoins: List<Coin>,
        vault: Vault
    ): List<Coin> {
        if (solanaCoins.any { !it.isNativeToken }) return emptyList()
        val solanaAddress = solanaCoins.firstOrNull()?.address
        val newSPLTokens = mutableListOf<Coin>()
        solanaAddress?.let {
            val splTokens = splTokenRepository.getTokens(solanaAddress, vault)
            splTokens.forEach { spl ->
                if (!solanaCoins.any { it.id == spl.id }) {
                    vaultRepository.addTokenToVault(vault.id, spl)
                    newSPLTokens += spl
                }
            }
        }
        return newSPLTokens
    }

    override fun loadAddress(
        vaultId: String,
        chain: Chain,
    ): Flow<Address> = flow {
        val vault = getVault(vaultId)
        val coins = vault.coins.filter { it.chain == chain }

        var account = chainAndTokensToAddressMapper.map(ChainAndTokens(chain, coins))
            ?: return@flow

        emit(account)

        val updatedCoins = if (chain == Chain.Solana)
            coins + getSPLCoins(coins, vault)
        else coins

        account = chainAndTokensToAddressMapper.map(ChainAndTokens(chain, updatedCoins))
            ?: return@flow

        emit(account)

        val loadPrices = supervisorScope {
            async { tokenPriceRepository.refresh(updatedCoins) }
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