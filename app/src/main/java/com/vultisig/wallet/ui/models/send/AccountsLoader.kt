package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.data.blockchain.model.StakingDetails.Companion.generateId
import com.vultisig.wallet.data.blockchain.thorchain.RujiStakingService.Companion.RUJI_REWARDS_COIN
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import java.math.BigInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

internal class AccountsLoader(
    private val scope: CoroutineScope,
    private val accounts: MutableStateFlow<List<Account>>,
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
                DeFiNavActions.WITHDRAW_RUJI -> scope.launch { loadRewardsAccount(vaultId) }

                DeFiNavActions.WITHDRAW_USDC_CIRCLE ->
                    scope.launch { loadCircleUSDCAccount(vaultId) }

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
                    scope.launch {
                        accountsRepository
                            .loadAddresses(vaultId)
                            .map { addrs -> addrs.flatMap { it.accounts } }
                            .collect(accounts)
                    }

                else ->
                    scope.launch {
                        accountsRepository
                            .loadDeFiAddresses(vaultId, false)
                            .map { addrs -> addrs.flatMap { it.accounts } }
                            .collect(accounts)
                    }
            }
    }

    private suspend fun loadCircleUSDCAccount(vaultId: VaultId) {
        val accountsLoaded =
            accountsRepository.loadAddresses(vaultId).firstOrNull()?.flatMap { it.accounts }
        val ethereumAccount =
            accountsLoaded?.find { it.token.id.equals(Coins.Ethereum.ETH.id, true) }
                ?: Account(
                    token = Coins.Ethereum.ETH,
                    tokenValue = TokenValue(BigInteger.ZERO, Coins.Ethereum.ETH),
                    fiatValue = null,
                    price = null,
                )

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
            accounts.value = listOf(ethereumAccount, usdcCircleAccount)
        } else {
            Timber.e("MSCA address not available for Circle USDC withdrawal")
            accounts.value =
                listOf(
                    ethereumAccount,
                    Account(
                        token = usdc,
                        tokenValue = TokenValue(value = BigInteger.ZERO, token = usdc),
                        fiatValue = null,
                        price = null,
                    ),
                )
        }
    }

    private suspend fun loadRewardsAccount(vaultId: VaultId) {
        val accountsLoaded =
            accountsRepository.loadAddresses(vaultId).firstOrNull()?.flatMap { it.accounts }
        val thorchainAccount =
            accountsLoaded?.find { it.token.id.equals(Coins.ThorChain.RUNE.id, true) } ?: return

        val rujiAccount =
            accountsLoaded.find { it.token.id.equals(Coins.ThorChain.RUJI.id, true) } ?: return

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
            accounts.value = listOf(rewardsAccount, thorchainAccount, rujiAccount)
        } else {
            accounts.value = emptyList()
        }
    }
}
