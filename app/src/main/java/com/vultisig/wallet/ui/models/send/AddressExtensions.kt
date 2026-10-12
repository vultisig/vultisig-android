package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import timber.log.Timber

/**
 * Returns the first [SendSrc] matching [selectedTokenId] or [filterByChain], falling back to the
 * first address. Returns null when no suitable address or account can be found.
 */
internal fun List<Address>.firstSendSrc(selectedTokenId: String?, filterByChain: Chain?): SendSrc? {
    val address =
        when {
            !selectedTokenId.isNullOrBlank() ->
                firstOrNull { it -> it.accounts.any { it.token.id == selectedTokenId } }
                    ?: run {
                        Timber.w("selectedTokenId %s not found", selectedTokenId)
                        firstOrNull()
                    }
                    ?: return null

            filterByChain != null -> firstOrNull { it.chain == filterByChain } ?: return null
            else -> firstOrNull() ?: return null
        }
    val account =
        when {
            !selectedTokenId.isNullOrBlank() ->
                address.accounts.firstOrNull { it.token.id == selectedTokenId }
                    ?: address.accounts.firstOrNull()
                    ?: return null

            filterByChain != null ->
                address.accounts.firstOrNull { it.token.isNativeToken } ?: return null

            else -> address.accounts.firstOrNull() ?: return null
        }

    return SendSrc(address, account)
}

/**
 * Re-resolves [currentSrc] from this updated list, or delegates to [firstSendSrc] when
 * [selectedTokenId] is set. Returns null when the current address or account can no longer be
 * found.
 */
internal fun List<Address>.findCurrentSrc(selectedTokenId: String?, currentSrc: SendSrc): SendSrc? {
    if (selectedTokenId == null) {
        val selectedAddress = currentSrc.address
        val selectedAccount = currentSrc.account
        val address =
            firstOrNull {
                it.chain == selectedAddress.chain && it.address == selectedAddress.address
            } ?: return null
        val account =
            address.accounts.firstOrNull { it.token.ticker == selectedAccount.token.ticker }
                ?: return null
        return SendSrc(address, account)
    } else {
        return firstSendSrc(selectedTokenId, null)
    }
}
