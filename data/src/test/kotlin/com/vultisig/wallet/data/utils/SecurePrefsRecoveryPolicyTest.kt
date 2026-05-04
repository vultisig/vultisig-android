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

    // KPIE lives in android.security.keystore; the JVM test classpath binds an Android stub jar
    // whose constructors throw RuntimeException("Stub!"). MockK gives a real subtype instance that
    // satisfies the `is` check without triggering the stub.
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

    // Pins the deliberate no-unwrap policy: a wrapped KPIE is reported via its outer type and is
    // therefore on a non-KPIE catch path, which the hard rule for #4401 forbids from destroying
    // user data. If we ever decide to honor wrapped KPIE, the production catch must change too.
    @Test
    fun `KPIE wrapped as cause of IOException does not trigger destructive recovery`() {
        assertFalse(shouldDestructivelyRecover(IOException("oem keystore wrapper", kpie)))
    }
}
