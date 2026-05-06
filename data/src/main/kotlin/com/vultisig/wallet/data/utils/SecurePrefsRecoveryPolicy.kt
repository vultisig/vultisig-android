package com.vultisig.wallet.data.utils

import android.security.keystore.KeyPermanentlyInvalidatedException

/**
 * Returns true only for [KeyPermanentlyInvalidatedException]; transient
 * [java.security.GeneralSecurityException] and [java.io.IOException] must propagate so a single
 * keystore stall cannot wipe encrypted state. See issue #4401.
 */
internal fun shouldDestructivelyRecover(cause: Throwable): Boolean =
    cause is KeyPermanentlyInvalidatedException
