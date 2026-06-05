package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.CircleApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.ScaCircleAccountRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class HasCircleAccountUseCaseTest {

    private lateinit var scaCircleAccountRepository: ScaCircleAccountRepository
    private lateinit var circleApi: CircleApi
    private lateinit var vaultRepository: VaultRepository
    private lateinit var chainAccountAddressRepository: ChainAccountAddressRepository
    private lateinit var useCase: HasCircleAccountUseCaseImpl

    private val vaultId = "vault-1"
    private val evmAddress = "0xVaultEvmAddress"
    private val mscaAddress = "0xMscaAddress"

    @BeforeEach
    fun setUp() {
        scaCircleAccountRepository = mockk(relaxed = true)
        circleApi = mockk()
        vaultRepository = mockk()
        chainAccountAddressRepository = mockk()
        useCase =
            HasCircleAccountUseCaseImpl(
                scaCircleAccountRepository,
                circleApi,
                vaultRepository,
                chainAccountAddressRepository,
            )
    }

    @Test
    fun `returns true from cache without hitting network`() = runTest {
        coEvery { scaCircleAccountRepository.getAccount(vaultId) } returns mscaAddress

        assertTrue(useCase(vaultId))

        coVerify(exactly = 0) { circleApi.getScAccount(any()) }
    }

    @Test
    fun `discovers account on network, caches it, and returns true`() = runTest {
        val vault = mockk<Vault>()
        coEvery { scaCircleAccountRepository.getAccount(vaultId) } returns null
        coEvery { vaultRepository.get(vaultId) } returns vault
        coEvery { chainAccountAddressRepository.getAddress(Chain.Ethereum, vault) } returns
            (evmAddress to "")
        coEvery { circleApi.getScAccount(evmAddress) } returns mscaAddress

        assertTrue(useCase(vaultId))

        coVerify { scaCircleAccountRepository.saveAccount(vaultId, mscaAddress) }
    }

    @Test
    fun `returns false when no cache and network finds no account`() = runTest {
        val vault = mockk<Vault>()
        coEvery { scaCircleAccountRepository.getAccount(vaultId) } returns null
        coEvery { vaultRepository.get(vaultId) } returns vault
        coEvery { chainAccountAddressRepository.getAddress(Chain.Ethereum, vault) } returns
            (evmAddress to "")
        coEvery { circleApi.getScAccount(evmAddress) } returns null

        assertFalse(useCase(vaultId))
    }

    @Test
    fun `returns false when network discovery throws`() = runTest {
        val vault = mockk<Vault>()
        coEvery { scaCircleAccountRepository.getAccount(vaultId) } returns null
        coEvery { vaultRepository.get(vaultId) } returns vault
        coEvery { chainAccountAddressRepository.getAddress(Chain.Ethereum, vault) } returns
            (evmAddress to "")
        coEvery { circleApi.getScAccount(evmAddress) } throws RuntimeException("network down")

        assertFalse(useCase(vaultId))
    }

    @Test
    fun `returns false when vault not found`() = runTest {
        coEvery { scaCircleAccountRepository.getAccount(vaultId) } returns null
        coEvery { vaultRepository.get(vaultId) } returns null

        assertFalse(useCase(vaultId))
    }
}
