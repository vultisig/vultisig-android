package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.ThorChainPoolJson
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The payout-asset list behind the referral edit (issue #5684). Thornode refuses a THORName whose
 * preferred asset is not an available pool, so a staged or suspended pool must never be offered.
 */
class GetThorChainPoolAssetsUseCaseTest {

    private val thorChainApi = mockk<ThorChainApi>()
    private val useCase = GetThorChainPoolAssetsUseCaseImpl(thorChainApi)

    @Test
    fun `offers available pools only, ordered by ticker`() = runTest {
        coEvery { thorChainApi.getPools() } returns
            listOf(
                pool("ETH.ETH", "Available"),
                pool("ETH.DPI-0X1494CA1F11D487C2BBE4543E90080AEBA4BA3C2B", "Staged"),
                pool("BTC.BTC", "Available"),
                pool("LTC.LTC", "Suspended"),
            )

        val assets = useCase()

        assertEquals(listOf("BTC.BTC", "ETH.ETH"), assets.map { it.asset })
    }

    @Test
    fun `drops pools on chains the wallet cannot hold`() = runTest {
        coEvery { thorChainApi.getPools() } returns
            listOf(pool("BTC.BTC", "Available"), pool("ALEO.ALEO", "Available"))

        val assets = useCase()

        assertEquals(listOf("BTC.BTC"), assets.map { it.asset })
    }

    private fun pool(asset: String, status: String) =
        ThorChainPoolJson(asset = asset, assetTorPrice = BigInteger.ZERO, status = status)
}
