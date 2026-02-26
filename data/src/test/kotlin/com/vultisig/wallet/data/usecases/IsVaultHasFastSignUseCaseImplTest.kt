package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class IsVaultHasFastSignUseCaseImplTest {

    private lateinit var repository: VultiSignerRepository
    private lateinit var useCase: IsVaultHasFastSignUseCaseImpl

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = IsVaultHasFastSignUseCaseImpl(repository)
    }

    @Test
    fun `server vault returns false`() = runTest {
        val vault = vault(
            localPartyID = SERVER_PARTY,
            signers = listOf(SERVER_PARTY, OTHER_DEVICE),
        )

        assertFalse(useCase(vault))
    }

    @Test
    fun `server vault does not call API`() = runTest {
        val vault = vault(
            localPartyID = SERVER_PARTY,
            signers = listOf(SERVER_PARTY, OTHER_DEVICE),
        )

        useCase(vault)

        coVerify(exactly = 0) { repository.hasFastSign(any()) }
    }

    @Test
    fun `fast vault returns true without API call`() = runTest {
        val vault = vault(
            localPartyID = LOCAL_DEVICE,
            signers = listOf(LOCAL_DEVICE, SERVER_PARTY),
        )

        assertTrue(useCase(vault))
        coVerify(exactly = 0) { repository.hasFastSign(any()) }
    }

    @Test
    fun `secure vault returns true when API confirms fast sign`() = runTest {
        coEvery { repository.hasFastSign(ECDSA_KEY) } returns true

        assertTrue(useCase(vault()))
        coVerify(exactly = 1) { repository.hasFastSign(ECDSA_KEY) }
    }

    @Test
    fun `secure vault returns false when API denies fast sign`() = runTest {
        coEvery { repository.hasFastSign(ECDSA_KEY) } returns false

        assertFalse(useCase(vault()))
    }

    private fun vault(
        localPartyID: String = LOCAL_DEVICE,
        signers: List<String> = listOf(LOCAL_DEVICE, OTHER_DEVICE),
    ) = Vault(
        id = VAULT_ID,
        name = "Test",
        pubKeyECDSA = ECDSA_KEY,
        localPartyID = localPartyID,
        signers = signers,
    )

    private companion object {
        const val VAULT_ID = "vault-id"
        const val ECDSA_KEY = "ecdsa-key"
        const val LOCAL_DEVICE = "device-1"
        const val OTHER_DEVICE = "device-2"
        const val SERVER_PARTY = "Server-abc"
    }
}
