package com.vultisig.wallet.data.api.chains.ton

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

internal class JettonWalletsJsonTest {

    @Test
    fun `getMasterAddress returns the user-friendly master from the address book`() {
        val response =
            JettonWalletsJson(
                jettonWallets =
                    listOf(
                        JettonWalletJson(address = "0:wallet", jetton = "0:master", balance = "5")
                    ),
                addressBook = mapOf("0:master" to AddressEntryJson(userFriendly = "EQmaster")),
            )
        assertEquals("EQmaster", response.getMasterAddress())
    }

    @Test
    fun `getMasterAddress falls back to the raw master when it is not in the address book`() {
        val response =
            JettonWalletsJson(
                jettonWallets =
                    listOf(
                        JettonWalletJson(address = "0:wallet", jetton = "0:master", balance = "5")
                    ),
                addressBook = emptyMap(),
            )
        assertEquals("0:master", response.getMasterAddress())
    }

    @Test
    fun `getMasterAddress returns null when there are no wallets`() {
        assertNull(JettonWalletsJson().getMasterAddress())
    }
}
