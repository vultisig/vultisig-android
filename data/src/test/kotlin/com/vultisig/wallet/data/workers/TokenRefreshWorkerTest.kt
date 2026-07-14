package com.vultisig.wallet.data.workers

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class TokenRefreshWorkerTest {

    private lateinit var tokenRepository: TokenRepository
    private lateinit var vaultRepository: VaultRepository
    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        tokenRepository = mockk()
        vaultRepository = mockk()
        context = mockk(relaxed = true)
    }

    private fun buildWorker(
        inputData: Data = Data.EMPTY,
        runAttemptCount: Int = 0,
    ): TokenRefreshWorker =
        TestListenableWorkerBuilder<TokenRefreshWorker>(context)
            .setInputData(inputData)
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker =
                        TokenRefreshWorker(
                            appContext,
                            workerParameters,
                            tokenRepository,
                            vaultRepository,
                        )
                }
            )
            .build()

    private fun vault(id: String, coins: List<Coin>) = Vault(id = id, name = id, coins = coins)

    private fun coin(chain: Chain) =
        Coin.EMPTY.copy(chain = chain, ticker = chain.id, contractAddress = "")

    @Test
    fun `doWork retries when a chain refresh call throws`() = runTest {
        val vault = vault(id = "vault-1", coins = listOf(coin(Chain.Ethereum)))
        coEvery { vaultRepository.getAll() } returns listOf(vault)
        coEvery { vaultRepository.getDisabledCoinIds(vault.id) } returns emptyList()
        every { vaultRepository.getEnabledTokens(vault.id) } returns flowOf(emptyList())
        coEvery { tokenRepository.getRefreshTokens(any(), any()) } throws IOException("offline")

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `doWork succeeds when every chain refreshes without error`() = runTest {
        val vault = vault(id = "vault-2", coins = listOf(coin(Chain.Ethereum)))
        coEvery { vaultRepository.getAll() } returns listOf(vault)
        coEvery { vaultRepository.getDisabledCoinIds(vault.id) } returns emptyList()
        every { vaultRepository.getEnabledTokens(vault.id) } returns flowOf(emptyList())
        coEvery { tokenRepository.getRefreshTokens(any(), any()) } returns emptyList()

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork retries when reading enabled tokens fails`() = runTest {
        val vault = vault(id = "vault-4", coins = listOf(coin(Chain.Ethereum)))
        coEvery { vaultRepository.getAll() } returns listOf(vault)
        coEvery { vaultRepository.getDisabledCoinIds(vault.id) } returns emptyList()
        every { vaultRepository.getEnabledTokens(vault.id) } returns
            flow { throw IOException("offline") }

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `doWork fails without retry when the requested vault does not exist`() = runTest {
        val inputData =
            Data.Builder().putString(TokenRefreshWorker.ARG_VAULT_ID, "missing-vault").build()
        coEvery { vaultRepository.get("missing-vault") } returns null

        val result = buildWorker(inputData).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `doWork corrects a stale ticker whose casing drifted from the curated identity`() =
        runTest {
            // A pre-canonicalization entry with the old uppercase ticker; the refreshed identity
            // recases it. The ids match case-insensitively, so without a correction path the stale
            // entry would be kept forever.
            val stale =
                Coin.EMPTY.copy(
                    chain = Chain.ThorChain,
                    ticker = "BRUNE",
                    contractAddress = "x/brune",
                )
            val corrected = stale.copy(ticker = "bRUNE")
            val vault = vault(id = "vault-5", coins = listOf(coin(Chain.ThorChain)))

            coEvery { vaultRepository.getAll() } returns listOf(vault)
            coEvery { vaultRepository.getDisabledCoinIds(vault.id) } returns emptyList()
            every { vaultRepository.getEnabledTokens(vault.id) } returns flowOf(listOf(stale))
            coEvery { tokenRepository.getRefreshTokens(Chain.ThorChain, vault) } returns
                listOf(corrected)
            coEvery { vaultRepository.deleteTokenFromVault(any(), any()) } returns Unit
            coEvery { vaultRepository.addTokenToVault(any(), any()) } returns Unit

            buildWorker().doWork()

            coVerify(exactly = 1) { vaultRepository.deleteTokenFromVault(vault.id, stale) }
            coVerify(exactly = 1) { vaultRepository.addTokenToVault(vault.id, corrected) }
        }

    @Test
    fun `doWork leaves an already-correct token untouched`() = runTest {
        val existing =
            Coin.EMPTY.copy(chain = Chain.ThorChain, ticker = "bRUNE", contractAddress = "x/brune")
        val vault = vault(id = "vault-6", coins = listOf(coin(Chain.ThorChain)))

        coEvery { vaultRepository.getAll() } returns listOf(vault)
        coEvery { vaultRepository.getDisabledCoinIds(vault.id) } returns emptyList()
        every { vaultRepository.getEnabledTokens(vault.id) } returns flowOf(listOf(existing))
        coEvery { tokenRepository.getRefreshTokens(Chain.ThorChain, vault) } returns
            listOf(existing)

        buildWorker().doWork()

        coVerify(exactly = 0) { vaultRepository.deleteTokenFromVault(any(), any()) }
        coVerify(exactly = 0) { vaultRepository.addTokenToVault(any(), any()) }
    }

    @Test
    fun `doWork fails once a chain refresh call keeps throwing past the max attempts`() = runTest {
        val vault = vault(id = "vault-3", coins = listOf(coin(Chain.Ethereum)))
        coEvery { vaultRepository.getAll() } returns listOf(vault)
        coEvery { vaultRepository.getDisabledCoinIds(vault.id) } returns emptyList()
        every { vaultRepository.getEnabledTokens(vault.id) } returns flowOf(emptyList())
        coEvery { tokenRepository.getRefreshTokens(any(), any()) } throws IOException("offline")

        val result = buildWorker(runAttemptCount = TokenRefreshWorker.MAX_REFRESH_ATTEMPTS).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }
}
