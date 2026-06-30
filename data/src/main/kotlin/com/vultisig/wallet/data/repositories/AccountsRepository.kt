@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.models.thorchain.MergeAccount
import com.vultisig.wallet.data.mappers.ChainAndTokens
import com.vultisig.wallet.data.mappers.ChainAndTokensToAddressMapper
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenBalance
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.isDeFiSupported
import com.vultisig.wallet.data.models.settings.AppCurrency
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import kotlin.collections.component1
import kotlin.collections.component2
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.supervisorScope
import timber.log.Timber

/**
 * Progressive result of a balance load: [addresses] is the latest snapshot and [isComplete] is true
 * only on the terminal emission, once every chain has resolved (or failed). Lets callers that care
 * about the load lifecycle — e.g. the pull-to-refresh spinner — know when the refresh is done,
 * which a plain `Flow<List<Address>>` cannot express because the stream stays open.
 */
data class AddressBalancesUpdate(val addresses: List<Address>, val isComplete: Boolean)

interface AccountsRepository {
    fun loadAddresses(vaultId: String): Flow<List<Address>>

    /**
     * Same per-chain streaming load as [loadAddresses] but each emission also carries whether the
     * whole load has finished. [loadAddresses] is implemented on top of this, dropping the terminal
     * completion emission since it only repeats the last snapshot.
     */
    fun loadAddressBalances(vaultId: String): Flow<AddressBalancesUpdate>

    fun loadCachedAddresses(vaultId: String): Flow<List<Address>>

    fun loadAddress(vaultId: String, chain: Chain): Flow<Address>

    fun loadCachedAddress(vaultId: String, chain: Chain): Flow<Address>

    suspend fun loadAccount(vaultId: String, token: Coin): Account

    suspend fun fetchMergeBalance(chain: Chain, vaultId: String): List<MergeAccount>

    suspend fun loadDeFiAddresses(vaultId: String, isRefresh: Boolean): Flow<List<Address>>
}

internal class AccountsRepositoryImpl
@Inject
constructor(
    private val vaultRepository: VaultRepository,
    private val balanceRepository: BalanceRepository,
    private val tokenPriceRepository: TokenPriceRepository,
    private val chainAndTokensToAddressMapper: ChainAndTokensToAddressMapper,
    private val splTokenRepository: SplTokenRepository,
) : AccountsRepository {

    private suspend fun getVault(vaultId: String): Vault =
        checkNotNull(vaultRepository.get(vaultId)) { "No vault with id $vaultId" }

    private fun getVaultAsFlow(vaultId: String): Flow<Vault> =
        vaultRepository.getAsFlow(vaultId).filterNotNull()

    // Drop the terminal completion emission for plain List consumers: it only repeats the last
    // per-chain snapshot, so re-rendering it would be redundant. Lifecycle-aware callers use
    // loadAddressBalances instead.
    override fun loadAddresses(vaultId: String): Flow<List<Address>> =
        loadAddressBalances(vaultId).filter { !it.isComplete }.map { it.addresses }

    override fun loadAddressBalances(vaultId: String): Flow<AddressBalancesUpdate> =
        buildCacheAddresses(vaultId).flatMapLatest { (vaultCoins, addresses) ->
            channelFlow {
                supervisorScope {
                    val loadPrices = async { tokenPriceRepository.refresh(vaultCoins) }

                    // Always emit the last-known DB snapshot first so the UI shows cached
                    // balances immediately instead of empty rows — including on refresh
                    // (e.g. right after adding a chain).
                    addresses.fetchAccountFromDb()
                    send(AddressBalancesUpdate(addresses.toList(), isComplete = false))

                    addresses
                        .mapIndexed { index, account ->
                            async {
                                try {
                                    val address = account.address

                                    // Don't gate balances on the price refresh: prices come from an
                                    // in-memory StateFlow that resolves instantly (cached/zero), so
                                    // awaiting the refresh here only delayed balances for chains
                                    // that need no pricing. Fiat is recomputed below once prices
                                    // land. (Desktop decouples these the same way.)
                                    val newAccounts =
                                        if (account.chain.standard == TokenStandard.EVM) {
                                            // One Multicall3 round-trip per (chain, address) for
                                            // all balances, instead of N+1 eth_calls fanned out
                                            // per token. Falls back per-token inside the repo on
                                            // chains without a canonical Multicall3.
                                            val balancesAndPrices =
                                                balanceRepository.getEvmTokenBalancesAndPrices(
                                                    address,
                                                    account.accounts.map { it.token },
                                                )
                                            account.accounts.map { acc ->
                                                balancesAndPrices[acc.token.id]?.let { balance ->
                                                    acc.applyBalance(
                                                        balance.tokenBalance,
                                                        balance.price,
                                                    )
                                                } ?: acc
                                            }
                                        } else {
                                            supervisorScope {
                                                account.accounts
                                                    .map {
                                                        async {
                                                            val balance =
                                                                balanceRepository
                                                                    .getTokenBalanceAndPrice(
                                                                        address,
                                                                        it.token,
                                                                    )
                                                                    .first()

                                                            it.applyBalance(
                                                                balance.tokenBalance,
                                                                balance.price,
                                                            )
                                                        }
                                                    }
                                                    .awaitAll()
                                            }
                                        }

                                    addresses[index] = account.copy(accounts = newAccounts)
                                    // Stream each chain's balance as soon as it resolves rather
                                    // than waiting for every chain to finish (Windows streams the
                                    // same way; iOS instead batches all balances and applies them
                                    // at once). Emit a snapshot copy because other chains may still
                                    // be mutating the backing list in parallel.
                                    send(
                                        AddressBalancesUpdate(
                                            addresses.toList(),
                                            isComplete = false,
                                        )
                                    )
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    Timber.e(e)
                                    // ignore
                                }
                            }
                        }
                        .awaitAll()

                    // Balances are already streamed; now wait for the price refresh and recompute
                    // fiat from the freshly persisted prices. On a warm start the StateFlow already
                    // held prices, so the per-chain emissions were already correct and this is a
                    // no-op confirmation; only a cold start (empty StateFlow) updates fiat here.
                    // Guard both the price await and the cache recompute: a failure in either must
                    // not skip the terminal emission below, or the UI's refresh spinner would hang
                    // (it only stops on isComplete). On failure we keep the already-streamed
                    // balances with their last-known fiat.
                    try {
                        loadPrices.await()
                        addresses.recomputeFiatFromFreshPrices()
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Timber.e(
                            e,
                            "Price refresh/fiat recompute failed; emitting last-known balances",
                        )
                    }

                    // Terminal emission: every chain has resolved (or failed). Carries the final
                    // snapshot and flags completion so the UI can stop the refresh spinner.
                    send(AddressBalancesUpdate(addresses.toList(), isComplete = true))
                }
                awaitClose()
            }
        }

    override fun loadCachedAddresses(vaultId: String): Flow<List<Address>> =
        buildCacheAddresses(vaultId).flatMapLatest { cachedAddress ->
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
            val addresses =
                coins.mapNotNullTo(mutableListOf()) { (chain, tokens) ->
                    chainAndTokensToAddressMapper.map(ChainAndTokens(chain, tokens))
                }

            CachedAddresses(vaultCoins = vaultCoins, addresses = addresses)
        }
    }

    private suspend fun MutableList<Address>.fetchAccountFromDb() {
        val balances =
            balanceRepository.getCachedTokenBalances(
                this.map { adr -> adr.address },
                this.flatMap { adr -> adr.accounts.map { it.token } },
            )

        mapIndexed { index, account ->
            val newAccounts =
                account.accounts.map { acc ->
                    val balance =
                        balances.firstOrNull {
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

    /**
     * Recomputes each account's fiat value and unit price from the now-refreshed prices, reusing
     * the Room-backed [BalanceRepository.getCachedTokenBalanceAndPrice] read (the freshly fetched
     * balance was already persisted there, and so is the fresh price) so no extra network calls are
     * made. Accounts with no cached balance (e.g. a brand-new coin whose network fetch failed) are
     * left untouched so a streamed value is never overwritten with null.
     */
    private suspend fun MutableList<Address>.recomputeFiatFromFreshPrices() {
        forEachIndexed { index, account ->
            val newAccounts =
                account.accounts.map { acc ->
                    val balance =
                        balanceRepository.getCachedTokenBalanceAndPrice(account.address, acc.token)
                    if (balance.tokenBalance.tokenValue != null) {
                        acc.applyBalance(balance.tokenBalance, balance.price)
                    } else {
                        acc
                    }
                }
            this[index] = account.copy(accounts = newAccounts)
        }
    }

    private suspend fun getSPLCoins(solanaCoins: List<Coin>, vault: Vault): List<Coin> {
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

    override fun loadAddress(vaultId: String, chain: Chain): Flow<Address> =
        flow {
                val vault = getVault(vaultId)
                val coins = vault.coins.filter { it.chain == chain }

                var account =
                    chainAndTokensToAddressMapper.map(ChainAndTokens(chain, coins)) ?: return@flow

                emit(account)

                val updatedCoins =
                    if (chain == Chain.Solana) coins + getSPLCoins(coins, vault) else coins

                account =
                    chainAndTokensToAddressMapper.map(ChainAndTokens(chain, updatedCoins))
                        ?: return@flow

                emit(account)

                emitCachedAddress(account)

                coroutineScope {
                    // Fetch the network balance in parallel with the price refresh instead of
                    // gating it behind the refresh; emitRefreshAddress already shows the balance
                    // using whatever price the StateFlow currently holds. The refresh is caught
                    // inside the async: an uncaught failure here would cancel this coroutineScope
                    // and propagate out (catching await() alone is not enough), failing the flow.
                    val loadPrices = async {
                        try {
                            tokenPriceRepository.refresh(updatedCoins)
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            Timber.e(e, "Price refresh failed for ${chain.id}")
                        }
                    }

                    emitRefreshAddress(account)

                    loadPrices.await()

                    // Re-emit from the cache so fiat reflects the freshly persisted balance and
                    // price (no extra network call). On a warm start this just confirms the values
                    // emitRefreshAddress already produced.
                    emitCachedAddress(account)
                }
            }
            .map { it.distinctByChainAndContractAddress() }

    override fun loadCachedAddress(vaultId: String, chain: Chain): Flow<Address> =
        flow {
                val vault = getVault(vaultId)
                val coins = vault.coins.filter { it.chain == chain }

                val account =
                    chainAndTokensToAddressMapper.map(ChainAndTokens(chain, coins)) ?: return@flow
                emitCachedAddress(account)
            }
            .map { it.distinctByChainAndContractAddress() }

    private suspend fun FlowCollector<Address>.emitRefreshAddress(address: Address) {
        val tokenAddress = address.address
        emit(
            address.copy(
                accounts =
                    address.accounts.map {
                        val balance =
                            balanceRepository
                                .getTokenBalanceAndPrice(tokenAddress, it.token)
                                .first()

                        it.applyBalance(balance.tokenBalance, balance.price)
                    }
            )
        )
    }

    private suspend fun FlowCollector<Address>.emitCachedAddress(address: Address) {

        val tokenAddress = address.address

        emit(
            address.copy(
                accounts =
                    address.accounts.map {
                        val balance =
                            balanceRepository.getCachedTokenBalanceAndPrice(tokenAddress, it.token)

                        it.applyBalance(balance.tokenBalance, balance.price)
                    }
            )
        )
    }

    override suspend fun fetchMergeBalance(chain: Chain, vaultId: String): List<MergeAccount> {
        if (chain == Chain.ThorChain) {
            val address =
                vaultRepository.get(vaultId)?.coins?.firstOrNull { it.chain == chain }?.address
                    ?: return emptyList()

            return runCatching { balanceRepository.getMergeTokenValue(address, chain) }.getOrNull()
                ?: emptyList()
        }
        return emptyList()
    }

    override suspend fun loadDeFiAddresses(
        vaultId: String,
        isRefresh: Boolean,
    ): Flow<List<Address>> = channelFlow {
        val vault = getVault(vaultId)
        val defiCoins = vault.coins.filter { it.isValidForDeFi() }.distinctBy { it.id.lowercase() }

        val loadPrices =
            if (isRefresh) {
                async { tokenPriceRepository.refresh(defiCoins) }
            } else {
                null
            }

        val coins = defiCoins.groupBy { it.chain }
        val addresses =
            coins.mapNotNullTo(mutableListOf()) { (chain, tokens) ->
                chainAndTokensToAddressMapper.map(ChainAndTokens(chain, tokens))
            }

        // emit cached
        try {
            val defiChainNativeCoins =
                listOf(
                    Chain.ThorChain to Coins.ThorChain.RUNE,
                    Chain.Ethereum to Coins.Ethereum.ETH,
                    Chain.MayaChain to Coins.MayaChain.CACAO,
                    Chain.Tron to Coins.Tron.TRX,
                    Chain.Terra to Coins.Terra.LUNA,
                    Chain.TerraClassic to Coins.TerraClassic.LUNC,
                    Chain.Qbtc to Coins.Qbtc.QBTC,
                )

            val addressesByChain = addresses.associateBy { it.chain }
            val cacheBalances =
                defiChainNativeCoins.flatMap { (chain, nativeCoin) ->
                    addressesByChain[chain]?.let { address ->
                        balanceRepository.getDeFiCachedTokeBalanceAndPrice(
                            address = address.address,
                            coin = nativeCoin,
                            vaultId = vaultId,
                        )
                    } ?: emptyList()
                }

            if (cacheBalances.isNotEmpty()) {
                val balancesByTicker =
                    cacheBalances.associateBy { balance ->
                        balance.tokenBalance.tokenValue?.unit?.lowercase()
                    }

                val cachedAddresses =
                    addresses.map { address ->
                        val updatedAccounts =
                            address.accounts.map { account ->
                                val cachedBalance =
                                    balancesByTicker[account.token.ticker.lowercase()]
                                if (cachedBalance != null) {
                                    account.applyBalance(
                                        cachedBalance.tokenBalance,
                                        cachedBalance.price,
                                    )
                                } else {
                                    account.copy(
                                        tokenValue =
                                            TokenValue(
                                                value = BigInteger.ZERO,
                                                unit = account.token.ticker,
                                                decimals = account.token.decimal,
                                            ),
                                        fiatValue =
                                            FiatValue(
                                                value = BigDecimal.ZERO,
                                                currency = AppCurrency.USD.ticker,
                                            ),
                                        price = null,
                                    )
                                }
                            }
                        val canBeDeFiProvider = address.chain.isDeFiSupported

                        address.copy(accounts = updatedAccounts, isDefiProvider = canBeDeFiProvider)
                    }

                send(cachedAddresses)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "Failed to load cached DeFi balances")
        }

        if (!isRefresh) {
            return@channelFlow
        }

        loadPrices?.await()

        // emit network
        val updated =
            addresses
                .map { account ->
                    async {
                        try {
                            val address = account.address
                            val newAccounts =
                                account.accounts
                                    .map {
                                        async {
                                            val balance =
                                                balanceRepository
                                                    .getDefiTokenBalanceAndPrice(
                                                        address = address,
                                                        coin = it.token,
                                                        vaultId = vaultId,
                                                    )
                                                    .first()

                                            it.applyBalance(balance.tokenBalance, balance.price)
                                        }
                                    }
                                    .awaitAll()
                            val canBeDeFiProvider = account.chain.isDeFiSupported

                            account.copy(accounts = newAccounts, isDefiProvider = canBeDeFiProvider)
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            Timber.e(e)
                            null
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()

        send(updated)
    }

    override suspend fun loadAccount(vaultId: String, token: Coin): Account = coroutineScope {
        val vault = getVault(vaultId)
        val chain = token.chain
        val vaultCoins = vault.coins.filter { it.chain == chain }
        val nativeCoin =
            vaultCoins.find { it.isNativeToken } ?: error("Missing native token for chain: $chain")

        val (coins, updatedToken) =
            if (token.isNativeToken) {
                listOf(nativeCoin) to nativeCoin
            } else {
                val updatedCoin = vaultCoins.firstOrNull { it.id.equals(token.id, true) } ?: token
                listOf(nativeCoin, updatedCoin) to updatedCoin
            }

        val finalAccount =
            chainAndTokensToAddressMapper.map(ChainAndTokens(chain, coins))
                ?: error("Failed to map address for chain: $chain with coins: $coins")

        // Refresh prices and fetch the balance concurrently (wall-clock max of the two, not the
        // sum) instead of awaiting prices first. The balance fetch persists the value to the Room
        // cache, so once prices land we recompute fiat from the cache without a second network
        // call.
        val loadPrices = async {
            runCatching { tokenPriceRepository.refresh(coins) }
                .onFailure { Timber.e(it, "Failed to refresh token prices for chain: $chain") }
        }

        val accountToUpdate =
            finalAccount.accounts.firstOrNull { it.token.id == updatedToken.id }
                ?: error("Account for token ${updatedToken.id} not found in mapped address")

        val balance =
            balanceRepository.getTokenBalanceAndPrice(finalAccount.address, updatedToken).first()

        loadPrices.await()

        val refreshed =
            balanceRepository.getCachedTokenBalanceAndPrice(finalAccount.address, updatedToken)
        if (refreshed.tokenBalance.tokenValue != null) {
            accountToUpdate.applyBalance(refreshed.tokenBalance, refreshed.price)
        } else {
            accountToUpdate.applyBalance(balance.tokenBalance, balance.price)
        }
    }

    private fun Account.applyBalance(balance: TokenBalance): Account =
        copy(tokenValue = balance.tokenValue, fiatValue = balance.fiatValue)

    private fun Account.applyBalance(balance: TokenBalance, price: FiatValue?): Account =
        copy(tokenValue = balance.tokenValue, fiatValue = balance.fiatValue, price = price)

    private fun Address.distinctByChainAndContractAddress() =
        copy(
            accounts =
                accounts.distinctBy { account ->
                    account.token.chain.id to account.token.contractAddress.lowercase()
                }
        )

    private fun Coin.isValidForDeFi(): Boolean {
        return when (this.chain) {
            Chain.ThorChain -> true
            Chain.Ethereum -> true
            Chain.MayaChain -> true
            Chain.Tron -> true
            // LUNA / LUNC / QBTC surface a staking DeFi position. Without these their coins are
            // dropped from the candidate set, so they pass isDeFiSupported (selectable in the
            // "Select DeFi chains" sheet) yet never appear in the Portfolio DeFi list.
            Chain.Terra -> true
            Chain.TerraClassic -> true
            Chain.Qbtc -> true
            else -> false
        }
    }
}

private data class CachedAddresses(val vaultCoins: List<Coin>, val addresses: MutableList<Address>)
