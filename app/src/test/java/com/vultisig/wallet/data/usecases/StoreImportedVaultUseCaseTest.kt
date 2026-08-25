package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class StoreImportedVaultUseCaseTest {

    private lateinit var saveVault: SaveVaultUseCase
    private lateinit var vaultRepository: VaultRepository
    private lateinit var vaultDataStoreRepository: VaultDataStoreRepository
    private lateinit var chainAccountAddressRepository: ChainAccountAddressRepository
    private lateinit var useCase: StoreImportedVaultUseCaseImpl

    @BeforeEach
    fun setUp() {
        saveVault = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        vaultDataStoreRepository = mockk(relaxed = true)
        chainAccountAddressRepository = mockk(relaxed = true)
        useCase =
            StoreImportedVaultUseCaseImpl(
                saveVault = saveVault,
                vaultRepository = vaultRepository,
                vaultDataStoreRepository = vaultDataStoreRepository,
                chainAccountAddressRepository = chainAccountAddressRepository,
            )
    }

    private fun testVault(libType: SigningLibType = SigningLibType.DKLS, pubKeyMLDSA: String = "") =
        Vault(
            id = "test-vault-id",
            name = "Test Vault",
            libType = libType,
            pubKeyMLDSA = pubKeyMLDSA,
        )

    private suspend fun storedLibType(vault: Vault, fileName: String?): SigningLibType {
        useCase(vault, fileName)
        val stored = slot<Vault>()
        coVerify { saveVault(capture(stored), false) }
        return stored.captured.libType
    }

    @Test
    fun `KeyImport vault with share filename keeps KeyImport libType`() = runTest {
        storedLibType(testVault(SigningLibType.KeyImport), "share1of2-test.bak") shouldBe
            SigningLibType.KeyImport
    }

    @Test
    fun `GG20 vault with share filename gets overridden to DKLS`() = runTest {
        storedLibType(testVault(SigningLibType.GG20), "share1of2-test.bak") shouldBe
            SigningLibType.DKLS
    }

    @Test
    fun `DKLS vault without share filename keeps DKLS`() = runTest {
        storedLibType(testVault(SigningLibType.DKLS), "test.bak") shouldBe SigningLibType.DKLS
    }

    @Test
    fun `GG20 vault keeps GG20 when the backup has no file name`() = runTest {
        storedLibType(testVault(SigningLibType.GG20), fileName = null) shouldBe SigningLibType.GG20
    }

    @Test
    fun `storing an MLDSA-capable vault re-adds the QBTC token`() = runTest {
        coEvery { chainAccountAddressRepository.getAddress(any<Coin>(), any<Vault>()) } returns
            Pair("qbtc1address", "qbtc-derived-pubkey")

        useCase(testVault(pubKeyMLDSA = "mldsa-pubkey"), "share1of2-test.bak")

        val token = slot<Coin>()
        coVerify { vaultRepository.addTokenToVault("test-vault-id", capture(token)) }
        token.captured.ticker shouldBe Coins.Qbtc.QBTC.ticker
        token.captured.address shouldBe "qbtc1address"
        token.captured.hexPublicKey shouldBe "qbtc-derived-pubkey"
    }

    @Test
    fun `storing a vault without MLDSA leaves QBTC alone`() = runTest {
        useCase(testVault(), "share1of2-test.bak")

        coVerify(exactly = 0) { vaultRepository.addTokenToVault(any(), any()) }
    }

    @Test
    fun `the stored vault is marked as backed up`() = runTest {
        useCase(testVault(), "vault.bak")

        coVerify { vaultDataStoreRepository.setBackupStatus("test-vault-id", true) }
    }

    @Test
    fun `import succeeds when setBackupStatus fails`() = runTest {
        coEvery { vaultDataStoreRepository.setBackupStatus(any(), any()) } throws
            RuntimeException("datastore")

        useCase(testVault(), "vault.bak").id shouldBe "test-vault-id"
    }

    @Test
    fun `import succeeds when QBTC address derivation fails on MLDSA vault`() = runTest {
        coEvery { chainAccountAddressRepository.getAddress(any<Coin>(), any<Vault>()) } throws
            RuntimeException("derivation failed")

        useCase(testVault(pubKeyMLDSA = "mldsa-pub-key"), "vault.bak").id shouldBe "test-vault-id"
    }

    /** The save is the point of no return, so its failure has to reach the caller. */
    @Test
    fun `a refused duplicate propagates`() = runTest {
        coEvery { saveVault(any(), false) } throws DuplicateVaultException()

        assertFailsWith<DuplicateVaultException> { useCase(testVault(), "vault.bak") }
    }
}
