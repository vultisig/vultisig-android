package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.OnChainSecurityScannerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class SecurityScannerServiceTest {

    @Test
    fun `disabled setting prevents transaction verdict requests`() = runTest {
        val scannerRepository = mockk<OnChainSecurityScannerRepository>()
        val provider = mockk<ProviderScannerServiceContract>()
        val scanner = SecurityScannerService(listOf(provider), scannerRepository, mockk())
        val transaction =
            SecurityScannerTransaction(
                chain = Chain.Ethereum,
                type = SecurityTransactionType.COIN_TRANSFER,
                from = "sender",
                to = "recipient",
            )

        every { scannerRepository.getSecurityScannerStatus() } returns false
        assertThrows<SecurityScannerException> { scanner.scanTransaction(transaction) }
        coVerify(exactly = 0) { provider.scanTransaction(any()) }

        val verdict = mockk<SecurityScannerResult>()
        every { scannerRepository.getSecurityScannerStatus() } returns true
        every { provider.getProviderName() } returns BLOCKAID_PROVIDER
        coEvery { provider.scanTransaction(transaction) } returns verdict
        assertSame(verdict, scanner.scanTransaction(transaction))
        coVerify(exactly = 1) { provider.scanTransaction(transaction) }
    }
}
