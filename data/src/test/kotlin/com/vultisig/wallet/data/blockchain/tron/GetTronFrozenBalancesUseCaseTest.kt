@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.blockchain.tron

import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.api.models.TronAccountJson
import com.vultisig.wallet.data.api.models.TronFrozenV2Json
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class GetTronFrozenBalancesUseCaseTest {

    private val tronApi: TronApi = mockk()
    private val useCase = GetTronFrozenBalancesUseCase(tronApi)

    @Test
    fun `invoke converts sun amounts to TRX and splits by resource type`() = runTest {
        coEvery { tronApi.getAccount(ADDRESS) } returns
            TronAccountJson(
                address = ADDRESS,
                frozenV2 =
                    listOf(
                        TronFrozenV2Json(type = "BANDWIDTH", amount = 1_500_000L),
                        TronFrozenV2Json(type = "ENERGY", amount = 2_750_000L),
                    ),
            )

        val result = useCase(ADDRESS)

        assertEquals(BigDecimal("1.500000"), result.bandwidthTrx)
        assertEquals(BigDecimal("2.750000"), result.energyTrx)
    }

    @Test
    fun `invoke treats a null frozen type as BANDWIDTH`() = runTest {
        coEvery { tronApi.getAccount(ADDRESS) } returns
            TronAccountJson(
                address = ADDRESS,
                frozenV2 = listOf(TronFrozenV2Json(type = null, amount = 5_000_000L)),
            )

        val result = useCase(ADDRESS)

        assertEquals(BigDecimal("5.000000"), result.bandwidthTrx)
        assertEquals(BigDecimal("0.000000"), result.energyTrx)
    }

    @Test
    fun `invoke sums multiple entries of the same resource`() = runTest {
        coEvery { tronApi.getAccount(ADDRESS) } returns
            TronAccountJson(
                address = ADDRESS,
                frozenV2 =
                    listOf(
                        TronFrozenV2Json(type = "BANDWIDTH", amount = 1_000_000L),
                        TronFrozenV2Json(type = "BANDWIDTH", amount = 2_000_000L),
                        TronFrozenV2Json(type = "ENERGY", amount = 500_000L),
                    ),
            )

        val result = useCase(ADDRESS)

        assertEquals(BigDecimal("3.000000"), result.bandwidthTrx)
        assertEquals(BigDecimal("0.500000"), result.energyTrx)
    }

    @Test
    fun `invoke returns zero for accounts with no frozen balance`() = runTest {
        coEvery { tronApi.getAccount(ADDRESS) } returns
            TronAccountJson(address = ADDRESS, frozenV2 = null)

        val result = useCase(ADDRESS)

        assertEquals(BigDecimal("0.000000"), result.bandwidthTrx)
        assertEquals(BigDecimal("0.000000"), result.energyTrx)
    }

    @Test
    fun `forResource routes BANDWIDTH and ENERGY to matching fields`() {
        val balances =
            TronFrozenBalances(bandwidthTrx = BigDecimal("1.1"), energyTrx = BigDecimal("2.2"))

        assertEquals(BigDecimal("1.1"), balances.forResource(TronResourceType.BANDWIDTH))
        assertEquals(BigDecimal("2.2"), balances.forResource(TronResourceType.ENERGY))
    }

    private companion object {
        const val ADDRESS = "TXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
    }
}
