package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.VaultDao
import com.vultisig.wallet.data.db.models.CoinEntity
import com.vultisig.wallet.data.db.models.KeyShareEntity
import com.vultisig.wallet.data.db.models.VaultEntity
import com.vultisig.wallet.data.db.models.VaultWithKeySharesAndTokens
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.KeyShare
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.passcode.KeyShareCipher
import com.vultisig.wallet.data.passcode.KeyShareIdentity
import com.vultisig.wallet.data.passcode.PasscodeDataKeySource
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class VaultRepositoryImplTest {

    private lateinit var vaultDao: VaultDao
    private lateinit var tokenRepository: TokenRepository
    private lateinit var passcode: FakePasscodeDataKeySource
    private lateinit var repository: VaultRepositoryImpl

    private val keyShareCipher = KeyShareCipher()

    @BeforeEach
    fun setUp() {
        vaultDao = mockk(relaxUnitFun = true)
        tokenRepository = mockk()
        passcode = FakePasscodeDataKeySource()
        repository = VaultRepositoryImpl(vaultDao, tokenRepository, keyShareCipher, passcode)
    }

    /**
     * Stands in for the passcode repository's in-memory key without pulling in its dependencies.
     */
    private class FakePasscodeDataKeySource : PasscodeDataKeySource {
        var dataKey: ByteArray? = null
        var locked: Boolean = false

        override fun dataKeyOrNull(): ByteArray? = dataKey

        override fun isLocked(): Boolean = locked

        override suspend fun awaitUnlocked() = Unit
    }

    /** Returns a minimal [VaultWithKeySharesAndTokens] suitable for DAO stub returns. */
    private fun makeVaultWithTokens(
        id: String = "vault-1",
        name: String = "Test Vault",
        libType: SigningLibType = SigningLibType.GG20,
        coins: List<CoinEntity> = emptyList(),
    ) =
        VaultWithKeySharesAndTokens(
            vault =
                VaultEntity(
                    id = id,
                    name = name,
                    localPartyID = "device-1",
                    pubKeyEcdsa = "ecdsa-$id",
                    pubKeyEddsa = "eddsa-$id",
                    hexChainCode = "chaincode-$id",
                    resharePrefix = "",
                    libType = libType,
                ),
            keyShares = emptyList(),
            signers = emptyList(),
            coins = coins,
            chainPublicKeys = emptyList(),
        )

    /** Returns a [CoinEntity] representing native ETH on Ethereum. */
    private fun makeEthCoin(vaultId: String = "vault-1") =
        CoinEntity(
            id = "ETH-Ethereum",
            vaultId = vaultId,
            chain = "Ethereum",
            ticker = "ETH",
            decimals = 18,
            logo = "",
            priceProviderID = "ethereum",
            contractAddress = "",
            address = "0xabc",
            hexPublicKey = "pub",
        )

    /** Returns a [Coin] representing native ETH on Ethereum. */
    private fun ethCoin() =
        Coin(
            chain = Chain.Ethereum,
            ticker = "ETH",
            logo = "",
            address = "0xabc",
            decimal = 18,
            hexPublicKey = "pub",
            priceProviderID = "ethereum",
            contractAddress = "",
            isNativeToken = true,
        )

    /** Returns a [Coin] representing a THORChain secured asset with the given denom. */
    private fun securedCoin(ticker: String, contractAddress: String) =
        Coin(
            chain = Chain.ThorChain,
            ticker = ticker,
            logo = "",
            address = "thor1abc",
            decimal = 8,
            hexPublicKey = "pub",
            priceProviderID = "",
            contractAddress = contractAddress,
            isNativeToken = false,
        )

    // ---- get ----------------------------------------------------------------

    /** Verifies [get] returns null when no vault matches the given id in the DAO. */
    @Test
    fun `get returns null when vault does not exist`() = runTest {
        coEvery { vaultDao.loadById("missing") } returns null

        assertNull(repository.get("missing"))
    }

    /** Verifies [get] maps the DAO entity to a [Vault] with the correct id and name. */
    @Test
    fun `get returns vault with correct id and name`() = runTest {
        coEvery { vaultDao.loadById("vault-1") } returns makeVaultWithTokens()
        coEvery { tokenRepository.getToken(any()) } returns null

        val vault = repository.get("vault-1")
        assertNotNull(vault)
        assertEquals("vault-1", vault.id)
        assertEquals("Test Vault", vault.name)
    }

    // ---- add ----------------------------------------------------------------

    /** Verifies [add] delegates the insert call to the DAO. */
    @Test
    fun `add delegates to dao insert`() = runTest {
        repository.add(Vault(id = "vault-1", name = "V"))

        coVerify { vaultDao.insert(any()) }
    }

    // ---- upsert -------------------------------------------------------------

    /** Verifies [upsert] delegates the upsert call to the DAO. */
    @Test
    fun `upsert delegates to dao upsert`() = runTest {
        repository.upsert(Vault(id = "vault-1", name = "V"))

        coVerify { vaultDao.upsert(any()) }
    }

    /** Verifies coins are forwarded to the DAO unchanged during upsert. */
    @Test
    fun `upsert preserves coins in the captured vault`() = runTest {
        val captured = slot<VaultWithKeySharesAndTokens>()
        coJustRun { vaultDao.upsert(capture(captured)) }

        repository.upsert(Vault(id = "vault-1", name = "V", coins = listOf(ethCoin())))

        assertEquals(1, captured.captured.coins.size)
        assertEquals("ETH", captured.captured.coins[0].ticker)
        assertEquals("Ethereum", captured.captured.coins[0].chain)
    }

    // ---- delete -------------------------------------------------------------

    /** Verifies [delete] passes the vault id to the DAO delete method. */
    @Test
    fun `delete delegates to dao with correct id`() = runTest {
        repository.delete("vault-1")

        coVerify { vaultDao.delete("vault-1") }
    }

    // ---- setVaultName -------------------------------------------------------

    /** Verifies [setVaultName] passes both the vault id and new name to the DAO. */
    @Test
    fun `setVaultName delegates to dao with vault id and new name`() = runTest {
        repository.setVaultName("vault-1", "Renamed")

        coVerify { vaultDao.setVaultName("vault-1", "Renamed") }
    }

    // ---- getByEcdsa ---------------------------------------------------------

    /** Verifies [getByEcdsa] returns the matching vault when the ECDSA key is found. */
    @Test
    fun `getByEcdsa returns vault when key matches`() = runTest {
        coEvery { vaultDao.loadByEcdsa("ecdsa-vault-1") } returns makeVaultWithTokens()
        coEvery { tokenRepository.getToken(any()) } returns null

        val vault = repository.getByEcdsa("ecdsa-vault-1")
        assertNotNull(vault)
        assertEquals("vault-1", vault.id)
    }

    /** Verifies [getByEcdsa] returns null when the ECDSA key has no match. */
    @Test
    fun `getByEcdsa returns null when key has no match`() = runTest {
        coEvery { vaultDao.loadByEcdsa("unknown-key") } returns null

        assertNull(repository.getByEcdsa("unknown-key"))
    }

    // ---- getAll -------------------------------------------------------------

    /** Verifies [getAll] returns all vaults returned by the DAO. */
    @Test
    fun `getAll returns every vault from dao`() = runTest {
        coEvery { vaultDao.loadAll() } returns
            listOf(makeVaultWithTokens("v1", "Vault 1"), makeVaultWithTokens("v2", "Vault 2"))
        coEvery { tokenRepository.getToken(any()) } returns null

        val vaults = repository.getAll()
        assertEquals(2, vaults.size)
        assertEquals("v1", vaults[0].id)
        assertEquals("v2", vaults[1].id)
    }

    /** Verifies [getAll] returns an empty list when the DAO has no vaults. */
    @Test
    fun `getAll returns empty list when dao is empty`() = runTest {
        coEvery { vaultDao.loadAll() } returns emptyList()

        val vaults = repository.getAll()
        assertTrue(vaults.isEmpty())
    }

    /** Verifies [getAll] silently drops coins whose chain string is not a known [Chain] value. */
    @Test
    fun `getAll skips coins whose chain value is not in the Chain enum`() = runTest {
        val unknownChainCoin =
            CoinEntity(
                id = "GHOST-ghost_chain",
                vaultId = "vault-1",
                chain = "ghost_chain",
                ticker = "GHOST",
                decimals = 18,
                logo = "",
                priceProviderID = "",
                contractAddress = "",
                address = "0x0",
                hexPublicKey = "",
            )
        coEvery { vaultDao.loadAll() } returns
            listOf(makeVaultWithTokens(coins = listOf(makeEthCoin(), unknownChainCoin)))
        coEvery { tokenRepository.getToken(any()) } returns null

        val vaults = repository.getAll()
        assertEquals(1, vaults.size)
        assertEquals(1, vaults[0].coins.size)
        assertEquals("ETH", vaults[0].coins[0].ticker)
    }

    // ---- getEnabledTokens / getEnabledChains --------------------------------

    /** Verifies [getEnabledTokens] emits the coins that belong to the given vault. */
    @Test
    fun `getEnabledTokens emits coins belonging to vault`() = runTest {
        every { vaultDao.loadByIdAsFlow("vault-1") } returns
            flowOf(makeVaultWithTokens(coins = listOf(makeEthCoin())))
        coEvery { tokenRepository.getToken(any()) } returns null

        val tokens = repository.getEnabledTokens("vault-1").first()
        assertEquals(1, tokens.size)
        assertEquals("ETH", tokens[0].ticker)
    }

    /** Verifies [getEnabledChains] emits only chains that have a native-token coin enabled. */
    @Test
    fun `getEnabledChains emits only native-token chains`() = runTest {
        every { vaultDao.loadByIdAsFlow("vault-1") } returns
            flowOf(makeVaultWithTokens(coins = listOf(makeEthCoin())))
        coEvery { tokenRepository.getToken(any()) } returns null

        val chains = repository.getEnabledChains("vault-1").first()
        assertEquals(setOf(Chain.Ethereum), chains)
    }

    // ---- addTokenToVault ----------------------------------------------------

    /** Verifies [addTokenToVault] delegates to the DAO's enableCoins method. */
    @Test
    fun `addTokenToVault calls dao enableCoins`() = runTest {
        repository.addTokenToVault("vault-1", ethCoin())

        coVerify { vaultDao.enableCoins(any()) }
    }

    /** Verifies [addTokenToVault] builds the coin entity id as "ticker-chainRaw". */
    @Test
    fun `addTokenToVault constructs coin entity id as ticker-chainRaw`() = runTest {
        val captured = slot<List<CoinEntity>>()
        coJustRun { vaultDao.enableCoins(capture(captured)) }

        repository.addTokenToVault("vault-1", ethCoin())

        assertEquals("ETH-Ethereum", captured.captured[0].id)
    }

    /**
     * Regression: two secured assets sharing a ticker on different underlying chains (e.g. ETH.USDC
     * and AVAX.USDC) must get distinct entity ids, or Room's REPLACE-on-conflict insert would
     * silently overwrite the first one's persisted row with the second's.
     */
    @Test
    fun `addTokenToVault gives secured assets sharing a ticker distinct entity ids`() = runTest {
        val captured = mutableListOf<List<CoinEntity>>()
        coJustRun { vaultDao.enableCoins(capture(captured)) }

        repository.addTokenToVault(
            "vault-1",
            securedCoin("USDC", "eth-usdc-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"),
        )
        repository.addTokenToVault(
            "vault-1",
            securedCoin("USDC", "avax-usdc-0xb97ef9ef8734c71904d8002f8b6bc66dd9c48a6e"),
        )

        assertEquals(2, captured.flatten().map { it.id }.distinct().size)
    }

    // ---- deleteTokenFromVault -----------------------------------------------

    /** Verifies [deleteTokenFromVault] passes the correct coin entity id to the DAO. */
    @Test
    fun `deleteTokenFromVault passes correct token id to dao`() = runTest {
        repository.deleteTokenFromVault("vault-1", ethCoin())

        coVerify { vaultDao.deleteTokenFromVault("vault-1", "ETH-Ethereum") }
    }

    // ---- disableTokenFromVault ----------------------------------------------

    /** Verifies [disableTokenFromVault] passes the correct token id and chain id to the DAO. */
    @Test
    fun `disableTokenFromVault passes correct token id and chain id to dao`() = runTest {
        repository.disableTokenFromVault("vault-1", ethCoin())

        coVerify { vaultDao.disableTokenFromVault("vault-1", "ETH-Ethereum", "Ethereum") }
    }

    // ---- deleteChainFromVault -----------------------------------------------

    /** Verifies [deleteChainFromVault] passes the chain's raw string id to the DAO. */
    @Test
    fun `deleteChainFromVault calls dao disableChainFromVault with chain raw id`() = runTest {
        repository.deleteChainFromVault("vault-1", Chain.Ethereum)

        coVerify { vaultDao.disableChainFromVault("vault-1", "Ethereum") }
    }

    // ---- signing lib-type round-trips ---------------------------------------

    /** Verifies that [SigningLibType.DKLS] survives a DAO round-trip without corruption. */
    @Test
    fun `libType DKLS is preserved when loading vault`() = runTest {
        coEvery { vaultDao.loadById("v") } returns
            makeVaultWithTokens(id = "v", libType = SigningLibType.DKLS)
        coEvery { tokenRepository.getToken(any()) } returns null

        assertEquals(SigningLibType.DKLS, repository.get("v")?.libType)
    }

    /** Verifies that [SigningLibType.GG20] survives a DAO round-trip without corruption. */
    @Test
    fun `libType GG20 is preserved when loading vault`() = runTest {
        coEvery { vaultDao.loadById("v") } returns
            makeVaultWithTokens(id = "v", libType = SigningLibType.GG20)
        coEvery { tokenRepository.getToken(any()) } returns null

        assertEquals(SigningLibType.GG20, repository.get("v")?.libType)
    }

    /** Verifies that [SigningLibType.KeyImport] survives a DAO round-trip without corruption. */
    @Test
    fun `libType KeyImport is preserved when loading vault`() = runTest {
        coEvery { vaultDao.loadById("v") } returns
            makeVaultWithTokens(id = "v", libType = SigningLibType.KeyImport)
        coEvery { tokenRepository.getToken(any()) } returns null

        assertEquals(SigningLibType.KeyImport, repository.get("v")?.libType)
    }

    // ---- hasVaults / isNameTaken / getDisabledCoinIds -----------------------

    /** Verifies [hasVaults] returns true when the DAO reports at least one vault. */
    @Test
    fun `hasVaults returns true when dao reports vaults present`() = runTest {
        coEvery { vaultDao.hasVaults() } returns true

        assertTrue(repository.hasVaults())
    }

    /** Verifies [hasVaults] returns false when the DAO reports no vaults. */
    @Test
    fun `hasVaults returns false when dao reports no vaults`() = runTest {
        coEvery { vaultDao.hasVaults() } returns false

        assertTrue(!repository.hasVaults())
    }

    /** Verifies [isNameTaken] returns true when another vault already uses the given name. */
    @Test
    fun `isNameTaken returns true when another vault uses the same name`() = runTest {
        coEvery { vaultDao.countByNameExcluding("My Vault", "other-id") } returns 1

        assertTrue(repository.isNameTaken("My Vault", "other-id"))
    }

    /** Verifies [isNameTaken] returns false when no other vault uses the given name. */
    @Test
    fun `isNameTaken returns false when name is unique`() = runTest {
        coEvery { vaultDao.countByNameExcluding("Unique Name", "any-id") } returns 0

        assertTrue(!repository.isNameTaken("Unique Name", "any-id"))
    }

    /** Verifies [getDisabledCoinIds] delegates to the DAO and returns its result. */
    @Test
    fun `getDisabledCoinIds delegates to dao`() = runTest {
        coEvery { vaultDao.loadDisabledCoinIds("vault-1") } returns listOf("ETH-Ethereum")

        assertEquals(listOf("ETH-Ethereum"), repository.getDisabledCoinIds("vault-1"))
    }

    private val dataKey = ByteArray(32) { it.toByte() }
    private val keyShareIdentity = KeyShareIdentity(vaultId = "vault-1", pubKey = "pub-1")

    /** Returns a stored vault carrying [keyShare] verbatim in the keyshare column. */
    private fun vaultWithStoredKeyShare(keyShare: String) =
        makeVaultWithTokens()
            .copy(
                keyShares =
                    listOf(
                        KeyShareEntity(vaultId = "vault-1", pubKey = "pub-1", keyShare = keyShare)
                    )
            )

    /** Verifies a vault stored before the passcode existed still loads unchanged. */
    @Test
    fun `get returns plaintext keyshares for vaults stored without a passcode`() = runTest {
        coEvery { vaultDao.loadById("vault-1") } returns vaultWithStoredKeyShare("share-1")

        assertEquals(listOf("share-1"), repository.get("vault-1")?.keyshares?.map { it.keyShare })
    }

    /** Verifies encrypted keyshares are decrypted while the app is unlocked. */
    @Test
    fun `get decrypts keyshares while unlocked`() = runTest {
        passcode.dataKey = dataKey
        coEvery { vaultDao.loadById("vault-1") } returns
            vaultWithStoredKeyShare(keyShareCipher.encrypt("share-1", dataKey, keyShareIdentity))

        assertEquals(listOf("share-1"), repository.get("vault-1")?.keyshares?.map { it.keyShare })
    }

    /**
     * Verifies a share that will not open *with the key in hand* fails loudly. Dropping it, as the
     * locked path does, would hand the caller a vault that looks like it simply has fewer shares.
     */
    @Test
    fun `get fails when an unlocked keyshare cannot be decrypted`() = runTest {
        passcode.dataKey = ByteArray(32) { (it + 1).toByte() }
        coEvery { vaultDao.loadById("vault-1") } returns
            vaultWithStoredKeyShare(keyShareCipher.encrypt("share-1", dataKey, keyShareIdentity))

        assertFailsWith<IllegalStateException> { repository.get("vault-1") }
    }

    /**
     * Verifies a locked read yields the vault without its keyshares rather than throwing, so
     * background work that only needs addresses keeps running behind the lock screen.
     */
    @Test
    fun `get drops keyshares it cannot decrypt while locked`() = runTest {
        coEvery { vaultDao.loadById("vault-1") } returns
            vaultWithStoredKeyShare(keyShareCipher.encrypt("share-1", dataKey, keyShareIdentity))
        passcode.dataKey = null
        passcode.locked = true

        val vault = repository.get("vault-1")

        assertNotNull(vault)
        assertEquals(emptyList(), vault.keyshares)
        assertEquals("ecdsa-vault-1", vault.pubKeyECDSA)
    }

    /** Returns a stored vault carrying one plaintext row and one encrypted row, in that order. */
    private fun vaultWithMixedKeyShares() =
        makeVaultWithTokens()
            .copy(
                keyShares =
                    listOf(
                        KeyShareEntity(
                            vaultId = "vault-1",
                            pubKey = "pub-1",
                            keyShare = "plaintext-share-1",
                        ),
                        KeyShareEntity(
                            vaultId = "vault-1",
                            pubKey = "pub-2",
                            keyShare =
                                keyShareCipher.encrypt(
                                    "share-2",
                                    dataKey,
                                    KeyShareIdentity(vaultId = "vault-1", pubKey = "pub-2"),
                                ),
                        ),
                    )
            )

    /**
     * Verifies a table holding both plaintext and encrypted rows comes back with *no* shares while
     * locked, not just the plaintext one that happened to be readable.
     *
     * That mix is a supported state — a `protectAll` interrupted by process death leaves exactly it
     * — so the partial read was reachable in normal use. A vault carrying some of its shares is
     * useless to every caller and indistinguishable from a complete one to all of them: export
     * checks only for emptiness and would write a .vult that restores cleanly and can never sign.
     */
    @Test
    fun `get drops every keyshare when only some can be read while locked`() = runTest {
        coEvery { vaultDao.loadById("vault-1") } returns vaultWithMixedKeyShares()
        passcode.dataKey = null
        passcode.locked = true

        val vault = repository.get("vault-1")

        assertNotNull(vault)
        assertEquals(emptyList(), vault.keyshares)
        assertEquals("ecdsa-vault-1", vault.pubKeyECDSA)
    }

    /**
     * Verifies the same mix loads complete once the key is available, so the all-or-nothing rule
     * costs nothing in the state it exists to protect.
     */
    @Test
    fun `get returns both rows of a mixed table while unlocked`() = runTest {
        coEvery { vaultDao.loadById("vault-1") } returns vaultWithMixedKeyShares()
        passcode.dataKey = dataKey

        assertEquals(
            listOf("plaintext-share-1", "share-2"),
            repository.get("vault-1")?.keyshares?.map { it.keyShare },
        )
    }

    /**
     * Verifies a mixed table with the key in hand still fails loudly when a row will not open.
     * Returning nothing there would hide a damaged row behind the same silence as a locked read.
     */
    @Test
    fun `get fails when a mixed table has an undecryptable row and the key is available`() =
        runTest {
            coEvery { vaultDao.loadById("vault-1") } returns vaultWithMixedKeyShares()
            passcode.dataKey = ByteArray(32) { (it + 1).toByte() }

            assertFailsWith<IllegalStateException> { repository.get("vault-1") }
        }

    /** Verifies keyshares are encrypted on the way into the database while unlocked. */
    @Test
    fun `add encrypts keyshares while unlocked`() = runTest {
        passcode.dataKey = dataKey
        val stored = slot<VaultWithKeySharesAndTokens>()

        repository.add(makeVault().copy(keyshares = listOf(KeyShare("pub-1", "share-1"))))

        coVerify { vaultDao.insert(capture(stored)) }
        val written = stored.captured.keyShares.single().keyShare
        assertTrue(keyShareCipher.isEncrypted(written))
        assertEquals("share-1", keyShareCipher.decrypt(written, dataKey, keyShareIdentity))
    }

    /** Verifies users without a passcode keep storing keyshares in the clear. */
    @Test
    fun `add stores plaintext keyshares when no passcode is set`() = runTest {
        val stored = slot<VaultWithKeySharesAndTokens>()

        repository.add(makeVault().copy(keyshares = listOf(KeyShare("pub-1", "share-1"))))

        coVerify { vaultDao.insert(capture(stored)) }
        assertEquals("share-1", stored.captured.keyShares.single().keyShare)
    }

    /**
     * Verifies a locked write fails loudly. Storing the share in the clear instead would silently
     * undo the at-rest protection the user asked for.
     */
    @Test
    fun `add refuses to persist keyshares while locked`() = runTest {
        passcode.dataKey = null
        passcode.locked = true

        assertFailsWith<IllegalStateException> {
            repository.add(makeVault().copy(keyshares = listOf(KeyShare("pub-1", "share-1"))))
        }

        coVerify(exactly = 0) { vaultDao.insert(any()) }
    }

    /**
     * Verifies a locked write is refused even when the vault carries no keyshares.
     *
     * That shape is exactly what a locked *read* produces — the shares are dropped on the way out —
     * so it is the dangerous case, not the harmless one. Nothing reaches the per-share encryption
     * on this path, so the refusal has to sit above it; previously only Room's habit of leaving
     * keyshare rows alone on an empty upsert stood between this and erasing them.
     */
    @Test
    fun `upsert of a keyshare-free vault is refused while locked`() = runTest {
        passcode.locked = true

        assertFailsWith<IllegalStateException> { repository.upsert(makeVault()) }

        coVerify(exactly = 0) { vaultDao.upsert(any()) }
    }

    /** Returns a minimal domain [Vault]. */
    private fun makeVault() =
        Vault(
            id = "vault-1",
            name = "Test Vault",
            pubKeyECDSA = "ecdsa-vault-1",
            pubKeyEDDSA = "eddsa-vault-1",
            hexChainCode = "chaincode",
            localPartyID = "device-1",
        )
}
