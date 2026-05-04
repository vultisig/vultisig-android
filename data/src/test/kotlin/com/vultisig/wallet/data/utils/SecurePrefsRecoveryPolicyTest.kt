/**
 * Verifies the policy encoded in [shouldDestructivelyRecover]: ONLY
 * [android.security.keystore.KeyPermanentlyInvalidatedException] may trigger destructive deletion
 * of EncryptedSharedPreferences; every other throwable — including superclasses, siblings, and
 * unrelated runtime exceptions — must NOT. Covers the hard rule in issue #4401.
 */
package com.vultisig.wallet.data.utils

import android.security.keystore.KeyPermanentlyInvalidatedException
import io.mockk.mockk
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.UnrecoverableKeyException
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SecurePrefsRecoveryPolicyTest {

    // KeyPermanentlyInvalidatedException lives in android.security.keystore.
    // The data/src/test source set runs on the JVM against Android stub jars where every Android
    // class constructor throws RuntimeException("Stub!"). Use mockk to obtain an instance without
    // triggering the stub, mirroring the pattern documented in QrShareInfoTest.kt.
    private val kpie: KeyPermanentlyInvalidatedException =
        mockk<KeyPermanentlyInvalidatedException>(relaxed = true)

    @Test
    fun `KeyPermanentlyInvalidatedException triggers destructive recovery`() {
        assertTrue(shouldDestructivelyRecover(kpie))
    }

    @Test
    fun `transient GeneralSecurityException does not trigger destructive recovery`() {
        assertFalse(shouldDestructivelyRecover(GeneralSecurityException("transient")))
    }

    @Test
    fun `IOException does not trigger destructive recovery`() {
        assertFalse(shouldDestructivelyRecover(IOException("disk")))
    }

    @Test
    fun `InvalidKeyException superclass alone does not trigger destructive recovery`() {
        assertFalse(shouldDestructivelyRecover(InvalidKeyException()))
    }

    @Test
    fun `UnrecoverableKeyException does not trigger destructive recovery`() {
        assertFalse(shouldDestructivelyRecover(UnrecoverableKeyException()))
    }

    @Test
    fun `IllegalStateException does not trigger destructive recovery`() {
        assertFalse(shouldDestructivelyRecover(IllegalStateException()))
    }

    @Test
    fun `RuntimeException does not trigger destructive recovery`() {
        assertFalse(shouldDestructivelyRecover(RuntimeException("unexpected crash")))
    }
}
