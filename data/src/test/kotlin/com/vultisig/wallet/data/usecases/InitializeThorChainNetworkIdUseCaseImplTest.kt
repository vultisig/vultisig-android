package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.crypto.ThorChainHelper
import com.vultisig.wallet.data.repositories.ThorChainRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class InitializeThorChainNetworkIdUseCaseImplTest {

    private val thorChainRepository: ThorChainRepository = mockk(relaxed = true)
    private val vaultRepository: VaultRepository = mockk(relaxed = true)

    private lateinit var useCase: InitializeThorChainNetworkIdUseCaseImpl

    private var originalNetworkId: String = ThorChainHelper.THORCHAIN_NETWORK_ID

    @BeforeEach
    fun setUp() {
        originalNetworkId = ThorChainHelper.THORCHAIN_NETWORK_ID
        useCase = InitializeThorChainNetworkIdUseCaseImpl(thorChainRepository, vaultRepository)
    }

    @AfterEach
    fun tearDown() {
        ThorChainHelper.THORCHAIN_NETWORK_ID = originalNetworkId
    }

    @Test
    fun `skips fetch and cache when no vaults exist`() = runTest {
        ThorChainHelper.THORCHAIN_NETWORK_ID = "default-id"
        coEvery { vaultRepository.hasVaults() } returns false

        useCase()

        coVerify(exactly = 0) { thorChainRepository.getCachedNetworkChainId() }
        coVerify(exactly = 0) { thorChainRepository.fetchNetworkChainId() }
        assertEquals("default-id", ThorChainHelper.THORCHAIN_NETWORK_ID)
    }

    @Test
    fun `applies cached value then fetches when vaults exist`() = runTest {
        coEvery { vaultRepository.hasVaults() } returns true
        coEvery { thorChainRepository.getCachedNetworkChainId() } returns "thorchain-cached"
        coEvery { thorChainRepository.fetchNetworkChainId() } returns "thorchain-fresh"

        useCase()

        coVerify(exactly = 1) { thorChainRepository.getCachedNetworkChainId() }
        coVerify(exactly = 1) { thorChainRepository.fetchNetworkChainId() }
        assertEquals("thorchain-fresh", ThorChainHelper.THORCHAIN_NETWORK_ID)
    }

    @Test
    fun `keeps cached value when fetch fails`() = runTest {
        coEvery { vaultRepository.hasVaults() } returns true
        coEvery { thorChainRepository.getCachedNetworkChainId() } returns "thorchain-cached"
        coEvery { thorChainRepository.fetchNetworkChainId() } throws RuntimeException("boom")

        useCase()

        assertEquals("thorchain-cached", ThorChainHelper.THORCHAIN_NETWORK_ID)
    }
}
