package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.data.blockchain.thorchain.RujiStakingService.Companion.RUJI_REWARDS_COIN
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.ChainId
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.TokenId
import com.vultisig.wallet.data.models.isReadOnlyAsset
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

internal class TokenPreselectionService(
    private val scope: CoroutineScope,
    private val accountsState: StateFlow<AccountsLoadState>,
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
                var forced = forcePreselection
                accountsState.collect { state ->
                    // Wait until AccountsLoader publishes a Loaded state — Uninitialized is
                    // the sentinel for "haven't loaded yet". An intentional Loaded(emptyList)
                    // (e.g. DeFi prerequisites missing) still falls through to the default-
                    // coin map in findDeFiPreselectedToken.
                    val accounts = (state as? AccountsLoadState.Loaded)?.accounts ?: return@collect

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

                    // Force preselection fires once on the first Loaded emission, then defers
                    // to the user — otherwise every later accounts hydration would wipe their
                    // typed amount via selectToken → resetUserInputCache.
                    if ((forced || selectedTokenProvider() == null) && preSelectedToken != null) {
                        onTokenSelected(preSelectedToken)
                        forced = false
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
            // Read-only assets (XRPL issued currencies) can be balance-read but not sent. The
            // token id here comes straight from unvalidated deep-link params, so a crafted
            // vultisig://send link could otherwise pin one as the send source until keysign throws.
            if (accountToken.isReadOnlyAsset) {
                continue
            }
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
        return searchByChainResult ?: accounts.firstOrNull { !it.token.isReadOnlyAsset }?.token
    }

    private fun findDeFiPreselectedToken(
        accounts: List<Account>,
        preSelectedChainIds: List<ChainId?>,
        preSelectedTokenId: TokenId?,
    ): Coin? {
        // For WITHDRAW types, empty accounts means prerequisites are missing (e.g. no
        // RUNE/RUJI in vault for WITHDRAW_RUJI, no ETH for WITHDRAW_USDC_CIRCLE). Returning
        // a static template coin here would cause collectSelectedAccount to synthesize an
        // Account with tokenValue = null, making the form look submittable when it isn't.
        // UNSTAKE_SRUJI and UNSTAKE_YBRUNE belong to the same group: both receipt accounts are
        // synthesized on top of the vault's RUNE account, so no accounts means that prerequisite
        // is missing too.
        // STAKE types keep their defaults even on empty accounts because the default coin
        // guides the user toward what they would be staking.
        if (accounts.isEmpty()) {
            return when (defiTypeProvider()) {
                DeFiNavActions.WITHDRAW_RUJI,
                DeFiNavActions.WITHDRAW_USDC_CIRCLE,
                DeFiNavActions.UNSTAKE_SRUJI,
                DeFiNavActions.UNSTAKE_YBRUNE -> null
                else -> defaultDefiCoin(accounts, preSelectedChainIds, preSelectedTokenId)
            }
        }

        for (account in accounts) {
            val accountToken = account.token
            if (accountToken.id.equals(preSelectedTokenId, ignoreCase = true)) {
                return accountToken
            }
        }

        return defaultDefiCoin(accounts, preSelectedChainIds, preSelectedTokenId)
    }

    // default coins, in case the account does not exist
    private fun defaultDefiCoin(
        accounts: List<Account>,
        preSelectedChainIds: List<ChainId?>,
        preSelectedTokenId: TokenId?,
    ): Coin? =
        when (defiTypeProvider()) {
            DeFiNavActions.STAKE_RUJI,
            DeFiNavActions.UNSTAKE_RUJI -> Coins.ThorChain.RUJI

            // The auto-compounding position is funded with RUJI and redeemed out of the sRUJI
            // receipt, so the two directions start from different tokens.
            DeFiNavActions.STAKE_SRUJI -> Coins.ThorChain.RUJI
            DeFiNavActions.UNSTAKE_SRUJI -> Coins.ThorChain.sRUJI

            DeFiNavActions.STAKE_TCY,
            DeFiNavActions.UNSTAKE_TCY -> Coins.ThorChain.TCY

            DeFiNavActions.MINT_YRUNE -> Coins.ThorChain.RUNE
            DeFiNavActions.MINT_YTCY -> Coins.ThorChain.TCY
            DeFiNavActions.WITHDRAW_RUJI -> RUJI_REWARDS_COIN
            DeFiNavActions.REDEEM_YRUNE -> Coins.ThorChain.yRUNE
            DeFiNavActions.REDEEM_YTCY -> Coins.ThorChain.yTCY
            DeFiNavActions.WITHDRAW_USDC_CIRCLE -> Coins.Ethereum.USDC
            DeFiNavActions.STAKE_STCY -> Coins.ThorChain.TCY
            DeFiNavActions.UNSTAKE_STCY -> Coins.ThorChain.sTCY

            // The liquid bond is funded with bRUNE and redeemed out of the ybRUNE receipt, so the
            // two directions start from different tokens — as with the RUJI pair above.
            DeFiNavActions.STAKE_YBRUNE -> Coins.ThorChain.bRUNE
            DeFiNavActions.UNSTAKE_YBRUNE -> Coins.ThorChain.ybRUNE
            // The EVM asset side of a pool add is the one case ADD_LP still submits through the
            // Send flow (the Deposit flow's AddLiquidityStrategy only covers the RUNE/CACAO side).
            DeFiNavActions.ADD_LP,
            DeFiNavActions.STAKE_CACAO,
            DeFiNavActions.UNSTAKE_CACAO,
            DeFiNavActions.REMOVE_LP -> Coins.MayaChain.CACAO
            DeFiNavActions.FREEZE_TRX,
            DeFiNavActions.UNFREEZE_TRX -> Coins.Tron.TRX
            DeFiNavActions.STAKE_TON,
            DeFiNavActions.UNSTAKE_TON -> Coins.Ton.TON
            // Bond/Unbond only submit through the Deposit flow now, but a Route.Send reaching here
            // (a stale deep link or restored back stack) must still preselect the chain's own
            // native asset explicitly rather than falling back to findPreselectedToken's
            // arbitrary-first-account default — submitFor refuses to submit these regardless.
            DeFiNavActions.BOND,
            DeFiNavActions.UNBOND -> Coins.ThorChain.RUNE
            null -> findPreselectedToken(accounts, preSelectedChainIds, preSelectedTokenId)
        }
}
