package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.data.blockchain.model.StakingDetails.Companion.generateId
import com.vultisig.wallet.data.blockchain.thorchain.RujiStakingService.Companion.RUJI_REWARDS_COIN
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import java.math.BigInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import timber.log.Timber

internal class AccountsLoader(
    private val scope: CoroutineScope,
    private val accountsState: MutableStateFlow<AccountsLoadState>,
    private val accountsRepository: AccountsRepository,
    private val stakingDetailsRepository: StakingDetailsRepository,
    private val defiTypeProvider: () -> DeFiNavActions?,
    private val mscaAddressProvider: () -> String?,
) {
    private var loadAccountsJob: Job? = null

    fun load(vaultId: VaultId) {
        loadAccountsJob?.cancel()
        loadAccountsJob =
            when (defiTypeProvider()) {
                DeFiNavActions.WITHDRAW_RUJI ->
                    scope.safeLaunch(onError = ::onLoadError) { loadRewardsAccount(vaultId) }

                DeFiNavActions.WITHDRAW_USDC_CIRCLE ->
                    scope.safeLaunch(onError = ::onLoadError) { loadCircleUSDCAccount(vaultId) }

                null,
                DeFiNavActions.BOND,
                DeFiNavActions.STAKE_RUJI,
                DeFiNavActions.STAKE_TCY,
                DeFiNavActions.STAKE_STCY,
                DeFiNavActions.UNSTAKE_STCY,
                DeFiNavActions.MINT_YRUNE,
                DeFiNavActions.MINT_YTCY,
                DeFiNavActions.REDEEM_YRUNE,
                DeFiNavActions.REDEEM_YTCY,
                DeFiNavActions.DEPOSIT_USDC_CIRCLE,
                DeFiNavActions.FREEZE_TRX ->
                    scope.safeLaunch(onError = ::onLoadError) {
                        accountsRepository
                            .loadAddresses(vaultId)
                            .map { addrs -> addrs.flatMap { it.accounts } }
                            .collect { publishLoaded(it) }
                    }

                else ->
                    scope.safeLaunch(onError = ::onLoadError) {
                        accountsRepository
                            .loadDeFiAddresses(vaultId, false)
                            .map { addrs -> addrs.flatMap { it.accounts } }
                            .collect { publishLoaded(it) }
                    }
            }
    }

    private fun publishLoaded(accounts: List<Account>) {
        accountsState.value = AccountsLoadState.Loaded(accounts)
    }

    private suspend fun onLoadError(error: Throwable) {
        Timber.e(error, "Failed to load accounts")
    }

    // Collect both cached and hydrated emissions from loadAddresses (isRefresh = false) so
    // the form renders the cached snapshot immediately and then re-renders once balances
    // have been refreshed from the network. Using isRefresh = true here would skip the
    // cached pre-emission and block the form on slow networks.
    private suspend fun loadCircleUSDCAccount(vaultId: VaultId) {
        accountsRepository
            .loadAddresses(vaultId, isRefresh = false)
            .map { addrs -> addrs.flatMap { it.accounts } }
            .collect { accountsLoaded -> publishCircleUsdc(vaultId, accountsLoaded) }
    }

    private suspend fun publishCircleUsdc(vaultId: VaultId, accountsLoaded: List<Account>) {
        val ethereumAccount =
            accountsLoaded.find { it.token.id.equals(Coins.Ethereum.ETH.id, true) }
        if (ethereumAccount == null) {
            // Without a vault-bound ETH account the address copied onto USDC below would be
            // empty, which silently breaks any later submit through WithdrawUsdcCircleStrategy.
            Timber.e("Ethereum account not available for Circle USDC withdrawal")
            publishLoaded(emptyList())
            return
        }

        val usdc = Coins.Ethereum.USDC.copy(address = ethereumAccount.token.address)
        val mscaAddress = mscaAddressProvider()

        if (mscaAddress != null) {
            val id = usdc.generateId(mscaAddress)
            val cachedDetails = stakingDetailsRepository.getStakingDetailsById(vaultId, id)
            val usdcCircleAccount =
                Account(
                    token = usdc,
                    tokenValue =
                        TokenValue(
                            value = cachedDetails?.stakeAmount ?: BigInteger.ZERO,
                            token = usdc,
                        ),
                    fiatValue = null,
                    price = null,
                )
            publishLoaded(listOf(ethereumAccount, usdcCircleAccount))
        } else {
            Timber.e("MSCA address not available for Circle USDC withdrawal")
            publishLoaded(
                listOf(
                    ethereumAccount,
                    Account(
                        token = usdc,
                        tokenValue = TokenValue(value = BigInteger.ZERO, token = usdc),
                        fiatValue = null,
                        price = null,
                    ),
                )
            )
        }
    }

    // Collect both cached and hydrated emissions so the form renders cached RUNE/RUJI
    // balances immediately, then refreshes when network balances arrive.
    private suspend fun loadRewardsAccount(vaultId: VaultId) {
        accountsRepository
            .loadAddresses(vaultId, isRefresh = false)
            .map { addrs -> addrs.flatMap { it.accounts } }
            .collect { accountsLoaded -> publishRewards(vaultId, accountsLoaded) }
    }

    private suspend fun publishRewards(vaultId: VaultId, accountsLoaded: List<Account>) {
        val thorchainAccount =
            accountsLoaded.find { it.token.id.equals(Coins.ThorChain.RUNE.id, true) }
                ?: run {
                    publishLoaded(emptyList())
                    return
                }

        val rujiAccount =
            accountsLoaded.find { it.token.id.equals(Coins.ThorChain.RUJI.id, true) }
                ?: run {
                    publishLoaded(emptyList())
                    return
                }

        val cachedDetails =
            stakingDetailsRepository.getStakingDetailsByCoindId(vaultId, Coins.ThorChain.RUJI.id)

        if (cachedDetails != null) {
            val rewardsAccount =
                Account(
                    token = RUJI_REWARDS_COIN.copy(address = thorchainAccount.token.address),
                    tokenValue =
                        TokenValue(
                            value = cachedDetails.rewards?.toBigInteger() ?: BigInteger.ZERO,
                            token = RUJI_REWARDS_COIN,
                        ),
                    fiatValue = null,
                    price = null,
                )
            publishLoaded(listOf(rewardsAccount, thorchainAccount, rujiAccount))
        } else {
            publishLoaded(emptyList())
        }
    }
}
