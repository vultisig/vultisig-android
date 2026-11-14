package com.vultisig.wallet.ui.models.deposit

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

internal class PairedLpAddressTest {

    @Test
    fun `the held native coin names the paired address`() {
        val vault =
            Vault(id = "v", name = "v", coins = listOf(Coins.Bitcoin.BTC.copy(address = BTC)))

        assertEquals(BTC, vault.pairedLpAddress(Chain.Bitcoin))
    }

    @Test
    fun `a key-import vault without the chain names nothing`() {
        val vault =
            Vault(
                id = "v",
                name = "v",
                libType = SigningLibType.KeyImport,
                pubKeyECDSA = "02".padEnd(66, '1'),
                coins = listOf(Coins.MayaChain.CACAO.copy(address = "maya1self")),
            )

        assertNull(vault.pairedLpAddress(Chain.Bitcoin))
    }

    @Test
    fun `a held token on the chain is not the native half`() {
        val vault =
            Vault(
                id = "v",
                name = "v",
                coins = listOf(Coins.Ethereum.AAVE.copy(address = "0xself")),
            )

        assertNull(vault.pairedLpAddress(Chain.Ethereum))
    }

    private companion object {
        const val BTC = "bc1qself"
    }
}
