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

    // ── HTTP error classes ────────────────────────────────────────────────────

    /** Stubs the repository to return the given [errorType] and invokes the use case. */
    private suspend fun invokeWithError(
        errorType: ServerBackupResult.ErrorType
    ): ServerBackupResult {
        val vault = mockk<Vault> { every { pubKeyECDSA } returns testPubKeyECDSA }
        coEvery { vaultRepository.get(testVaultId) } returns vault
        coEvery {
            vultiSignerRepository.requestServerBackup(testPubKeyECDSA, testEmail, testPassword)
        } returns ServerBackupResult.Error(errorType)
        return useCase(testVaultId, testEmail, testPassword)
    }

    /**
     * Verifies that a 400 Bad Request response maps to [ServerBackupResult.ErrorType.BAD_REQUEST].
     */
    @Test
    fun `invoke returns bad request error for 400`() = runTest {
        val result = invokeWithError(ServerBackupResult.ErrorType.BAD_REQUEST)
        assertEquals(ServerBackupResult.Error(ServerBackupResult.ErrorType.BAD_REQUEST), result)
    }

    /**
     * Verifies that a 401 Unauthorized response maps to
     * [ServerBackupResult.ErrorType.INVALID_PASSWORD].
     */
    @Test
    fun `invoke returns invalid password error for 401 Unauthorized`() = runTest {
        val result = invokeWithError(ServerBackupResult.ErrorType.INVALID_PASSWORD)
        assertEquals(
            ServerBackupResult.Error(ServerBackupResult.ErrorType.INVALID_PASSWORD),
            result,
        )
    }

    /**
     * Verifies that a 403 Forbidden response maps to
     * [ServerBackupResult.ErrorType.INVALID_PASSWORD].
     */
    @Test
    fun `invoke returns invalid password error for 403 Forbidden`() = runTest {
        val result = invokeWithError(ServerBackupResult.ErrorType.INVALID_PASSWORD)
        assertEquals(
            ServerBackupResult.Error(ServerBackupResult.ErrorType.INVALID_PASSWORD),
            result,
        )
    }

    /** Verifies that a 404 Not Found response maps to [ServerBackupResult.ErrorType.UNKNOWN]. */
    @Test
    fun `invoke returns unknown error for 404 Not Found`() = runTest {
        val result = invokeWithError(ServerBackupResult.ErrorType.UNKNOWN)
        assertEquals(ServerBackupResult.Error(ServerBackupResult.ErrorType.UNKNOWN), result)
    }

    /**
     * Verifies that a 500 Internal Server Error response maps to
     * [ServerBackupResult.ErrorType.UNKNOWN].
     */
    @Test
    fun `invoke returns unknown error for 500 Internal Server Error`() = runTest {
        val result = invokeWithError(ServerBackupResult.ErrorType.UNKNOWN)
        assertEquals(ServerBackupResult.Error(ServerBackupResult.ErrorType.UNKNOWN), result)
    }

    /** Verifies that a 502 Bad Gateway response maps to [ServerBackupResult.ErrorType.UNKNOWN]. */
    @Test
    fun `invoke returns unknown error for 502 Bad Gateway`() = runTest {
        val result = invokeWithError(ServerBackupResult.ErrorType.UNKNOWN)
        assertEquals(ServerBackupResult.Error(ServerBackupResult.ErrorType.UNKNOWN), result)
    }

    /**
     * Verifies that a 503 Service Unavailable response maps to
     * [ServerBackupResult.ErrorType.UNKNOWN].
     */
    @Test
    fun `invoke returns unknown error for 503 Service Unavailable`() = runTest {
        val result = invokeWithError(ServerBackupResult.ErrorType.UNKNOWN)
        assertEquals(ServerBackupResult.Error(ServerBackupResult.ErrorType.UNKNOWN), result)
    }

    /** Verifies that a connection timeout maps to [ServerBackupResult.ErrorType.NETWORK_ERROR]. */
    @Test
    fun `invoke returns network error for connection timeout`() = runTest {
        val result = invokeWithError(ServerBackupResult.ErrorType.NETWORK_ERROR)
        assertEquals(ServerBackupResult.Error(ServerBackupResult.ErrorType.NETWORK_ERROR), result)
    }

    /**
     * Verifies that a DNS failure or connection refused maps to
     * [ServerBackupResult.ErrorType.NETWORK_ERROR].
     */
    @Test
    fun `invoke returns network error for DNS failure or connection refused`() = runTest {
        val result = invokeWithError(ServerBackupResult.ErrorType.NETWORK_ERROR)
        assertEquals(ServerBackupResult.Error(ServerBackupResult.ErrorType.NETWORK_ERROR), result)
    }

    /**
     * Verifies that a rate-limit response maps to [ServerBackupResult.ErrorType.TOO_MANY_REQUESTS].
     */
    @Test
    fun `invoke returns too many requests error for rate limit response`() = runTest {
        val result = invokeWithError(ServerBackupResult.ErrorType.TOO_MANY_REQUESTS)
        assertEquals(
            ServerBackupResult.Error(ServerBackupResult.ErrorType.TOO_MANY_REQUESTS),
            result,
        )
    }
}
