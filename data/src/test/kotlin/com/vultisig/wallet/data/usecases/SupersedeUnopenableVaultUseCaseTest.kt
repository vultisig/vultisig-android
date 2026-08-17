package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.db.dao.VaultDao
import com.vultisig.wallet.data.db.models.KeyShareEntity
import com.vultisig.wallet.data.db.models.VaultEntity
import com.vultisig.wallet.data.models.KeyShare
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.passcode.KeyShareCipher
import com.vultisig.wallet.data.passcode.KeyShareIdentity
import com.vultisig.wallet.data.passcode.PasscodeRepository
import com.vultisig.wallet.data.passcode.PasscodeState
import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.order.VaultOrderRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class SupersedeUnopenableVaultUseCaseTest {

    private val cipher = KeyShareCipher()
    private val dataKey = ByteArray(32) { it.toByte() }
    private val passcodeState = MutableStateFlow<PasscodeState>(PasscodeState.KeyUnavailable)

    private lateinit var vaultDao: VaultDao
    private lateinit var vaultRepository: VaultRepository
    private lateinit var vaultOrderRepository: VaultOrderRepository
    private lateinit var lastOpenedVaultRepository: LastOpenedVaultRepository
    private lateinit var passcodeRepository: PasscodeRepository
    private lateinit var useCase: SupersedeUnopenableVaultUseCaseImpl

    @BeforeEach
    fun setUp() {
        vaultDao = mockk(relaxUnitFun = true)
        vaultRepository = mockk(relaxUnitFun = true)
        vaultOrderRepository = mockk(relaxUnitFun = true)
        lastOpenedVaultRepository = mockk(relaxUnitFun = true)
        passcodeRepository = mockk(relaxUnitFun = true)
        every { passcodeRepository.state } returns passcodeState
        useCase =
            SupersedeUnopenableVaultUseCaseImpl(
                vaultDao = vaultDao,
                vaultRepository = vaultRepository,
                vaultOrderRepository = vaultOrderRepository,
                lastOpenedVaultRepository = lastOpenedVaultRepository,
                keyShareCipher = cipher,
                passcodeRepository = passcodeRepository,
                dispatcher = UnconfinedTestDispatcher(),
            )
    }

    private fun storedVault(
        id: String = "stored-id",
        ecdsa: String = ECDSA,
        eddsa: String = EDDSA,
        mldsa: String = "",
    ) =
        VaultEntity(
            id = id,
            name = "Main",
            localPartyID = "device-1",
            pubKeyEcdsa = ecdsa,
            pubKeyEddsa = eddsa,
            pubKeyMldsa = mldsa,
            hexChainCode = "chaincode",
            resharePrefix = "",
            libType = SigningLibType.DKLS,
        )

    /** A stored keyshare as the passcode sweep leaves it — sealed under a key that is now gone. */
    private fun sealedShare(pubKey: String, vaultId: String = "stored-id") =
        KeyShareEntity(
            vaultId = vaultId,
            pubKey = pubKey,
            keyShare = cipher.encrypt("share-$pubKey", dataKey, KeyShareIdentity(vaultId, pubKey)),
        )

    private fun plaintextShare(pubKey: String, vaultId: String = "stored-id") =
        KeyShareEntity(vaultId = vaultId, pubKey = pubKey, keyShare = "share-$pubKey")

    private fun backup(
        ecdsa: String = ECDSA,
        eddsa: String = EDDSA,
        mldsa: String = "",
        sharePubKeys: List<String> = listOf(ECDSA, EDDSA),
        shareValue: String = "restored-share",
    ) =
        Vault(
            id = "fresh-uuid",
            name = "Main",
            pubKeyECDSA = ecdsa,
            pubKeyEDDSA = eddsa,
            pubKeyMLDSA = mldsa,
            hexChainCode = "chaincode",
            localPartyID = "device-1",
            keyshares = sharePubKeys.map { KeyShare(pubKey = it, keyShare = shareValue) },
        )

    private fun onDevice(vaults: List<VaultEntity>, shares: List<KeyShareEntity>) {
        coEvery { vaultDao.loadAllVaults() } returns vaults
        vaults.forEach { vault ->
            coEvery { vaultDao.loadKeyShares(vault.id) } returns
                shares.filter { it.vaultId == vault.id }
        }
    }

    // ---- the recovery this exists for ---------------------------------------

    @Test
    fun `a backup replaces the sealed vault it restores`() = runTest {
        onDevice(listOf(storedVault()), listOf(sealedShare(ECDSA), sealedShare(EDDSA)))
        val backup = backup()

        useCase(backup) shouldBe true

        coVerify { vaultRepository.replace("stored-id", backup) }
        coVerify { vaultOrderRepository.delete(parentId = null, name = "stored-id") }
        coVerify { lastOpenedVaultRepository.setLastOpenedVaultId("fresh-uuid") }
    }

    @Test
    fun `a backup carrying more keys than the stored vault still replaces it`() = runTest {
        onDevice(listOf(storedVault()), listOf(sealedShare(ECDSA), sealedShare(EDDSA)))

        useCase(backup(mldsa = MLDSA, sharePubKeys = listOf(ECDSA, EDDSA, MLDSA))) shouldBe true
    }

    @Test
    fun `a backup colliding on EdDSA alone replaces the vault it restores`() = runTest {
        onDevice(listOf(storedVault(ecdsa = "")), listOf(sealedShare(EDDSA)))

        useCase(backup(ecdsa = "", sharePubKeys = listOf(EDDSA))) shouldBe true
    }

    // ---- what it must refuse ------------------------------------------------

    /** The load-bearing case: a vault this device can still read is not an orphan. */
    @Test
    fun `a backup does not replace a vault whose shares are in the clear`() = runTest {
        onDevice(listOf(storedVault()), listOf(plaintextShare(ECDSA), plaintextShare(EDDSA)))

        useCase(backup()) shouldBe false

        coVerify(exactly = 0) { vaultRepository.replace(any(), any()) }
    }

    /**
     * A row holding one share in the clear is one this device wrote itself. Refused, not guessed.
     */
    @Test
    fun `a backup does not replace a vault only some of whose shares are sealed`() = runTest {
        onDevice(listOf(storedVault()), listOf(sealedShare(ECDSA), plaintextShare(EDDSA)))

        useCase(backup()) shouldBe false
    }

    @Test
    fun `a backup does not replace a vault that holds no shares at all`() = runTest {
        onDevice(listOf(storedVault()), shares = emptyList())

        useCase(backup()) shouldBe false
    }

    /**
     * A key the stored row holds and the backup leaves out is key material the replacement would
     * take away with the row.
     */
    @Test
    fun `a backup that drops a key the stored vault holds is refused`() = runTest {
        onDevice(
            listOf(storedVault(mldsa = MLDSA)),
            listOf(sealedShare(ECDSA), sealedShare(EDDSA), sealedShare(MLDSA)),
        )

        // Carries a share for every key either side names, so only the dropped identity refuses it.
        useCase(backup(mldsa = "", sharePubKeys = listOf(ECDSA, EDDSA, MLDSA))) shouldBe false
    }

    @Test
    fun `a backup that spells a stored key differently is refused`() = runTest {
        onDevice(listOf(storedVault()), listOf(sealedShare(ECDSA), sealedShare(EDDSA)))

        // Carries a share for the stored key as well as its own, so only the spelling refuses it.
        useCase(
            backup(eddsa = "other-eddsa", sharePubKeys = listOf(ECDSA, "other-eddsa", EDDSA))
        ) shouldBe false
    }

    /** The stored row names no EdDSA key, so only the backup's own declaration can refuse this. */
    @Test
    fun `a backup declaring a key it carries no share for is refused`() = runTest {
        onDevice(listOf(storedVault(eddsa = "")), listOf(sealedShare(ECDSA)))

        useCase(backup(sharePubKeys = listOf(ECDSA))) shouldBe false
    }

    /** MLDSA is the only key either side names, so only that arm can match them up. */
    @Test
    fun `a backup colliding on MLDSA alone replaces the vault it restores`() = runTest {
        onDevice(
            listOf(storedVault(ecdsa = "", eddsa = "", mldsa = MLDSA)),
            listOf(sealedShare(MLDSA)),
        )

        useCase(
            backup(ecdsa = "", eddsa = "", mldsa = MLDSA, sharePubKeys = listOf(MLDSA))
        ) shouldBe true
    }

    /** An empty share list normalises perfectly well and would buy the deletion with nothing. */
    @Test
    fun `a backup carrying no shares is refused`() = runTest {
        onDevice(listOf(storedVault()), listOf(sealedShare(ECDSA), sealedShare(EDDSA)))

        useCase(backup(sharePubKeys = emptyList())) shouldBe false
    }

    @Test
    fun `a backup carrying a blank share is refused`() = runTest {
        onDevice(listOf(storedVault()), listOf(sealedShare(ECDSA), sealedShare(EDDSA)))

        useCase(backup(shareValue = "")) shouldBe false
    }

    @Test
    fun `a backup missing a share for a key it declares is refused`() = runTest {
        onDevice(listOf(storedVault()), listOf(sealedShare(ECDSA), sealedShare(EDDSA)))

        useCase(backup(sharePubKeys = listOf(ECDSA))) shouldBe false
    }

    @Test
    fun `a backup missing a share the stored vault holds is refused`() = runTest {
        onDevice(
            listOf(storedVault()),
            listOf(sealedShare(ECDSA), sealedShare(EDDSA), sealedShare(DERIVED)),
        )

        useCase(backup()) shouldBe false
    }

    /** Picking one of two would destroy the other's key material. */
    @Test
    fun `a backup colliding with two stored vaults is refused`() = runTest {
        onDevice(
            listOf(storedVault(), storedVault(id = "other-id", eddsa = "other-eddsa")),
            listOf(sealedShare(ECDSA), sealedShare(EDDSA), sealedShare(ECDSA, "other-id")),
        )

        useCase(backup()) shouldBe false
    }

    @Test
    fun `a backup colliding with nothing on the device is refused`() = runTest {
        onDevice(listOf(storedVault(ecdsa = "other", eddsa = "other")), listOf(sealedShare(ECDSA)))

        useCase(backup()) shouldBe false
    }

    /** Blank keys carry no identity, so two vaults lacking one are not the same vault. */
    @Test
    fun `a blank key does not make two vaults collide`() = runTest {
        onDevice(listOf(storedVault(ecdsa = "", eddsa = "")), listOf(sealedShare(ECDSA)))

        useCase(backup(ecdsa = "", eddsa = "")) shouldBe false
    }

    // ---- the states that must never supersede -------------------------------

    /**
     * The wrap is most likely still on disk and the shares open again once the keystore comes back,
     * so replacing here would destroy a vault that was only ever unreadable for a launch.
     */
    @Test
    fun `nothing is replaced while the credential store is merely unavailable`() = runTest {
        passcodeState.value = PasscodeState.StoreUnavailable
        onDevice(listOf(storedVault()), listOf(sealedShare(ECDSA), sealedShare(EDDSA)))

        useCase(backup()) shouldBe false

        coVerify(exactly = 0) { vaultRepository.replace(any(), any()) }
    }

    /**
     * The state authorising the delete is re-read first, so a keystore that has come back since
     * launch refuses here rather than after the row is gone.
     */
    @Test
    fun `nothing is replaced when the keystore comes back on the re-read`() = runTest {
        coEvery { passcodeRepository.retry() } answers
            {
                passcodeState.value = PasscodeState.Locked
            }
        onDevice(listOf(storedVault()), listOf(sealedShare(ECDSA), sealedShare(EDDSA)))

        useCase(backup()) shouldBe false

        coVerify(exactly = 0) { vaultRepository.replace(any(), any()) }
    }

    @Test
    fun `nothing is replaced while the app is merely locked`() = runTest {
        passcodeState.value = PasscodeState.Locked
        onDevice(listOf(storedVault()), listOf(sealedShare(ECDSA), sealedShare(EDDSA)))

        useCase(backup()) shouldBe false
    }

    @Test
    fun `nothing is replaced when no passcode is configured`() = runTest {
        passcodeState.value = PasscodeState.Disabled
        onDevice(listOf(storedVault()), listOf(sealedShare(ECDSA), sealedShare(EDDSA)))

        useCase(backup()) shouldBe false
    }

    @Test
    fun `nothing is replaced before the passcode state has been read`() = runTest {
        passcodeState.value = PasscodeState.Unknown
        onDevice(listOf(storedVault()), listOf(sealedShare(ECDSA), sealedShare(EDDSA)))

        useCase(backup()) shouldBe false
    }

    private companion object {
        const val ECDSA = "ecdsa-pub-key"
        const val EDDSA = "eddsa-pub-key"
        const val MLDSA = "mldsa-pub-key"
        const val DERIVED = "derived-chain-pub-key"
    }
}
