package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.utils.TextFieldUtils
import javax.inject.Inject

interface IsVaultNameValid : (String) -> Boolean

internal class IsVaultNameValidImpl @Inject constructor() : IsVaultNameValid {
    override fun invoke(name: String): Boolean {
        return (name.length <= TextFieldUtils.VAULT_NAME_MAX_LENGTH && name.isNotEmpty())
    }
}