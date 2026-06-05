package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.repositories.ScaCircleAccountRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class HasCircleAccountUseCaseTest {

    private lateinit var scaCircleAccountRepository: ScaCircleAccountRepository
    private lateinit var useCase: HasCircleAccountUseCaseImpl

    private val vaultId = "vault-1"

    @BeforeEach
    fun setUp() {
        scaCircleAccountRepository = mockk()
        useCase = HasCircleAccountUseCaseImpl(scaCircleAccountRepository)
    }

    @Test
    fun `returns true when a Circle account is cached`() = runTest {
        coEvery { scaCircleAccountRepository.getAccount(vaultId) } returns "0xMscaAddress"

        assertTrue(useCase(vaultId))
    }

    @Test
    fun `returns false when no Circle account is cached`() = runTest {
        coEvery { scaCircleAccountRepository.getAccount(vaultId) } returns null

        assertFalse(useCase(vaultId))
    }

    @Test
    fun `returns false when the cache read fails`() = runTest {
        coEvery { scaCircleAccountRepository.getAccount(vaultId) } throws
            RuntimeException("datastore error")

        assertFalse(useCase(vaultId))
    }
}
