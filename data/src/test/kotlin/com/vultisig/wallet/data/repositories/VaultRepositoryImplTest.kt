package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.VaultDao
import com.vultisig.wallet.data.db.models.CoinEntity
import com.vultisig.wallet.data.db.models.VaultEntity
import com.vultisig.wallet.data.db.models.VaultWithKeySharesAndTokens
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class VaultRepositoryImplTest {

    private lateinit var vaultDao: VaultDao
    private lateinit var tokenRepository: TokenRepository
    private lateinit var repository: VaultRepositoryImpl

    @BeforeEach
    fun setUp() {
        vaultDao = mockk(relaxUnitFun = true)
        tokenRepository = mockk()
        repository = VaultRepositoryImpl(vaultDao, tokenRepository)
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
        coEvery { vaultDao.loadById("vault-1") } returns
            makeVaultWithTokens(coins = listOf(makeEthCoin()))
        coEvery { tokenRepository.getToken(any()) } returns null

        val tokens = repository.getEnabledTokens("vault-1").first()
        assertEquals(1, tokens.size)
        assertEquals("ETH", tokens[0].ticker)
    }

    /** Verifies [getEnabledChains] emits only chains that have a native-token coin enabled. */
    @Test
    fun `getEnabledChains emits only native-token chains`() = runTest {
        coEvery { vaultDao.loadById("vault-1") } returns
            makeVaultWithTokens(coins = listOf(makeEthCoin()))
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
}
