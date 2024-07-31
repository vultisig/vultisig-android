package com.vultisig.wallet.data.models

import com.vultisig.wallet.models.Chain

internal data class AddressBookEntry(
    val chain: Chain,
    val address: String,
    val title: String,
) {

    val id: String
        get() = "${chain.id}-$address"

}