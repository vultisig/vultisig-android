package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.Transaction
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class TransactionRepositoryImplTest {

    private val repository = TransactionRepositoryImpl()

    @Test
    fun `getTransaction returns null when repository is empty`() = runTest {
        assertNull(repository.getTransaction("missing"))
    }

    @Test
    fun `getTransaction returns null when id is unknown`() = runTest {
        repository.addTransaction(transaction("known"))

        assertNull(repository.getTransaction("missing"))
    }

    @Test
    fun `getTransaction returns the stored transaction`() = runTest {
        val stored = transaction("tx-1")
        repository.addTransaction(stored)

        assertSame(stored, repository.getTransaction("tx-1"))
    }

    @Test
    fun `addTransaction overwrites the previous entry for the same id`() = runTest {
        repository.addTransaction(transaction("tx-1"))
        val replacement = transaction("tx-1")
        repository.addTransaction(replacement)

        assertSame(replacement, repository.getTransaction("tx-1"))
    }

    @Test
    fun `multiple transactions are addressable independently`() = runTest {
        val first = transaction("a")
        val second = transaction("b")
        repository.addTransaction(first)
        repository.addTransaction(second)

        assertSame(first, repository.getTransaction("a"))
        assertSame(second, repository.getTransaction("b"))
    }

    private fun transaction(id: String): Transaction =
        mockk(relaxed = true) { every { this@mockk.id } returns id }
}
