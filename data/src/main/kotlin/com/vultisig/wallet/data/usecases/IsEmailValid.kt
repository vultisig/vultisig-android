package com.vultisig.wallet.data.usecases

import android.util.Patterns
import javax.inject.Inject

interface IsEmailValid : (CharSequence) -> Boolean

internal class IsEmailValidImpl @Inject constructor() : IsEmailValid {
    override fun invoke(email: CharSequence): Boolean =
        Patterns.EMAIL_ADDRESS.matcher(email).matches()
}
