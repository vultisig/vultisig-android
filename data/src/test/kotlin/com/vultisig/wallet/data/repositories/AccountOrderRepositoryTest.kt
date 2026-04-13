package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.AccountOrderDao
import com.vultisig.wallet.data.db.models.AccountOrderEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class AccountOrderRepositoryTest {

    private lateinit var dao: AccountOrderDao
    private lateinit var repository: AccountOrderRepository

    private val vaultId = "vault-1"

    @BeforeEach
    fun setUp() {
        dao = mockk(relaxed = true)
        repository = AccountOrderRepository(dao)
    }

    @Test
    fun `loadOrders delegates to dao`() = runTest {
        val orders =
            listOf(
                AccountOrderEntity(
                    vaultId = vaultId,
                    chain = "Bitcoin",
                    order = 0f,
                    isPinned = true,
                ),
                AccountOrderEntity(
                    vaultId = vaultId,
                    chain = "Ethereum",
                    order = 1f,
                    isPinned = false,
                ),
            )
        coEvery { dao.loadOrders(vaultId) } returns flowOf(orders)

        val result = repository.loadOrders(vaultId).first()

        assertEquals(2, result.size)
        assertEquals("Bitcoin", result[0].chain)
        assertTrue(result[0].isPinned)
    }

    @Test
    fun `saveOrders deletes then inserts`() = runTest {
        val orders =
            listOf(
                AccountOrderEntity(
                    vaultId = vaultId,
                    chain = "Bitcoin",
                    order = 0f,
                    isPinned = true,
                )
            )

        repository.saveOrders(vaultId, orders)

        coVerifyOrder {
            dao.deleteAll(vaultId)
            dao.insertAll(orders)
        }
    }

    @Test
    fun `saveOrders with empty list still deletes old data`() = runTest {
        repository.saveOrders(vaultId, emptyList())

        coVerify { dao.deleteAll(vaultId) }
        coVerify { dao.insertAll(emptyList()) }
    }

    @Test
    fun `loadOrders returns empty flow when no orders saved`() = runTest {
        coEvery { dao.loadOrders(vaultId) } returns flowOf(emptyList())

        val result = repository.loadOrders(vaultId).first()

        assertTrue(result.isEmpty())
    }
}
