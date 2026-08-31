package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.data.blockchain.model.StakingDetails.Companion.generateId
import com.vultisig.wallet.data.blockchain.thorchain.DefaultStakingPositionService
import com.vultisig.wallet.data.blockchain.thorchain.RujiStakingService.Companion.RUJI_REWARDS_COIN
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.TokenId
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.CancellationException
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
    private val defaultStakingPositionService: DefaultStakingPositionService,
    private val defiTypeProvider: () -> DeFiNavActions?,
    private val preselectedTokenIdProvider: () -> TokenId?,
    private val mscaAddressProvider: () -> String?,
) {
    private var loadAccountsJob: Job? = null

    // Job.cancel() is cooperative, so a previous collector can still execute one more
    // publish after `load()` resets the state to `Uninitialized` — stamping each load with
    // a generation and gating publishes on the current generation drops those stale
    // emissions instead of letting them flash superseded data into accountsState.
    private var currentGeneration: Long = 0L

    fun load(vaultId: VaultId) {
        loadAccountsJob?.cancel()
        val generation = ++currentGeneration
        // Reset before launching the new load so a vault/action switch doesn't leave the
        // previous session's Loaded(...) visible while the new collector spins up.
        accountsState.value = AccountsLoadState.Uninitialized
        loadAccountsJob =
            when (defiTypeProvider()) {
                DeFiNavActions.WITHDRAW_RUJI ->
                    scope.safeLaunch(onError = ::onLoadError) {
                        loadRewardsAccount(vaultId, generation)
                    }

                DeFiNavActions.WITHDRAW_USDC_CIRCLE ->
                    scope.safeLaunch(onError = ::onLoadError) {
                        loadCircleUSDCAccount(vaultId, generation)
                    }

                DeFiNavActions.UNSTAKE_SRUJI ->
                    scope.safeLaunch(onError = ::onLoadError) {
                        loadAutoCompoundRujiAccount(vaultId, generation)
                    }

                DeFiNavActions.UNSTAKE_YBRUNE ->
                    scope.safeLaunch(onError = ::onLoadError) {
                        loadBondedRuneReceiptAccount(vaultId, generation)
                    }

                null,
                DeFiNavActions.STAKE_RUJI,
                DeFiNavActions.STAKE_SRUJI,
                DeFiNavActions.STAKE_TCY,
                DeFiNavActions.STAKE_STCY,
                DeFiNavActions.UNSTAKE_STCY,
                // The bond is funded with bRUNE, an ordinary wallet token.
                DeFiNavActions.STAKE_YBRUNE,
                DeFiNavActions.MINT_YRUNE,
                DeFiNavActions.MINT_YTCY,
                DeFiNavActions.REDEEM_YRUNE,
                DeFiNavActions.REDEEM_YTCY,
                DeFiNavActions.FREEZE_TRX ->
                    scope.safeLaunch(onError = ::onLoadError) {
                        loadWalletAccounts(vaultId, generation)
                    }

                else ->
                    scope.safeLaunch(onError = ::onLoadError) {
                        accountsRepository
                            .loadDeFiAddresses(vaultId, false)
                            .map { addrs -> addrs.spendableAccounts() }
                            .collect { publishLoaded(it, generation) }
                    }
            }
    }

    // Routes the autocompound switch through this single component so accountsState only
    // ever has one writer. The previous in-VM `collect` raced against this loader — and
    // for UNSTAKE_TCY the two sources were different APIs (loadAddresses vs
    // loadDeFiAddresses), so interleaved emissions would overwrite each other with data
    // sourced from different endpoints. Token selection still happens in the VM via
    // onAccountsLoaded, but the publish to accountsState happens here under the same
    // cancel + generation discipline as `load()`.
    fun loadForAutoCompoundSwitch(
        vaultId: VaultId,
        useStableCompound: Boolean,
        onAccountsLoaded: suspend (List<Account>) -> Unit,
    ) {
        loadAccountsJob?.cancel()
        val generation = ++currentGeneration
        accountsState.value = AccountsLoadState.Uninitialized
        loadAccountsJob =
            scope.safeLaunch(onError = ::onLoadError) {
                val addressesFlow =
                    if (useStableCompound) {
                        accountsRepository.loadAddresses(vaultId)
                    } else {
                        accountsRepository.loadDeFiAddresses(vaultId, false)
                    }
                addressesFlow
                    .map { addrs -> addrs.spendableAccounts() }
                    .collect { accounts ->
                        if (publishLoaded(accounts, generation)) {
                            onAccountsLoaded(accounts)
                        }
                    }
            }
    }

    /**
     * The vault's own accounts: what an ordinary send, and every DeFi form funded with a wallet
     * token, draws on.
     *
     * A send that was opened on the ybRUNE receipt carries it alongside them. The receipt is an
     * ordinary bank denom the vault can move like any other token — the Transfer action on its
     * position card is exactly that — but it is deliberately kept out of token discovery, so
     * nothing in the addresses flow would give the form an account to send it from.
     */
    private suspend fun loadWalletAccounts(vaultId: VaultId, generation: Long) {
        if (defiTypeProvider() == null && isBondedRuneReceipt(preselectedTokenIdProvider())) {
            loadBondedRuneReceiptAccount(vaultId, generation, alongsideWalletAccounts = true)
            return
        }
        accountsRepository
            .loadAddresses(vaultId)
            .map { addrs -> addrs.flatMap { it.accounts } }
            .collect { publishLoaded(it, generation) }
    }

    private fun isBondedRuneReceipt(tokenId: TokenId?): Boolean =
        tokenId.equals(Coins.ThorChain.ybRUNE.id, true)

    /**
     * The accounts a form may draw on.
     *
     * loadDeFiAddresses also carries an account for each position that is never a wallet token —
     * the sRUJI receipt kept out of token discovery, or a Kamino Earn deposit in a token the wallet
     * itself has none of — so the DeFi tab can total it. Those are not holdings a form can draw on,
     * so they are dropped here rather than offered in the token picker.
     */
    private fun List<Address>.spendableAccounts(): List<Account> =
        flatMap { it.accounts }.filterNot { Coins.isDefiOnly(it.token) || it.isPositionOnly }

    private fun publishLoaded(accounts: List<Account>, generation: Long): Boolean {
        if (generation != currentGeneration) return false
        accountsState.value = AccountsLoadState.Loaded(accounts)
        return true
    }

    /**
     * Finds the wallet account for [tokenId], or abandons this load by publishing an empty list.
     *
     * Every derived position below (rewards, Circle USDC, sRUJI) is synthesized on top of a wallet
     * account and copies its address, so publishing one without its source would hand the form a
     * token with an empty address and silently break the later submit. [missingReason] is logged
     * when the absence is genuinely unexpected; callers that also run against a pre-hydration
     * snapshot omit it rather than flood the error log.
     */
    private fun List<Account>.findSourceOrPublishEmpty(
        tokenId: String,
        generation: Long,
        missingReason: String? = null,
    ): Account? =
        find { it.token.id.equals(tokenId, true) }
            ?: run {
                missingReason?.let { Timber.e(it) }
                publishLoaded(emptyList(), generation)
                null
            }

    private suspend fun onLoadError(error: Throwable) {
        Timber.e(error, "Failed to load accounts")
    }

    // Collect both cached and hydrated emissions from loadAddresses (isRefresh = false) so
    // the form renders the cached snapshot immediately and then re-renders once balances
    // have been refreshed from the network. Using isRefresh = true here would skip the
    // cached pre-emission and block the form on slow networks.
    private suspend fun loadCircleUSDCAccount(vaultId: VaultId, generation: Long) {
        // Resolve staking details once for the lifetime of this load — generateId only
        // depends on Coins.Ethereum.USDC + mscaAddress (neither of which change between
        // cached and hydrated emissions), and the stake amount doesn't change when ETH
        // balances hydrate. Repeating the lookup per emission was a wasted DB hit.
        val mscaAddress = mscaAddressProvider()
        val cachedDetails =
            mscaAddress?.let { msca ->
                stakingDetailsRepository.getStakingDetailsById(
                    vaultId,
                    Coins.Ethereum.USDC.generateId(msca),
                )
            }
        accountsRepository
            .loadAddresses(vaultId)
            .map { addrs -> addrs.flatMap { it.accounts } }
            .collect { accountsLoaded ->
                publishCircleUsdc(
                    accountsLoaded,
                    mscaAddress,
                    cachedDetails?.stakeAmount,
                    generation,
                )
            }
    }

    private fun publishCircleUsdc(
        accountsLoaded: List<Account>,
        mscaAddress: String?,
        stakeAmount: BigInteger?,
        generation: Long,
    ) {
        if (generation != currentGeneration) return
        // Without a vault-bound ETH account the address copied onto USDC below would be empty,
        // which silently breaks any later submit through WithdrawUsdcCircleStrategy.
        val ethereumAccount =
            accountsLoaded.findSourceOrPublishEmpty(
                tokenId = Coins.Ethereum.ETH.id,
                generation = generation,
                missingReason = "Ethereum account not available for Circle USDC withdrawal",
            ) ?: return

        val usdc = Coins.Ethereum.USDC.copy(address = ethereumAccount.token.address)

        if (mscaAddress != null) {
            val usdcCircleAccount =
                Account(
                    token = usdc,
                    tokenValue = TokenValue(value = stakeAmount ?: BigInteger.ZERO, token = usdc),
                    fiatValue = null,
                    price = null,
                )
            publishLoaded(listOf(ethereumAccount, usdcCircleAccount), generation)
        } else {
            // Pre-setup state (MSCA not yet provisioned), not an error — use warn so this
            // doesn't flood error logs on the cached emission before the MSCA resolves.
            Timber.w("MSCA address not available for Circle USDC withdrawal")
            publishLoaded(
                listOf(
                    ethereumAccount,
                    Account(
                        token = usdc,
                        tokenValue = TokenValue(value = BigInteger.ZERO, token = usdc),
                        fiatValue = null,
                        price = null,
                    ),
                ),
                generation,
            )
        }
    }

    /**
     * The ybRUNE receipt is not a wallet token either, so the unbond form has no account to draw on
     * until one is synthesized from the vault's receipt balance.
     *
     * Denominated in receipt units — the vault's `x/staking-x/brune` bank balance, which is what
     * the position reports — so the amount the form carries is already what funds the unbond. No
     * conversion happens at submit, unlike the RUJI receipt above, whose position is reported in
     * RUJI.
     *
     * With [alongsideWalletAccounts] the receipt joins the vault's own accounts instead of
     * replacing them — that is the plain send the position card's Transfer action opens, where
     * every other holding stays selectable.
     *
     * Cached first, then re-read from the chain, the way the balance rows around it hydrate. The
     * cached figure is whatever the DeFi tab last stored, and this account is what MAX fills the
     * form from: left at the cache, a bond made since that refresh would cap MAX below the true
     * position and quietly redeem less than everything, with the submit-time clamp — which only
     * ever lowers an amount — unable to notice.
     */
    private suspend fun loadBondedRuneReceiptAccount(
        vaultId: VaultId,
        generation: Long,
        alongsideWalletAccounts: Boolean = false,
    ) {
        var receiptAmount =
            stakingDetailsRepository
                .getStakingDetailsByCoindId(vaultId, Coins.ThorChain.ybRUNE.id)
                ?.stakeAmount
        var hydrated = false
        accountsRepository
            .loadAddresses(vaultId)
            .map { addrs -> addrs.flatMap { it.accounts } }
            .collect { accountsLoaded ->
                publishBondedRuneReceipt(
                    accountsLoaded,
                    receiptAmount,
                    generation,
                    alongsideWalletAccounts,
                )

                val address =
                    accountsLoaded
                        .find { it.token.id.equals(Coins.ThorChain.RUNE.id, true) }
                        ?.token
                        ?.address
                if (hydrated || address.isNullOrEmpty()) return@collect

                // A failed read leaves the cached figure standing rather than emptying the form:
                // the submit clamp reads the balance again and refuses to build on a guess.
                val live =
                    try {
                        defaultStakingPositionService.getReceiptBalance(
                            address,
                            Coins.ThorChain.ybRUNE,
                        )
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        Timber.e(t, "Failed to read the live ybRUNE receipt balance")
                        return@collect
                    }

                // Marked hydrated only once a read has actually landed. Set before the attempt, a
                // single transient failure retired the retry for the rest of the form's life, and
                // MAX went back to the cached ceiling the submit clamp can only ever lower.
                hydrated = true
                receiptAmount = live
                publishBondedRuneReceipt(accountsLoaded, live, generation, alongsideWalletAccounts)
            }
    }

    private fun publishBondedRuneReceipt(
        accountsLoaded: List<Account>,
        receiptAmount: BigInteger?,
        generation: Long,
        alongsideWalletAccounts: Boolean = false,
    ) {
        if (generation != currentGeneration) return
        // As with sRUJI: the RUNE account carries the address the unbond is sent from, funds the
        // gas fee, and supplies the derived key the receipt's catalogue entry has no copy of.
        val thorchainAccount =
            if (alongsideWalletAccounts) {
                // A plain send that merely started on the receipt. Without a THORChain account
                // there is nothing to synthesize it from, but the rest of the vault is still
                // perfectly sendable — leave the form the accounts it does have rather than
                // emptying it over a token it can simply drop.
                accountsLoaded.find { it.token.id.equals(Coins.ThorChain.RUNE.id, true) }
                    ?: run {
                        publishLoaded(accountsLoaded, generation)
                        return
                    }
            } else {
                accountsLoaded.findSourceOrPublishEmpty(
                    tokenId = Coins.ThorChain.RUNE.id,
                    generation = generation,
                    missingReason = "THORChain account not available for ybRUNE unstake",
                ) ?: return
            }

        val ybRune =
            Coins.ThorChain.ybRUNE.copy(
                address = thorchainAccount.token.address,
                hexPublicKey = thorchainAccount.token.hexPublicKey,
            )
        val ybRuneAccount =
            Account(
                token = ybRune,
                tokenValue = TokenValue(value = receiptAmount ?: BigInteger.ZERO, token = ybRune),
                fiatValue = null,
                price = null,
            )
        val accounts =
            if (alongsideWalletAccounts) {
                // The whole wallet stays in the picker, with the receipt appended: this is an
                // ordinary send, not a form that may only ever draw on the position. Any receipt
                // already in the list is dropped in favour of the one carrying the live balance.
                accountsLoaded.filterNot { it.token.id.equals(Coins.ThorChain.ybRUNE.id, true) } +
                    ybRuneAccount
            } else {
                listOf(ybRuneAccount, thorchainAccount)
            }
        publishLoaded(accounts, generation)
    }

    // Collect both cached and hydrated emissions so the form renders cached RUNE/RUJI
    // balances immediately, then refreshes when network balances arrive.
    private suspend fun loadRewardsAccount(vaultId: VaultId, generation: Long) {
        // Resolve staking details once for the lifetime of this load. The rewards row is
        // sourced from staking details (not from the addresses flow), so the same value
        // should back both the cached and hydrated emissions instead of re-querying the
        // repo on every upstream emission.
        val cachedDetails =
            stakingDetailsRepository.getStakingDetailsByCoindId(vaultId, Coins.ThorChain.RUJI.id)
        accountsRepository
            .loadAddresses(vaultId)
            .map { addrs -> addrs.flatMap { it.accounts } }
            .collect { accountsLoaded ->
                publishRewards(accountsLoaded, cachedDetails?.rewards, generation)
            }
    }

    private fun publishRewards(
        accountsLoaded: List<Account>,
        rewards: BigDecimal?,
        generation: Long,
    ) {
        if (generation != currentGeneration) return
        val thorchainAccount =
            accountsLoaded.findSourceOrPublishEmpty(Coins.ThorChain.RUNE.id, generation) ?: return

        val rujiAccount =
            accountsLoaded.findSourceOrPublishEmpty(Coins.ThorChain.RUJI.id, generation) ?: return

        if (rewards != null) {
            val rewardsAccount =
                Account(
                    token = RUJI_REWARDS_COIN.copy(address = thorchainAccount.token.address),
                    tokenValue =
                        TokenValue(value = rewards.toBigInteger(), token = RUJI_REWARDS_COIN),
                    fiatValue = null,
                    price = null,
                )
            publishLoaded(listOf(rewardsAccount, thorchainAccount, rujiAccount), generation)
        } else {
            publishLoaded(emptyList(), generation)
        }
    }

    // The auto-compounding RUJI position is not a wallet token: its sRUJI receipt is deliberately
    // kept out of token discovery, so no account for it comes back from either addresses flow.
    // Synthesize one from the staking details the DeFi tab cached, denominated in RUJI (the pool's
    // `liquidSize`) so the form's MAX and the card the user tapped agree. UnstakeStrategy converts
    // that amount back into receipt shares at submit time.
    private suspend fun loadAutoCompoundRujiAccount(vaultId: VaultId, generation: Long) {
        val cachedDetails =
            stakingDetailsRepository.getStakingDetailsByCoindId(vaultId, Coins.ThorChain.sRUJI.id)
        accountsRepository
            .loadAddresses(vaultId)
            .map { addrs -> addrs.flatMap { it.accounts } }
            .collect { accountsLoaded ->
                publishAutoCompoundRuji(accountsLoaded, cachedDetails?.stakeAmount, generation)
            }
    }

    private fun publishAutoCompoundRuji(
        accountsLoaded: List<Account>,
        autoCompoundAmount: BigInteger?,
        generation: Long,
    ) {
        if (generation != currentGeneration) return
        // The RUNE account carries the THORChain address the unbond is sent from, and funds the gas
        // fee; without it the synthesized account below would have an empty address.
        val thorchainAccount =
            accountsLoaded.findSourceOrPublishEmpty(
                tokenId = Coins.ThorChain.RUNE.id,
                generation = generation,
                missingReason = "THORChain account not available for sRUJI unstake",
            ) ?: return

        // The receipt is never discovered as a wallet token, so its template carries an empty
        // key. It becomes KeysignPayload.coin, and ThorChainHelper builds the signing input from
        // coin.hexPublicKey — an empty one aborts the redemption before the keysign QR appears.
        // Every THORChain token shares the chain's derived key, so the RUNE account supplies it.
        val sRuji =
            Coins.ThorChain.sRUJI.copy(
                address = thorchainAccount.token.address,
                hexPublicKey = thorchainAccount.token.hexPublicKey,
            )
        val sRujiAccount =
            Account(
                token = sRuji,
                tokenValue =
                    TokenValue(value = autoCompoundAmount ?: BigInteger.ZERO, token = sRuji),
                fiatValue = null,
                price = null,
            )
        publishLoaded(listOf(sRujiAccount, thorchainAccount), generation)
    }
}
