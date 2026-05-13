package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.VultiSignerApi
import com.vultisig.wallet.data.api.utils.HttpException
import io.mockk.coEvery
import io.mockk.mockk
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class VultiSignerRepositoryImplBackupCodeTest {

    private val api: VultiSignerApi = mockk()
    private val repository = VultiSignerRepositoryImpl(api)

    @Test
    fun `isBackupCodeValid returns Valid when api succeeds`() = runTest {
        coEvery { api.verifyBackupCode("pub", "1234") } returns Unit

        assertEquals(BackupCodeVerifyResult.Valid, repository.isBackupCodeValid("pub", "1234"))
    }

    @Test
    fun `isBackupCodeValid maps HTTP 400 to Invalid`() = runTest {
        coEvery { api.verifyBackupCode("pub", "1234") } throws HttpException(400)

        assertEquals(BackupCodeVerifyResult.Invalid, repository.isBackupCodeValid("pub", "1234"))
    }

    @Test
    fun `isBackupCodeValid maps HTTP 401 to Invalid`() = runTest {
        coEvery { api.verifyBackupCode("pub", "1234") } throws HttpException(401)

        assertEquals(BackupCodeVerifyResult.Invalid, repository.isBackupCodeValid("pub", "1234"))
    }

    @Test
    fun `isBackupCodeValid maps HTTP 500 to NetworkError`() = runTest {
        coEvery { api.verifyBackupCode("pub", "1234") } throws HttpException(500)

        assertEquals(
            BackupCodeVerifyResult.NetworkError,
            repository.isBackupCodeValid("pub", "1234"),
        )
    }

    @Test
    fun `isBackupCodeValid maps HTTP 429 to NetworkError`() = runTest {
        coEvery { api.verifyBackupCode("pub", "1234") } throws HttpException(429)

        assertEquals(
            BackupCodeVerifyResult.NetworkError,
            repository.isBackupCodeValid("pub", "1234"),
        )
    }

    @Test
    fun `isBackupCodeValid maps IOException to NetworkError`() = runTest {
        coEvery { api.verifyBackupCode("pub", "1234") } throws IOException("no network")

        assertEquals(
            BackupCodeVerifyResult.NetworkError,
            repository.isBackupCodeValid("pub", "1234"),
        )
    }

    @Test
    fun `isBackupCodeValid maps generic Exception to NetworkError`() = runTest {
        coEvery { api.verifyBackupCode("pub", "1234") } throws RuntimeException("boom")

        assertEquals(
            BackupCodeVerifyResult.NetworkError,
            repository.isBackupCodeValid("pub", "1234"),
        )
    }

    @Test
    fun `isBackupCodeValid rethrows CancellationException`() = runTest {
        coEvery { api.verifyBackupCode("pub", "1234") } throws CancellationException("cancelled")

        assertFailsWith<CancellationException> { repository.isBackupCodeValid("pub", "1234") }
    }
}
