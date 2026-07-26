package com.vultisig.wallet.data.models

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.Test

internal class CoinsPolContractAddressTest {

    @Test
    fun `Ethereum POL points at the real POL contract, not the legacy MATIC contract`() {
        assertEquals(
            "0x455e53CBB86018Ac2B8092FdCd39d8444aFFC3F6",
            Coins.Ethereum.POL.contractAddress,
        )
        assertNotEquals(Coins.Ethereum.MATIC.contractAddress, Coins.Ethereum.POL.contractAddress)
    }

    @Test
    fun `Ethereum MATIC keeps its legacy contract address unchanged`() {
        assertEquals(
            "0x7d1afa7b718fb893db30a3abc0cfc608aacfebb0",
            Coins.Ethereum.MATIC.contractAddress,
        )
    }
}
