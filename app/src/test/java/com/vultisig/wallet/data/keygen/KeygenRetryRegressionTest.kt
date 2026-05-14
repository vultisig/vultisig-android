package com.vultisig.wallet.data.keygen

import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssAction
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class KeygenRetryRegressionTest {

    @Test
    fun `new keygen execution stays off when feature flag is off`() {
        val isEnabled =
            shouldUseNewKeygenExecution(
                action = TssAction.KEYGEN,
                libType = SigningLibType.DKLS,
                isParallelKeygenFeatureEnabled = false,
            )

        assertEquals(false, isEnabled)
    }

    @Test
    fun `new keygen execution is enabled for DKLS keygen when feature flag is on`() {
        val isEnabled =
            shouldUseNewKeygenExecution(
                action = TssAction.KEYGEN,
                libType = SigningLibType.DKLS,
                isParallelKeygenFeatureEnabled = true,
            )

        assertEquals(true, isEnabled)
    }

    @Test
    fun `new keygen execution opts DKLS reshare in when the feature flag is on`() {
        val isEnabled =
            shouldUseNewKeygenExecution(
                action = TssAction.ReShare,
                libType = SigningLibType.DKLS,
                isParallelKeygenFeatureEnabled = true,
            )

        assertEquals(true, isEnabled)
    }

    @Test
    fun `non-DKLS reshare keeps the legacy path even when the feature flag is on`() {
        val isEnabled =
            shouldUseNewKeygenExecution(
                action = TssAction.ReShare,
                libType = SigningLibType.GG20,
                isParallelKeygenFeatureEnabled = true,
            )

        assertEquals(false, isEnabled)
    }

    @Test
    fun `KeyImport vault reshare opts into the new path when the flag is on`() {
        // Mirrors iOS PR #4139: imported seed-phrase vaults also use DKLS / Schnorr threshold
        // protocols at the root level, so they are batch-eligible for reshare.
        val isEnabled =
            shouldUseNewKeygenExecution(
                action = TssAction.ReShare,
                libType = SigningLibType.KeyImport,
                isParallelKeygenFeatureEnabled = true,
            )

        assertEquals(true, isEnabled)
    }

    @Test
    fun `isBatchEligibleReshare matches iOS supportsBatch predicate`() {
        // iOS: `vault.libType == .DKLS || vault.libType == .KeyImport`
        assertEquals(true, isBatchEligibleReshare(TssAction.ReShare, SigningLibType.DKLS))
        assertEquals(true, isBatchEligibleReshare(TssAction.ReShare, SigningLibType.KeyImport))
        assertEquals(false, isBatchEligibleReshare(TssAction.ReShare, SigningLibType.GG20))
    }

    @Test
    fun `isBatchEligibleReshare rejects non-reshare actions`() {
        // Migrate shares the proto with reshare but is NOT batch-eligible.
        assertEquals(false, isBatchEligibleReshare(TssAction.Migrate, SigningLibType.DKLS))
        assertEquals(false, isBatchEligibleReshare(TssAction.KEYGEN, SigningLibType.DKLS))
        assertEquals(false, isBatchEligibleReshare(TssAction.KeyImport, SigningLibType.KeyImport))
        assertEquals(false, isBatchEligibleReshare(TssAction.SingleKeygen, SigningLibType.DKLS))
    }

    @Test
    fun `reshare stays on the legacy path when the feature flag is off`() {
        val isEnabled =
            shouldUseNewKeygenExecution(
                action = TssAction.ReShare,
                libType = SigningLibType.DKLS,
                isParallelKeygenFeatureEnabled = false,
            )

        assertEquals(false, isEnabled)
    }

    @Test
    fun `new keygen execution is enabled for key import when feature flag is on`() {
        val isEnabled =
            shouldUseNewKeygenExecution(
                action = TssAction.KeyImport,
                libType = SigningLibType.KeyImport,
                isParallelKeygenFeatureEnabled = true,
            )

        assertEquals(true, isEnabled)
    }

    @Test
    fun `new keygen execution is enabled for mldsa when feature flag is on`() {
        val isEnabled =
            shouldUseNewKeygenExecution(
                action = TssAction.SingleKeygen,
                libType = SigningLibType.DKLS,
                isParallelKeygenFeatureEnabled = true,
            )

        assertEquals(true, isEnabled)
    }

    @Test
    fun `routing can separate shared setup from exchange namespace`() {
        val routing = KeygenRouting.from(exchangeMessageId = "ecdsa")

        assertEquals(KeygenRouting(exchangeMessageId = "ecdsa", setupMessageId = null), routing)
    }

    @Test
    fun `routing reuses one namespace for setup and exchange when requested`() {
        val routing = KeygenRouting.from(setupMessageId = "mldsa")

        assertEquals(KeygenRouting(exchangeMessageId = "mldsa", setupMessageId = "mldsa"), routing)
    }

    @Test
    fun `retry helper rethrows cancellation without retrying`() = runTest {
        var retryCount = 0

        assertFailsWith<CancellationException> {
            runKeygenWithRetry(
                attempt = 0,
                retry = { _, _ ->
                    retryCount += 1
                    Unit
                },
            ) {
                throw CancellationException("cancelled")
            }
        }
        assertEquals(0, retryCount)
    }

    @Test
    fun `retry helper retries regular failures up to the next attempt`() = runTest {
        val attempts = mutableListOf<Int>()

        val result =
            runKeygenWithRetry(
                attempt = 0,
                retry = { nextAttempt, _ ->
                    attempts += nextAttempt
                    if (nextAttempt == 1) "recovered" else error("unexpected retry")
                },
            ) {
                error("boom")
            }

        assertEquals(listOf(1), attempts)
        assertEquals("recovered", result)
    }

    @Test
    fun `shouldKeepExistingChaincode returns true only for KeyImport vaults`() {
        // KeyImport vaults store the mnemonic's BIP32 chaincode in vault.hexChainCode; QC reshare
        // must NOT overwrite it. Freshly-created DKLS / GG20 vaults can adopt the QC output safely
        // because their existing chaincode IS the DKLS output.
        assertEquals(true, shouldKeepExistingChaincode(SigningLibType.KeyImport))
        assertEquals(false, shouldKeepExistingChaincode(SigningLibType.DKLS))
        assertEquals(false, shouldKeepExistingChaincode(SigningLibType.GG20))
    }

    @Test
    fun `retry helper rethrows the original failure when the attempt ceiling is reached`() =
        runTest {
            var retryCount = 0

            val failure =
                assertFailsWith<IllegalStateException> {
                    runKeygenWithRetry(
                        attempt = 3,
                        maxAttempts = 3,
                        retry = { _, _ ->
                            retryCount += 1
                            Unit
                        },
                    ) {
                        error("terminal failure")
                    }
                }

            assertEquals("terminal failure", failure.message)
            assertEquals(0, retryCount)
        }
}
