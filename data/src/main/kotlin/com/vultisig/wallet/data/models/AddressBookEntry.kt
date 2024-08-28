package com.vultisig.wallet.data.models

data class AddressBookEntry(
    val chain: Chain,
    val address: String,
    val title: String,
) {

    val id: String
        get() = "${chain.id}-$address"

}