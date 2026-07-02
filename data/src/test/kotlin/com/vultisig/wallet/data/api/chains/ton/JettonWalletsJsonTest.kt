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
        assertEquals("EQmaster", response.getMasterAddress("0:wallet"))
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
        assertEquals("0:master", response.getMasterAddress("0:wallet"))
    }

    @Test
    fun `getMasterAddress matches the wallet by its user-friendly address`() {
        val response =
            JettonWalletsJson(
                jettonWallets =
                    listOf(
                        JettonWalletJson(address = "0:wallet", jetton = "0:master", balance = "5")
                    ),
                addressBook =
                    mapOf(
                        "0:wallet" to AddressEntryJson(userFriendly = "EQwallet"),
                        "0:master" to AddressEntryJson(userFriendly = "EQmaster"),
                    ),
            )
        assertEquals("EQmaster", response.getMasterAddress("EQwallet"))
    }

    @Test
    fun `getMasterAddress returns null when no wallet matches`() {
        val response =
            JettonWalletsJson(
                jettonWallets =
                    listOf(
                        JettonWalletJson(address = "0:wallet", jetton = "0:master", balance = "5")
                    )
            )
        assertNull(response.getMasterAddress("0:other"))
    }

    @Test
    fun `getMasterAddress returns null when there are no wallets`() {
        assertNull(JettonWalletsJson().getMasterAddress("0:wallet"))
    }

    @Test
    fun `matchingWallet selects the wallet for the requested master, not the first`() {
        val response =
            JettonWalletsJson(
                jettonWallets =
                    listOf(
                        JettonWalletJson(
                            address = "0:ston",
                            jetton = "0:ston-master",
                            balance = "20000000",
                        ),
                        JettonWalletJson(
                            address = "0:usdt",
                            jetton = "0:usdt-master",
                            balance = "40886112",
                        ),
                    ),
                addressBook =
                    mapOf(
                        "0:ston-master" to AddressEntryJson(userFriendly = "EQston"),
                        "0:usdt-master" to AddressEntryJson(userFriendly = "EQusdt"),
                        "0:usdt" to AddressEntryJson(userFriendly = "EQusdtWallet"),
                    ),
            )

        // Requested master is the user-friendly USDT master, which is the second wallet.
        assertEquals("40886112", response.matchingWallet("EQusdt")?.balance)
        assertEquals("EQusdtWallet", response.getJettonsAddress("EQusdt"))
    }

    @Test
    fun `matchingWallet matches by raw jetton form`() {
        val response =
            JettonWalletsJson(
                jettonWallets =
                    listOf(
                        JettonWalletJson(
                            address = "0:usdt",
                            jetton = "0:usdt-master",
                            balance = "40886112",
                        )
                    )
            )
        assertEquals("40886112", response.matchingWallet("0:usdt-master")?.balance)
    }

    @Test
    fun `getJettonsAddress returns null when no wallet matches the master`() {
        val response =
            JettonWalletsJson(
                jettonWallets =
                    listOf(
                        JettonWalletJson(
                            address = "0:ston",
                            jetton = "0:ston-master",
                            balance = "20000000",
                        )
                    ),
                addressBook = mapOf("0:ston-master" to AddressEntryJson(userFriendly = "EQston")),
            )
        assertNull(response.getJettonsAddress("EQusdt"))
        assertNull(response.matchingWallet("EQusdt"))
    }
}
