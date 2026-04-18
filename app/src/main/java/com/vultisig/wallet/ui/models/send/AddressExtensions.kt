package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain

internal fun List<Address>.firstSendSrc(selectedTokenId: String?, filterByChain: Chain?): SendSrc {
    val address =
        when {
            !selectedTokenId.isNullOrBlank() ->
                first { it -> it.accounts.any { it.token.id == selectedTokenId } }

            filterByChain != null -> first { it.chain == filterByChain }
            else -> first()
        }

    val account =
        when {
            !selectedTokenId.isNullOrBlank() ->
                address.accounts.first { it.token.id == selectedTokenId }
            filterByChain != null -> address.accounts.first { it.token.isNativeToken }
            else -> address.accounts.first()
        }

    return SendSrc(address, account)
}

internal fun List<Address>.findCurrentSrc(selectedTokenId: String?, currentSrc: SendSrc): SendSrc {
    if (selectedTokenId == null) {
        val selectedAddress = currentSrc.address
        val selectedAccount = currentSrc.account
        val address = first {
            it.chain == selectedAddress.chain && it.address == selectedAddress.address
        }
        return SendSrc(
            address,
            address.accounts.first { it.token.ticker == selectedAccount.token.ticker },
        )
    } else {
        return firstSendSrc(selectedTokenId, null)
    }
}
