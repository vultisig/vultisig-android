@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.MergeAccount
import com.vultisig.wallet.data.mappers.ChainAndTokens
import com.vultisig.wallet.data.mappers.ChainAndTokensToAddressMapper
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenBalance
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.isDeFiSupported
import com.vultisig.wallet.data.models.settings.AppCurrency
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import kotlin.collections.component1
import kotlin.collections.component2

interface AccountsRepository {
    fun loadAddresses(
        vaultId: String,
        isRefresh: Boolean = false,
    ): Flow<List<Address>>

    fun loadCachedAddresses(
        vaultId: String,
    ): Flow<List<Address>>

    fun loadAddress(
        vaultId: String,
        chain: Chain,
    ): Flow<Address>

    suspend fun loadAccount(
        vaultId: String,
        token: Coin
    ): Account

    suspend fun fetchMergeBalance(chain: Chain, vaultId: String): List<MergeAccount>

    suspend fun loadDeFiAddresses(vaultId: String, isRefresh: Boolean): Flow<List<Address>>
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
    private fun getVaultAsFlow(vaultId: String): Flow<Vault> =
        vaultRepository.getAsFlow(vaultId).filterNotNull()

    override fun loadAddresses(vaultId: String, isRefresh: Boolean): Flow<List<Address>> = buildCacheAddresses(vaultId).flatMapLatest { (vaultCoins, addresses )->
        channelFlow {
            supervisorScope {
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
                                            balanceRepository.getTokenBalanceAndPrice(address, it.token)
                                                .first()

                                        it.applyBalance(balance.tokenBalance, balance.price)
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
    }

    override fun loadCachedAddresses(vaultId: String): Flow<List<Address>> =  buildCacheAddresses(vaultId)
        .flatMapLatest { cachedAddress ->
            flow {
                val addresses = cachedAddress.addresses
                addresses.fetchAccountFromDb()
                emit(addresses)
            }
        }


    private fun buildCacheAddresses(vaultId: String): Flow<CachedAddresses> {
        return getVaultAsFlow(vaultId).map { vault ->
            val vaultCoins = vault.coins

            val coins = vaultCoins.groupBy { it.chain }
            val addresses = coins.mapNotNullTo(mutableListOf()) { (chain, tokens) ->
                chainAndTokensToAddressMapper.map(ChainAndTokens(chain, tokens))
            }

            CachedAddresses(
                vaultCoins = vaultCoins,
                addresses = addresses
            )
        }
    }

    private suspend fun MutableList<Address>.fetchAccountFromDb() {
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
            val disabledCoinIds = vaultRepository.getDisabledCoinIds(vaultId = vault.id)
            splTokens.forEach { spl ->
                if (!solanaCoins.any { it.id == spl.id } && disabledCoinIds.none { it == spl.id }) {
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

        val address = account.address

        emit(
            account.copy(
                accounts = account.accounts.map {
                    val balance = balanceRepository.getCachedTokenBalanceAndPrice(
                        address,
                        it.token,
                    )

                    it.applyBalance(balance.tokenBalance, balance.price)
                }
            )
        )

        val loadPrices = supervisorScope {
            async { tokenPriceRepository.refresh(updatedCoins) }
        }


        loadPrices.await()

        emit(
            account.copy(
                accounts = account.accounts.map {
                    val balance = balanceRepository.getTokenBalanceAndPrice(address, it.token)
                        .first()

                    it.applyBalance(balance.tokenBalance, balance.price)
                }
            ))
    }.map {
        it.distinctByChainAndContractAddress()
    }

    override suspend fun fetchMergeBalance(chain: Chain, vaultId: String): List<MergeAccount> {
        if (chain == Chain.ThorChain) {
            val address = vaultRepository.get(vaultId)
                ?.coins
                ?.firstOrNull { it.chain == chain }
                ?.address
                ?: return emptyList()

            return runCatching { balanceRepository.getMergeTokenValue(address, chain) }.getOrNull()
                ?: emptyList()
        }
        return emptyList()
    }

    override suspend fun loadDeFiAddresses(
        vaultId: String,
        isRefresh: Boolean
    ): Flow<List<Address>> = channelFlow {
        val vault = getVault(vaultId)
        val defiCoins = vault.coins.filter { it.isValidForDeFi() }
            .distinctBy { it.id.lowercase() }

        val loadPrices = if (isRefresh) {
            async { tokenPriceRepository.refresh(defiCoins) }
        } else {
            null
        }

        val coins = defiCoins.groupBy { it.chain }
        val addresses = coins.mapNotNullTo(mutableListOf()) { (chain, tokens) ->
            chainAndTokensToAddressMapper.map(ChainAndTokens(chain, tokens))
        }

        // emit cached
        try {
            val thorchainAddress = addresses.find { it.chain == Chain.ThorChain }
            val ethereumAddresses = addresses.find { it.chain == Chain.Ethereum }

            if (thorchainAddress != null) {
                val cachedDeFiBalances = balanceRepository.getDeFiCachedTokeBalanceAndPrice(
                    address = thorchainAddress.address,
                    vaultId = vaultId,
                )

                if (cachedDeFiBalances.isNotEmpty()) {
                    val balancesByTicker = cachedDeFiBalances.associateBy { balance ->
                        balance.tokenBalance.tokenValue?.unit?.lowercase()
                    }

                    val cachedAddresses = addresses.map { address ->
                        val updatedAccounts = address.accounts.map { account ->
                            val cachedBalance = balancesByTicker[account.token.ticker.lowercase()]
                            if (cachedBalance != null) {
                                account.applyBalance(
                                    cachedBalance.tokenBalance,
                                    cachedBalance.price
                                )
                            } else {
                                account.copy(
                                    tokenValue = TokenValue(
                                        value = BigInteger.ZERO,
                                        unit = account.token.ticker,
                                        decimals = account.token.decimal
                                    ),
                                    fiatValue = FiatValue(
                                        value = BigDecimal.ZERO,
                                        currency = AppCurrency.USD.ticker,
                                    ),
                                    price = null
                                )
                            }
                        }
                        address.copy(accounts = updatedAccounts)
                    }

                    send(cachedAddresses)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load cached DeFi balances")
        }

        if (!isRefresh) {
            return@channelFlow
        }

        loadPrices?.await()

        // emit network
        val updated = addresses.map { account ->
            async {
                try {
                    val address = account.address
                    val newAccounts =
                        account.accounts.map {
                            async {
                                val balance =
                                    balanceRepository.getDefiTokenBalanceAndPrice(
                                        address = address,
                                        coin = it.token,
                                        vaultId = vaultId,
                                    ).first()

                                it.applyBalance(balance.tokenBalance, balance.price)
                            }
                        }.awaitAll()

                    account.copy(accounts = newAccounts)
                } catch (e: Exception) {
                    Timber.e(e)
                    null
                }
            }
        }.awaitAll().filterNotNull()

        send(updated)
    }

    override suspend fun loadAccount(vaultId: String, token: Coin): Account = coroutineScope {
        val vault = getVault(vaultId)
        val chain = token.chain
        val vaultCoins = vault.coins.filter { it.chain == chain }
        val nativeCoin = vaultCoins.find { it.isNativeToken }
            ?: error("Missing native token for chain: $chain")

        val (coins, updatedToken) = if (token.isNativeToken) {
            listOf(nativeCoin) to nativeCoin
        } else {
            val updatedCoin =
                vaultCoins.firstOrNull { it.id.equals(token.id, true) } ?: token
            listOf(nativeCoin, updatedCoin) to updatedCoin
        }

        val finalAccount = chainAndTokensToAddressMapper
            .map(ChainAndTokens(chain, coins))
            ?: error("Failed to map address for chain: $chain with coins: $coins")

        runCatching {
            tokenPriceRepository.refresh(coins)
        }.onFailure {
            Timber.e(it, "Failed to refresh token prices for chain: $chain")
        }

        val accountToUpdate = finalAccount.accounts
            .firstOrNull { it.token.id == updatedToken.id }
            ?: error("Account for token ${updatedToken.id} not found in mapped address")

        val balance = balanceRepository
            .getTokenBalanceAndPrice(finalAccount.address, updatedToken)
            .first()

        accountToUpdate.applyBalance(balance.tokenBalance, balance.price)
    }

    private fun Account.applyBalance(balance: TokenBalance): Account = copy(
        tokenValue = balance.tokenValue,
        fiatValue = balance.fiatValue,
    )

    private fun Account.applyBalance(balance: TokenBalance, price: FiatValue?): Account = copy(
        tokenValue = balance.tokenValue,
        fiatValue = balance.fiatValue,
        price = price
    )

    private fun Address.distinctByChainAndContractAddress() = copy(
        accounts = accounts.distinctBy { account ->
            account.token.chain.id to account.token.contractAddress.lowercase()
        }
    )

    private fun Coin.isValidForDeFi(): Boolean {
        return chain.isDeFiSupported || (ticker == "USDC" && chain == Chain.Ethereum)
    }
}

private data class CachedAddresses(
    val vaultCoins: List<Coin>,
    val addresses: MutableList<Address>
)