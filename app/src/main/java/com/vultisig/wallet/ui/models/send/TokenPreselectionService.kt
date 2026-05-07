package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.data.blockchain.thorchain.RujiStakingService.Companion.RUJI_REWARDS_COIN
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.ChainId
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.TokenId
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

internal class TokenPreselectionService(
    private val scope: CoroutineScope,
    private val accounts: StateFlow<List<Account>>,
    private val defiTypeProvider: () -> DeFiNavActions?,
    private val selectedTokenProvider: () -> Coin?,
    private val onTokenSelected: (Coin) -> Unit,
) {
    private var preSelectTokenJob: Job? = null

    fun preSelect(
        preSelectedChainIds: List<ChainId?>,
        preSelectedTokenId: TokenId?,
        forcePreselection: Boolean = false,
    ) {
        Timber.d(
            "preSelectToken(%s, %s, %s)",
            preSelectedChainIds,
            preSelectedTokenId,
            forcePreselection,
        )

        preSelectTokenJob?.cancel()
        preSelectTokenJob =
            scope.launch {
                accounts.collect { accounts ->
                    val preSelectedToken =
                        if (defiTypeProvider() == null) {
                            findPreselectedToken(accounts, preSelectedChainIds, preSelectedTokenId)
                        } else {
                            findDeFiPreselectedToken(
                                accounts,
                                preSelectedChainIds,
                                preSelectedTokenId,
                            )
                        }

                    Timber.d("Found a new token to pre select %s", preSelectedToken)

                    // if user hasn't yet selected any token, preselect found token
                    if (
                        (forcePreselection || selectedTokenProvider() == null) &&
                            preSelectedToken != null
                    ) {
                        onTokenSelected(preSelectedToken)
                    }
                }
            }
    }

    /**
     * Returns first token found for tokenId or chainId or first token it all list, can return null
     * if there's no tokens in the vault
     */
    private fun findPreselectedToken(
        accounts: List<Account>,
        preSelectedChainIds: List<ChainId?>,
        preSelectedTokenId: TokenId?,
    ): Coin? {
        var searchByChainResult: Coin? = null

        for (account in accounts) {
            val accountToken = account.token
            if (accountToken.id.equals(preSelectedTokenId, ignoreCase = true)) {
                // if we find token by id, return it asap
                return accountToken
            }
            if (
                searchByChainResult == null &&
                    preSelectedChainIds.contains(accountToken.chain.id) &&
                    accountToken.isNativeToken
            ) {
                // if we find token by chain, remember it and return later if nothing else found
                searchByChainResult = accountToken
            }
        }

        // if user selected none, or nothing was found, select the first token
        return searchByChainResult ?: accounts.firstOrNull()?.token
    }

    private fun findDeFiPreselectedToken(
        accounts: List<Account>,
        preSelectedChainIds: List<ChainId?>,
        preSelectedTokenId: TokenId?,
    ): Coin? {
        for (account in accounts) {
            val accountToken = account.token
            if (accountToken.id.equals(preSelectedTokenId, ignoreCase = true)) {
                return accountToken
            }
        }

        // default coins, in case the account does not exist
        return when (defiTypeProvider()) {
            DeFiNavActions.STAKE_RUJI,
            DeFiNavActions.UNSTAKE_RUJI -> Coins.ThorChain.RUJI

            DeFiNavActions.STAKE_TCY,
            DeFiNavActions.UNSTAKE_TCY -> Coins.ThorChain.TCY

            DeFiNavActions.MINT_YRUNE -> Coins.ThorChain.RUNE
            DeFiNavActions.MINT_YTCY -> Coins.ThorChain.TCY
            DeFiNavActions.BOND -> Coins.ThorChain.RUNE
            DeFiNavActions.UNBOND -> Coins.ThorChain.RUNE
            DeFiNavActions.WITHDRAW_RUJI -> RUJI_REWARDS_COIN
            DeFiNavActions.REDEEM_YRUNE -> Coins.ThorChain.yRUNE
            DeFiNavActions.REDEEM_YTCY -> Coins.ThorChain.yTCY
            DeFiNavActions.DEPOSIT_USDC_CIRCLE -> Coins.Ethereum.USDC
            DeFiNavActions.WITHDRAW_USDC_CIRCLE -> Coins.Ethereum.USDC
            DeFiNavActions.STAKE_STCY -> Coins.ThorChain.TCY
            DeFiNavActions.UNSTAKE_STCY -> Coins.ThorChain.sTCY
            DeFiNavActions.STAKE_CACAO,
            DeFiNavActions.UNSTAKE_CACAO,
            DeFiNavActions.ADD_LP,
            DeFiNavActions.REMOVE_LP -> Coins.MayaChain.CACAO
            DeFiNavActions.FREEZE_TRX,
            DeFiNavActions.UNFREEZE_TRX -> Coins.Tron.TRX
            null -> findPreselectedToken(accounts, preSelectedChainIds, preSelectedTokenId)
        }
    }
}
