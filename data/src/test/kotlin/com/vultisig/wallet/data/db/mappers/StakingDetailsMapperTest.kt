package com.vultisig.wallet.data.db.mappers

import com.vultisig.wallet.data.db.models.StakingDetailsEntity
import com.vultisig.wallet.data.models.Coins
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class StakingDetailsMapperTest {

    @Test
    fun `resolves a DeFi-only position coin that is absent from the token catalog`() {
        // sRUJI backs the auto-compounding RUJI card but must never appear as a wallet token, so
        // it lives outside Coins.coins — the cached position still has to round-trip (#5419).
        val details = listOf(entity(coinId = Coins.ThorChain.sRUJI.id)).toDomainModels()

        assertEquals(1, details.size)
        assertEquals(Coins.ThorChain.sRUJI, details.first().coin)
        assertEquals(BigInteger("1406899113878"), details.first().stakeAmount)
    }

    @Test
    fun `drops an unresolvable row instead of failing the whole vault read`() {
        val details =
            listOf(entity(coinId = "GONE-THORChain"), entity(coinId = Coins.ThorChain.RUJI.id))
                .toDomainModels()

        assertEquals(1, details.size)
        assertEquals(Coins.ThorChain.RUJI, details.first().coin)
    }

    @Test
    fun `keeps rewards coin optional`() {
        val details =
            listOf(entity(coinId = Coins.ThorChain.sRUJI.id, rewardsCoinId = null)).toDomainModels()

        assertTrue(details.first().rewardsCoin == null)
    }

    private fun entity(coinId: String, rewardsCoinId: String? = null) =
        StakingDetailsEntity(
            id = coinId,
            vaultId = "vault-1",
            coinId = coinId,
            stakeAmount = "1406899113878",
            apr = null,
            estimatedRewards = null,
            nextPayoutDate = null,
            rewards = null,
            rewardsCoinId = rewardsCoinId,
        )
}
