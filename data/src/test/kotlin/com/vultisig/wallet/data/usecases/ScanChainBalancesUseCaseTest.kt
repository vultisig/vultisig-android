package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.DerivationPath
import com.vultisig.wallet.data.repositories.TokenRepository
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Regression guard for #4453: a Phantom-derived Solana wallet must resolve to a different address
 * than the Default path for the same seed, or a Phantom import silently controls the wrong account.
 * Exercises the real WalletCore derivation behind [ScanChainBalancesUseCase] (JNI), skipped when
 * the native library is unavailable — same `skipIfJniUnavailable`/`assumeTrue` pattern as
 * `UtxoHelperTest`. Expected addresses were pinned directly against WalletCore 4.7.0 (this module's
 * exact dependency version).
 */
internal class ScanChainBalancesUseCaseTest {

    // The standard all-"abandon" BIP-39 test mnemonic, no passphrase.
    private val mnemonic =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
            "abandon about"

    // Balance lookups are deliberately left unstubbed: any getNativeToken/getTokenValue call
    // throws, and ScanChainBalancesUseCase already treats that as hasBalance = false, so this test
    // can pin address derivation without wiring balance behavior for every scanned chain.
    private val useCase =
        ScanChainBalancesUseCaseImpl(
            balanceRepository = mockk<BalanceRepository>(),
            tokenRepository = mockk<TokenRepository>(),
        )

    @Test
    fun `Solana Default and Phantom derivation paths pin to their known-correct addresses`() =
        runTest {
            try {
                val solanaResults = useCase(mnemonic).filter { it.chain == Chain.Solana }

                assertEquals(
                    "GjJyeC1r2RgkuoCWMyPYkCWSGSGLcz266EaAkLA27AhL",
                    solanaResults.first { it.derivationPath == DerivationPath.Default }.address,
                )
                assertEquals(
                    "HAgk14JpMQLgt6rVgv7cBQFJWFto5Dqxi472uT3DKpqk",
                    solanaResults.first { it.derivationPath == DerivationPath.Phantom }.address,
                )
            } catch (e: Throwable) {
                skipIfJniUnavailable(e)
            }
        }

    private fun skipIfJniUnavailable(e: Throwable) {
        if (
            e is UnsatisfiedLinkError ||
                e is ExceptionInInitializerError ||
                e is NoClassDefFoundError
        ) {
            assumeTrue(false, "WalletCore JNI not available: ${e.message}")
        } else throw e
    }
}
