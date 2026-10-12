package com.vultisig.wallet.data.blockchain.solana.staking

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StakewizValidatorJsonTest {

    @Test
    fun `apy comes from total_apy, not the inflated apy_estimate`() {
        val row =
            Json { ignoreUnknownKeys = true }
                .decodeFromString<StakewizValidatorJson>(
                    """{"vote_identity":"V1","apy_estimate":8.14,"total_apy":5.21}"""
                )

        assertEquals(5.21, row.apyEstimate)
    }
}
