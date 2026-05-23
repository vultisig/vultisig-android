package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.data.models.Account

internal sealed class AccountsLoadState {
    data object Uninitialized : AccountsLoadState()

    data class Loaded(val accounts: List<Account>) : AccountsLoadState()
}

internal val AccountsLoadState.accountsOrEmpty: List<Account>
    get() = if (this is AccountsLoadState.Loaded) accounts else emptyList()
