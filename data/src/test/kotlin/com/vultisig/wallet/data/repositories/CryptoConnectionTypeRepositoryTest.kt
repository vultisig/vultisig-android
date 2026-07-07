package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.Chain
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class CryptoConnectionTypeRepositoryTest {

    private val repository = CryptoConnectionTypeRepositoryImpl()

    @Test
    fun `chains with a DeFi positions screen are recognised`() {
        // Terra / TerraClassic / QBTC must be present — otherwise the Cosmos staking view is
        // filtered out of the DeFi portfolio list and the user can never reach it.
        listOf(
                Chain.ThorChain,
                Chain.MayaChain,
                Chain.Tron,
                Chain.Ton,
                Chain.Terra,
                Chain.TerraClassic,
                Chain.Qbtc,
            )
            .forEach { chain -> assertTrue(repository.hasDeFiPositionsScreen(chain), chain.raw) }
    }

    @Test
    fun `chains without a DeFi positions screen are excluded`() {
        listOf(Chain.Bitcoin, Chain.Ethereum, Chain.Solana).forEach { chain ->
            assertFalse(repository.hasDeFiPositionsScreen(chain), chain.raw)
        }
    }
}
