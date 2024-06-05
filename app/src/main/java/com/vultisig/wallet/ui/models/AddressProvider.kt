package com.vultisig.wallet.ui.models

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

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
