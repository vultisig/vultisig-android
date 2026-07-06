package com.vultisig.wallet.data.usecases

import androidx.work.NetworkType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class DiscoverTokenUseCaseTest {

    @Test
    fun `refresh work request requires network connectivity`() {
        val request = buildRefreshWorkRequest(vaultId = "vault-1", chainId = "chain-1")

        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
    }
}
