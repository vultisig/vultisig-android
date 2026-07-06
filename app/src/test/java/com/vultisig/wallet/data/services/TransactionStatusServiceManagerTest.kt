package com.vultisig.wallet.data.services

import io.kotest.matchers.nulls.shouldNotBeNull
import javax.inject.Singleton
import org.junit.jupiter.api.Test

internal class TransactionStatusServiceManagerTest {

    // Regression guard: KeysignTxStatusPoller (which binds the running foreground service) and
    // KeysignFlowViewModel.complete() (which cancels its notification) each inject this class
    // separately. Without @Singleton, Hilt hands each site its own never-bound instance, so
    // completion silently no-ops instead of reaching the service that's actually running.
    @Test
    fun `is scoped as a singleton so every injection site shares the bound instance`() {
        val scope = TransactionStatusServiceManager::class.java.getAnnotation(Singleton::class.java)

        scope.shouldNotBeNull()
    }
}
