package com.vultisig.wallet.data

/**
 * Loads WalletCore's native library for instrumented tests in this module.
 *
 * Production code loads it from `:app`, which `:data`'s test APK never runs, so any test touching a
 * `wallet.core.jni` class has to load it itself or fail with `UnsatisfiedLinkError`.
 */
object WalletCoreNative {

    private val loaded: Unit by lazy { System.loadLibrary("TrustWalletCore") }

    /** Idempotent; safe to call from every test class that needs the JNI bindings. */
    fun ensureLoaded() = loaded
}
