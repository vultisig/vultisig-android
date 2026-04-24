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
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Unit tests for [VaultRepositoryImpl] using MockK mocks of [VaultDao] and [TokenRepository]. */
internal class VaultRepositoryImplTest {

    private lateinit var vaultDao: VaultDao
    private lateinit var tokenRepository: TokenRepository
    private lateinit var repository: VaultRepositoryImpl

    @BeforeEach
    fun setUp() {
        vaultDao = mockk(relaxed = true)
        tokenRepository = mockk(relaxed = true)
        repository = VaultRepositoryImpl(vaultDao, tokenRepository)
    }

    @Test
    fun `get returns null when vault is not found`() = runTest {
        coEvery { vaultDao.loadById(VAULT_ID) } returns null

        assertNull(repository.get(VAULT_ID))
    }

    @Test
    fun `get returns vault with correct id and name`() = runTest {
        coEvery { vaultDao.loadById(VAULT_ID) } returns fakeVaultData()

        val vault = repository.get(VAULT_ID)

        assertNotNull(vault)
        assertEquals(VAULT_ID, vault.id)
        assertEquals(VAULT_NAME, vault.name)
    }

    @Test
    fun `add calls dao insert with correct vault id and name`() = runTest {
        val inserted = slot<VaultWithKeySharesAndTokens>()
        coEvery { vaultDao.insert(capture(inserted)) } returns Unit

        repository.add(fakeVault())

        assertEquals(VAULT_ID, inserted.captured.vault.id)
        assertEquals(VAULT_NAME, inserted.captured.vault.name)
    }

    @Test
    fun `upsert calls dao upsert with correct vault id`() = runTest {
        val upserted = slot<VaultWithKeySharesAndTokens>()
        coEvery { vaultDao.upsert(capture(upserted)) } returns Unit

        repository.upsert(fakeVault())

        assertEquals(VAULT_ID, upserted.captured.vault.id)
    }

    @Test
    fun `delete calls dao delete with the vault id`() = runTest {
        repository.delete(VAULT_ID)

        coVerify { vaultDao.delete(VAULT_ID) }
    }

    @Test
    fun `setVaultName calls dao with correct vault id and new name`() = runTest {
        repository.setVaultName(VAULT_ID, "New Name")

        coVerify { vaultDao.setVaultName(VAULT_ID, "New Name") }
    }

    @Test
    fun `getByEcdsa returns vault when found by ecdsa key`() = runTest {
        coEvery { vaultDao.loadByEcdsa(ECDSA_KEY) } returns fakeVaultData(pubKeyEcdsa = ECDSA_KEY)

        val vault = repository.getByEcdsa(ECDSA_KEY)

        assertNotNull(vault)
        assertEquals(ECDSA_KEY, vault.pubKeyECDSA)
    }

    @Test
    fun `getByEcdsa returns null when no vault matches the ecdsa key`() = runTest {
        coEvery { vaultDao.loadByEcdsa("unknown") } returns null

        assertNull(repository.getByEcdsa("unknown"))
    }

    @Test
    fun `getAll returns list of all stored vaults`() = runTest {
        coEvery { vaultDao.loadAll() } returns
            listOf(fakeVaultData(), fakeVaultData(id = "vault-2", name = "Second"))

        val vaults = repository.getAll()

        assertEquals(2, vaults.size)
    }

    @Test
    fun `getAll returns empty list when no vaults are stored`() = runTest {
        coEvery { vaultDao.loadAll() } returns emptyList()

        assertTrue(repository.getAll().isEmpty())
    }

    @Test
    fun `getAll skips coins with unrecognised chain and still returns the vault`() = runTest {
        val badCoin = fakeCoinEntity(id = "X-UNKNOWN", chain = "UNKNOWN_CHAIN_XYZ_999")
        val goodCoin = fakeCoinEntity(id = "BTC-Bitcoin", chain = Chain.Bitcoin.raw, ticker = "BTC")
        coEvery { vaultDao.loadAll() } returns
            listOf(fakeVaultData(coins = listOf(badCoin, goodCoin)))

        val vaults = repository.getAll()

        assertEquals(1, vaults.size)
        assertEquals(1, vaults[0].coins.size)
        assertEquals(Chain.Bitcoin, vaults[0].coins[0].chain)
    }

    @Test
    fun `getEnabledTokens emits coins stored in the vault`() = runTest {
        val coin = fakeCoinEntity(id = "ETH-Ethereum", chain = Chain.Ethereum.raw, ticker = "ETH")
        coEvery { vaultDao.loadById(VAULT_ID) } returns fakeVaultData(coins = listOf(coin))

        val coins = repository.getEnabledTokens(VAULT_ID).first()

        assertEquals(1, coins.size)
        assertEquals(Chain.Ethereum, coins[0].chain)
        assertEquals("ETH", coins[0].ticker)
    }

    @Test
    fun `getEnabledChains returns only chains that have a native token`() = runTest {
        val native =
            fakeCoinEntity(
                id = "ETH-Ethereum",
                chain = Chain.Ethereum.raw,
                ticker = "ETH",
                contractAddress = "",
            )
        val erc20 =
            fakeCoinEntity(
                id = "USDC-Ethereum",
                chain = Chain.Ethereum.raw,
                ticker = "USDC",
                contractAddress = "0xusdc",
            )
        coEvery { vaultDao.loadById(VAULT_ID) } returns fakeVaultData(coins = listOf(native, erc20))

        val chains = repository.getEnabledChains(VAULT_ID).first()

        assertEquals(setOf(Chain.Ethereum), chains)
    }

    @Test
    fun `addTokenToVault calls enableCoins with id in ticker-chainRaw format`() = runTest {
        val capturedCoins = slot<List<CoinEntity>>()
        coEvery { vaultDao.enableCoins(capture(capturedCoins)) } returns Unit

        repository.addTokenToVault(VAULT_ID, fakeCoin(Chain.Bitcoin, "BTC"))

        val entity = capturedCoins.captured.single()
        assertEquals("BTC-Bitcoin", entity.id)
        assertEquals(VAULT_ID, entity.vaultId)
        assertEquals(Chain.Bitcoin.raw, entity.chain)
    }

    @Test
    fun `addTokenToVault token id is stable so Room REPLACE prevents duplication`() = runTest {
        val capturedCoins = mutableListOf<List<CoinEntity>>()
        coEvery { vaultDao.enableCoins(capture(capturedCoins)) } returns Unit
        val token = fakeCoin(Chain.Ethereum, "ETH")

        repository.addTokenToVault(VAULT_ID, token)
        repository.addTokenToVault(VAULT_ID, token)

        assertEquals(2, capturedCoins.size)
        assertEquals(capturedCoins[0].single().id, capturedCoins[1].single().id)
    }

    @Test
    fun `deleteTokenFromVault calls dao with the correct token id`() = runTest {
        repository.deleteTokenFromVault(VAULT_ID, fakeCoin(Chain.Bitcoin, "BTC"))

        coVerify { vaultDao.deleteTokenFromVault(VAULT_ID, "BTC-Bitcoin") }
    }

    @Test
    fun `disableTokenFromVault calls dao with token id and chain raw id`() = runTest {
        repository.disableTokenFromVault(VAULT_ID, fakeCoin(Chain.Ethereum, "ETH"))

        coVerify { vaultDao.disableTokenFromVault(VAULT_ID, "ETH-Ethereum", Chain.Ethereum.raw) }
    }

    @Test
    fun `deleteChainFromVault calls dao disableChainFromVault with chain raw id`() = runTest {
        repository.deleteChainFromVault(VAULT_ID, Chain.Bitcoin)

        coVerify { vaultDao.disableChainFromVault(VAULT_ID, Chain.Bitcoin.raw) }
    }

    @Test
    fun `get returns vault with DKLS lib type preserved`() = runTest {
        coEvery { vaultDao.loadById(VAULT_ID) } returns fakeVaultData(libType = SigningLibType.DKLS)

        assertEquals(SigningLibType.DKLS, repository.get(VAULT_ID)?.libType)
    }

    @Test
    fun `get returns vault with GG20 lib type preserved`() = runTest {
        coEvery { vaultDao.loadById(VAULT_ID) } returns fakeVaultData(libType = SigningLibType.GG20)

        assertEquals(SigningLibType.GG20, repository.get(VAULT_ID)?.libType)
    }

    @Test
    fun `get returns vault with KeyImport lib type preserved`() = runTest {
        coEvery { vaultDao.loadById(VAULT_ID) } returns
            fakeVaultData(libType = SigningLibType.KeyImport)

        assertEquals(SigningLibType.KeyImport, repository.get(VAULT_ID)?.libType)
    }

    @Test
    fun `add stores the vault entity with DKLS lib type`() = runTest {
        val inserted = slot<VaultWithKeySharesAndTokens>()
        coEvery { vaultDao.insert(capture(inserted)) } returns Unit

        repository.add(fakeVault(libType = SigningLibType.DKLS))

        assertEquals(SigningLibType.DKLS, inserted.captured.vault.libType)
    }

    @Test
    fun `hasVaults returns true when the dao reports vaults exist`() = runTest {
        coEvery { vaultDao.hasVaults() } returns true

        assertTrue(repository.hasVaults())
    }

    @Test
    fun `hasVaults returns false when the dao reports no vaults`() = runTest {
        coEvery { vaultDao.hasVaults() } returns false

        assertFalse(repository.hasVaults())
    }

    @Test
    fun `isNameTaken returns true when another vault has the same name`() = runTest {
        coEvery { vaultDao.countByNameExcluding("Taken", VAULT_ID) } returns 1

        assertTrue(repository.isNameTaken("Taken", VAULT_ID))
    }

    @Test
    fun `isNameTaken returns false when no other vault uses the name`() = runTest {
        coEvery { vaultDao.countByNameExcluding("Free", VAULT_ID) } returns 0

        assertFalse(repository.isNameTaken("Free", VAULT_ID))
    }

    @Test
    fun `getDisabledCoinIds delegates to dao and returns the result`() = runTest {
        val expected = listOf("ETH-Ethereum", "USDC-Ethereum")
        coEvery { vaultDao.loadDisabledCoinIds(VAULT_ID) } returns expected

        assertEquals(expected, repository.getDisabledCoinIds(VAULT_ID))
    }

    private fun fakeVaultData(
        id: String = VAULT_ID,
        name: String = VAULT_NAME,
        pubKeyEcdsa: String = ECDSA_KEY,
        libType: SigningLibType = SigningLibType.GG20,
        coins: List<CoinEntity> = emptyList(),
    ): VaultWithKeySharesAndTokens =
        VaultWithKeySharesAndTokens(
            vault =
                VaultEntity(
                    id = id,
                    name = name,
                    pubKeyEcdsa = pubKeyEcdsa,
                    pubKeyEddsa = "eddsa-key",
                    pubKeyMldsa = "",
                    hexChainCode = "",
                    localPartyID = "",
                    resharePrefix = "",
                    libType = libType,
                ),
            keyShares = emptyList(),
            signers = emptyList(),
            coins = coins,
            chainPublicKeys = emptyList(),
        )

    private fun fakeCoinEntity(
        id: String = "ETH-Ethereum",
        chain: String = Chain.Ethereum.raw,
        ticker: String = "ETH",
        contractAddress: String = "",
        vaultId: String = VAULT_ID,
    ): CoinEntity =
        CoinEntity(
            id = id,
            vaultId = vaultId,
            chain = chain,
            ticker = ticker,
            decimals = 18,
            logo = "",
            priceProviderID = "",
            contractAddress = contractAddress,
            address = "0x",
            hexPublicKey = "",
        )

    private fun fakeVault(libType: SigningLibType = SigningLibType.GG20): Vault =
        Vault(id = VAULT_ID, name = VAULT_NAME, libType = libType)

    private fun fakeCoin(chain: Chain, ticker: String, contractAddress: String = ""): Coin =
        Coin(
            chain = chain,
            ticker = ticker,
            logo = "",
            address = "0x",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = contractAddress,
            isNativeToken = contractAddress.isBlank(),
        )

    private companion object {
        const val VAULT_ID = "vault-id-1"
        const val VAULT_NAME = "Test Vault"
        const val ECDSA_KEY = "ecdsa-pub-key"
    }
}
