package com.vultisig.wallet.data.repositories

import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class CustomMessagePayloadRepoImplTest {

    private val repository = CustomMessagePayloadRepoImpl()

    @Test
    fun `get returns null when repository is empty`() = runTest {
        assertNull(repository.get("missing"))
    }

    @Test
    fun `get returns null when id is unknown`() = runTest {
        repository.add(payload("known"))

        assertNull(repository.get("missing"))
    }

    @Test
    fun `get returns the stored payload`() = runTest {
        val stored = payload("p-1")
        repository.add(stored)

        assertSame(stored, repository.get("p-1"))
    }

    @Test
    fun `add overwrites the previous entry for the same id`() = runTest {
        repository.add(payload("p-1"))
        val replacement = payload("p-1")
        repository.add(replacement)

        assertSame(replacement, repository.get("p-1"))
    }

    @Test
    fun `multiple payloads are addressable independently`() = runTest {
        val first = payload("a")
        val second = payload("b")
        repository.add(first)
        repository.add(second)

        assertSame(first, repository.get("a"))
        assertSame(second, repository.get("b"))
    }

    private fun payload(id: String): CustomMessagePayloadDto =
        CustomMessagePayloadDto(id = id, vaultId = "vault-1", payload = mockk())
}
