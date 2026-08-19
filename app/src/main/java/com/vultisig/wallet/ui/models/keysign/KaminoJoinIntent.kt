package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoAction
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoRelayedIntent
import com.vultisig.wallet.data.blockchain.solana.kamino.kaminoDestinationAddress
import com.vultisig.wallet.data.models.payload.KeysignPayload
import timber.log.Timber

/**
 * This intent when [payload]'s display fields describe the transaction it was read from, null when
 * they do not.
 *
 * A co-signing device is told an amount and a destination, and handed the bytes that will actually
 * be signed. Only the bytes say what the transaction does, so the two are compared before the
 * payload is given a Kamino deposit's framing: a payload naming a destination the transaction does
 * not pay, or a deposit amount the kVault instruction does not carry, keeps the generic screen
 * rather than being dressed up as something this device could not confirm (issue #5644).
 *
 * Withdraw amounts are deliberately not compared — the instruction carries the share count where
 * the payload carries the tokens those shares are worth, and the rate between them is not something
 * a joining device can pin down.
 */
internal fun KaminoRelayedIntent.takeIfDescribedBy(payload: KeysignPayload): KaminoRelayedIntent? {
    val expectedDestination = kaminoDestinationAddress(vault, action, payload.coin.address)
    if (payload.toAddress != expectedDestination) {
        Timber.w("Kamino payload names a destination its own transaction does not pay")
        return null
    }
    if (action == KaminoAction.DEPOSIT && amount != payload.toAmount) {
        Timber.w("Kamino payload states an amount its own transaction does not carry")
        return null
    }
    return this
}
