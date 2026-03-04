package com.vultisig.wallet.ui.models

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

@Singleton
internal class AddressProvider @Inject constructor() {

    val address = MutableStateFlow("")

    fun update(value: String) {
        address.update { value }
    }

    fun clean() {
        address.update { "" }
    }
}
