package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoAction
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoPriorityFee
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoRelayedIntent
import com.vultisig.wallet.data.blockchain.solana.kamino.coin
import com.vultisig.wallet.data.blockchain.solana.kamino.kaminoDestinationAddress
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import timber.log.Timber

/**
 * This intent when [payload]'s display fields describe the transaction it was read from, null when
 * they do not.
 *
 * A co-signing device is told an amount, a destination, an asset and a fee, and handed the bytes
 * that will actually be signed. Only the bytes say what the transaction does, so the two are
 * compared before the payload is given a Kamino deposit's framing: a payload naming a destination
 * the transaction does not pay, an asset it does not move, a fee it does not charge, or a deposit
 * amount the kVault instruction does not carry, keeps the generic screen rather than being dressed
 * up as something this device could not confirm (issue #5644).
 *
 * Withdraw amounts are deliberately not compared — the instruction carries the share count where
 * the payload carries the tokens those shares are worth, and the rate between them is not something
 * a joining device can pin down. That one is disclosed instead of checked, by
 * [JoinDepositUiModelBuilder], which names the share figure the bytes do carry. iOS answers the
 * same case with `.amountUnverifiable` rather than with a refusal, for the same reason: refusing
 * would break every legitimate peer withdraw.
 */
internal fun KaminoRelayedIntent.takeIfDescribedBy(payload: KeysignPayload): KaminoRelayedIntent? {
    // Not merely a cast guard. The fee row below is priced off this payload's compute budget, and
    // the deposit screen scales its amount with this payload's coin — neither of which a payload
    // for some other chain carries at all.
    val specific = payload.blockChainSpecific as? BlockChainSpecific.Solana
    if (specific == null || payload.coin.chain != Chain.Solana) {
        Timber.w("Kamino bytes arrived under a payload that is not a Solana one")
        return null
    }

    val expectedDestination = kaminoDestinationAddress(vault, action, payload.coin.address)
    if (payload.toAddress != expectedDestination) {
        Timber.w("Kamino payload names a destination its own transaction does not pay")
        return null
    }
    if (!describesAsset(payload.coin)) {
        Timber.w("Kamino payload names an asset that is not the vault's underlying token")
        return null
    }
    if (!describesPriorityFee(specific)) {
        Timber.w("Kamino payload states a network fee its own transaction does not charge")
        return null
    }
    if (action == KaminoAction.DEPOSIT && amount != payload.toAmount) {
        Timber.w("Kamino payload states an amount its own transaction does not carry")
        return null
    }
    return this
}

/**
 * Whether [coin] is the vault's underlying token.
 *
 * The verify screen scales `toAmount` by this coin's decimals and labels it with its ticker, so a
 * coin that is not the vault's asset renders the headline figure at the wrong precision or under
 * the wrong name — a nine-decimal SOL deposit relayed as a six-decimal coin reads a thousandfold
 * out. The base-unit amount check above does not catch it: base units are the same number either
 * way, and it is the scale applied to them that moves.
 *
 * Skipped when no wallet coin maps to the vault's mint, matching iOS: an unestablished fact is not
 * a contradiction.
 */
private fun KaminoRelayedIntent.describesAsset(coin: Coin): Boolean {
    val underlying = vault.coin ?: return true
    return coin.decimal == underlying.decimal &&
        coin.contractAddress == underlying.contractAddress &&
        coin.ticker.equals(underlying.ticker, ignoreCase = true)
}

/**
 * Whether the compute budget recorded beside the bytes is the one inside them.
 *
 * The two are genuinely independent claims: [specific] is what the initiating device says it
 * injected and is what the joining device's fee row is priced from, while
 * [KaminoRelayedIntent.priorityFee] is what the runtime will actually charge against. A relay that
 * lowered the recorded price, or stripped the budget from the bytes, would otherwise leave a screen
 * quoting one fee for a transaction that pays another — and the displayed figure is clamped into
 * [com.vultisig.wallet.data.blockchain.solana.kamino.KaminoComputeBudget.MAX_UNIT_PRICE] where the
 * charged one is a bare `u64`.
 *
 * "Neither has one" would agree, but cannot arise: the validator refuses bytes carrying no budget
 * before this runs.
 */
private fun KaminoRelayedIntent.describesPriorityFee(specific: BlockChainSpecific.Solana): Boolean {
    val recorded =
        if (specific.priorityFee.signum() > 0 && specific.priorityLimit.signum() > 0) {
            KaminoPriorityFee(limit = specific.priorityLimit, price = specific.priorityFee)
        } else {
            null
        }
    return recorded == priorityFee
}
