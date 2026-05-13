package com.vultisig.wallet.data.usecases.backup

import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.ServerBackupResult
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RequestServerBackupUseCaseTest {

    private lateinit var vaultRepository: VaultRepository
    private lateinit var vultiSignerRepository: VultiSignerRepository
    private lateinit var useCase: RequestServerBackupUseCaseImpl

    private val testVaultId = "vault-123"
    private val testPubKeyECDSA = "pub-key-ecdsa-123"
    private val testEmail = "user@example.com"
    private val testPassword = "password123"

    @BeforeEach
    fun setUp() {
        vaultRepository = mockk()
        vultiSignerRepository = mockk()
        useCase =
            RequestServerBackupUseCaseImpl(
                vaultRepository = vaultRepository,
                vultiSignerRepository = vultiSignerRepository,
            )
    }

    @Test
    fun `invoke returns success when repository succeeds`() = runTest {
        val vault = mockk<Vault> { every { pubKeyECDSA } returns testPubKeyECDSA }
        coEvery { vaultRepository.get(testVaultId) } returns vault
        coEvery {
            vultiSignerRepository.requestServerBackup(testPubKeyECDSA, testEmail, testPassword)
        } returns ServerBackupResult.Success

        val result = useCase(testVaultId, testEmail, testPassword)

        assertEquals(ServerBackupResult.Success, result)
        coVerify {
            vultiSignerRepository.requestServerBackup(testPubKeyECDSA, testEmail, testPassword)
        }
    }

    @Test
    fun `invoke returns error when vault not found`() = runTest {
        coEvery { vaultRepository.get(testVaultId) } returns null

        val result = useCase(testVaultId, testEmail, testPassword)

        assertEquals(ServerBackupResult.Error(ServerBackupResult.ErrorType.UNKNOWN), result)
        coVerify(exactly = 0) { vultiSignerRepository.requestServerBackup(any(), any(), any()) }
    }

    @Test
    fun `invoke returns invalid password error from repository`() = runTest {
        val vault = mockk<Vault> { every { pubKeyECDSA } returns testPubKeyECDSA }
        coEvery { vaultRepository.get(testVaultId) } returns vault
        coEvery {
            vultiSignerRepository.requestServerBackup(testPubKeyECDSA, testEmail, testPassword)
        } returns ServerBackupResult.Error(ServerBackupResult.ErrorType.INVALID_PASSWORD)

        val result = useCase(testVaultId, testEmail, testPassword)

        assertEquals(
            ServerBackupResult.Error(ServerBackupResult.ErrorType.INVALID_PASSWORD),
            result,
        )
    }

    @Test
    fun `invoke returns network error from repository`() = runTest {
        val vault = mockk<Vault> { every { pubKeyECDSA } returns testPubKeyECDSA }
        coEvery { vaultRepository.get(testVaultId) } returns vault
        coEvery {
            vultiSignerRepository.requestServerBackup(testPubKeyECDSA, testEmail, testPassword)
        } returns ServerBackupResult.Error(ServerBackupResult.ErrorType.NETWORK_ERROR)

        val result = useCase(testVaultId, testEmail, testPassword)

        assertEquals(ServerBackupResult.Error(ServerBackupResult.ErrorType.NETWORK_ERROR), result)
    }

    @Test
    fun `invoke returns too many requests error from repository`() = runTest {
        val vault = mockk<Vault> { every { pubKeyECDSA } returns testPubKeyECDSA }
        coEvery { vaultRepository.get(testVaultId) } returns vault
        coEvery {
            vultiSignerRepository.requestServerBackup(testPubKeyECDSA, testEmail, testPassword)
        } returns ServerBackupResult.Error(ServerBackupResult.ErrorType.TOO_MANY_REQUESTS)

        val result = useCase(testVaultId, testEmail, testPassword)

        assertEquals(
            ServerBackupResult.Error(ServerBackupResult.ErrorType.TOO_MANY_REQUESTS),
            result,
        )
    }

    @Test
    fun `invoke returns bad request error from repository`() = runTest {
        val vault = mockk<Vault> { every { pubKeyECDSA } returns testPubKeyECDSA }
        coEvery { vaultRepository.get(testVaultId) } returns vault
        coEvery {
            vultiSignerRepository.requestServerBackup(testPubKeyECDSA, testEmail, testPassword)
        } returns ServerBackupResult.Error(ServerBackupResult.ErrorType.BAD_REQUEST)

        val result = useCase(testVaultId, testEmail, testPassword)

        assertEquals(ServerBackupResult.Error(ServerBackupResult.ErrorType.BAD_REQUEST), result)
    }

    @Test
    fun `invoke returns unknown error from repository on server errors`() = runTest {
        val vault = mockk<Vault> { every { pubKeyECDSA } returns testPubKeyECDSA }
        coEvery { vaultRepository.get(testVaultId) } returns vault
        coEvery {
            vultiSignerRepository.requestServerBackup(testPubKeyECDSA, testEmail, testPassword)
        } returns ServerBackupResult.Error(ServerBackupResult.ErrorType.UNKNOWN)

        val result = useCase(testVaultId, testEmail, testPassword)

        assertEquals(ServerBackupResult.Error(ServerBackupResult.ErrorType.UNKNOWN), result)
    }

    @Test
    fun `invoke passes correct public key from vault`() = runTest {
        val specificPubKey = "specific-ecdsa-key-456"
        val vault = mockk<Vault> { every { pubKeyECDSA } returns specificPubKey }
        coEvery { vaultRepository.get(testVaultId) } returns vault
        coEvery { vultiSignerRepository.requestServerBackup(any(), any(), any()) } returns
            ServerBackupResult.Success

        useCase(testVaultId, testEmail, testPassword)

        coVerify {
            vultiSignerRepository.requestServerBackup(specificPubKey, testEmail, testPassword)
        }
    }
}
