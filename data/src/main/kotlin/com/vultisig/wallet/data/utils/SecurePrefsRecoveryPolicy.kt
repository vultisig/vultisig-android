package com.vultisig.wallet.data.utils

import android.security.keystore.KeyPermanentlyInvalidatedException

/**
 * Decides whether a failure raised while opening the encrypted [android.content.SharedPreferences]
 * justifies the destructive recovery path (delete prefs file, delete the AndroidKeyStore alias,
 * clear the migration sentinel).
 *
 * Returns `true` ONLY for [KeyPermanentlyInvalidatedException], which signals that the
 * AndroidKeyStore entry backing the prefs cannot decrypt the existing payload anymore (e.g. after
 * the user changes device credentials), so the encrypted file is unreadable for the remainder of
 * its lifetime and recovery is the only forward path. Returns `false` for every other throwable —
 * including transient [java.security.GeneralSecurityException] (keystore daemon stalls on certain
 * OEM ROMs) and [java.io.IOException] (disk hiccups) — so the user's encrypted state is preserved
 * and the failure surfaces as a deterministic crash for telemetry.
 *
 * See issue vultisig/vultisig-android#4401: the previous policy invoked destructive recovery on any
 * `GeneralSecurityException` or `IOException`, occasionally wiping a user's encrypted state on a
 * single transient stall.
 */
internal fun shouldDestructivelyRecover(cause: Throwable): Boolean =
    cause is KeyPermanentlyInvalidatedException
