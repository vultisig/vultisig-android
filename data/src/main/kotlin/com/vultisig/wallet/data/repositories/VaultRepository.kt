package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.VaultDao
import com.vultisig.wallet.data.db.models.ChainPublicKeyEntity
import com.vultisig.wallet.data.db.models.CoinEntity
import com.vultisig.wallet.data.db.models.KeyShareEntity
import com.vultisig.wallet.data.db.models.SignerEntity
import com.vultisig.wallet.data.db.models.VaultEntity
import com.vultisig.wallet.data.db.models.VaultWithKeySharesAndTokens
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ChainPublicKey
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.KeyShare
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.passcode.KeyShareCipher
import com.vultisig.wallet.data.passcode.KeyShareIdentity
import com.vultisig.wallet.data.passcode.PasscodeDataKeySource
import com.vultisig.wallet.data.passcode.identity
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

interface VaultRepository {

    fun getEnabledTokens(vaultId: VaultId): Flow<List<Coin>>

    fun getEnabledChains(vaultId: VaultId): Flow<Set<Chain>>

    suspend fun getDisabledCoinIds(vaultId: VaultId): List<String>

    suspend fun get(vaultId: VaultId): Vault?

    fun getAsFlow(vaultId: VaultId): Flow<Vault?>

    suspend fun getByEcdsa(pubKeyEcdsa: String): Vault?

    suspend fun getAll(): List<Vault>

    fun getAllAsFlow(): Flow<List<Vault>>

    suspend fun hasVaults(): Boolean

    suspend fun hasAnyCoinOnChain(chain: Chain): Boolean

    fun observeHasAnyCoinOnChain(chain: Chain): Flow<Boolean>

    suspend fun isNameTaken(name: String, excludeId: VaultId): Boolean

    suspend fun add(vault: Vault)

    suspend fun upsert(vault: Vault)

    suspend fun setVaultName(vaultId: VaultId, name: String)

    suspend fun delete(vaultId: VaultId)

    suspend fun disableTokenFromVault(vaultId: VaultId, token: Coin)

    suspend fun deleteTokenFromVault(vaultId: VaultId, token: Coin)

    suspend fun deleteChainFromVault(vaultId: VaultId, chain: Chain)

    suspend fun addTokenToVault(vaultId: VaultId, token: Coin)

    suspend fun replaceTokenInVault(vaultId: VaultId, oldToken: Coin, newToken: Coin)
}

internal class VaultRepositoryImpl
@Inject
constructor(
    private val vaultDao: VaultDao,
    private val tokenRepository: TokenRepository,
    private val keyShareCipher: KeyShareCipher,
    private val passcodeDataKeySource: PasscodeDataKeySource,
) : VaultRepository {

    override fun getEnabledTokens(vaultId: String): Flow<List<Coin>> =
        getAsFlow(vaultId).map { it?.coins.orEmpty() }

    override fun getEnabledChains(vaultId: String): Flow<Set<Chain>> =
        getEnabledTokens(vaultId).map { tokens ->
            tokens.asSequence().filter { it.isNativeToken }.map { it.chain }.toSet()
        }

    override suspend fun getDisabledCoinIds(vaultId: VaultId) =
        vaultDao.loadDisabledCoinIds(vaultId)

    override suspend fun get(vaultId: String): Vault? = vaultDao.loadById(vaultId)?.toVault()

    override fun getAllAsFlow(): Flow<List<Vault>> {
        return vaultDao.loadAllAsFlow().map { it.map { dbVault -> dbVault.toVault() } }
    }

    override fun getAsFlow(vaultId: String): Flow<Vault?> =
        vaultDao.loadByIdAsFlow(vaultId).map { it?.toVault() }

    override suspend fun getByEcdsa(pubKeyEcdsa: String): Vault? =
        vaultDao.loadByEcdsa(pubKeyEcdsa)?.toVault()

    override suspend fun getAll(): List<Vault> = vaultDao.loadAll().map { it.toVault() }

    override suspend fun hasVaults(): Boolean = vaultDao.hasVaults()

    override suspend fun hasAnyCoinOnChain(chain: Chain): Boolean =
        vaultDao.hasCoinOnChain(chain.id)

    override fun observeHasAnyCoinOnChain(chain: Chain): Flow<Boolean> =
        vaultDao.observeHasCoinOnChain(chain.id)

    override suspend fun isNameTaken(name: String, excludeId: VaultId): Boolean =
        vaultDao.countByNameExcluding(name, excludeId) > 0

    override suspend fun add(vault: Vault) {
        vaultDao.insert(vault.toVaultDb())
    }

    override suspend fun upsert(vault: Vault) {
        vaultDao.upsert(vault.toVaultDb())
    }

    override suspend fun setVaultName(vaultId: String, name: String) {
        vaultDao.setVaultName(vaultId, name)
    }

    override suspend fun delete(vaultId: String) {
        vaultDao.delete(vaultId)
    }

    // delete token from coins table
    override suspend fun deleteTokenFromVault(vaultId: String, token: Coin) {
        vaultDao.deleteTokenFromVault(vaultId, token.id)
    }

    // delete token from coins table and add it to disabled coins table
    override suspend fun disableTokenFromVault(vaultId: String, token: Coin) {
        vaultDao.disableTokenFromVault(vaultId, token.id, token.chain.id)
    }

    override suspend fun deleteChainFromVault(vaultId: String, chain: Chain) {
        vaultDao.disableChainFromVault(vaultId, chain.id)
    }

    override suspend fun addTokenToVault(vaultId: String, token: Coin) {
        vaultDao.enableCoins(listOf(token.toCoinEntity(vaultId)))
    }

    override suspend fun replaceTokenInVault(vaultId: String, oldToken: Coin, newToken: Coin) {
        vaultDao.replaceToken(vaultId, oldToken.id, newToken.toCoinEntity(vaultId))
    }

    /**
     * Decrypts stored keyshares, dropping any that cannot be opened *while the app is locked*.
     *
     * A locked read is expected and harmless: background work (notifications, balance sync) still
     * reads vaults and only needs public keys and addresses. Returning the vault without its shares
     * keeps that work alive, and the write path refuses to persist a vault in that state, so a
     * locked read can never be echoed back as data loss.
     *
     * A failure with the data key in hand is a different thing entirely — a damaged row, or one
     * written for another vault — and returning a partial vault there would let the caller act as
     * if the share simply did not exist. That fails loudly instead.
     */
    private fun List<KeyShareEntity>.toKeyShares(vaultId: VaultId): List<KeyShare> {
        val dataKey = passcodeDataKeySource.dataKeyOrNull()
        val shares = mapNotNull { entity ->
            keyShareCipher.decrypt(entity.keyShare, dataKey, entity.identity())?.let {
                KeyShare(entity.pubKey, it)
            }
        }
        if (shares.size != size) {
            check(dataKey == null) {
                "Failed to decrypt ${size - shares.size} keyshare(s) for vault $vaultId"
            }
            Timber.w("Dropped %d locked keyshare(s) for vault %s", size - shares.size, vaultId)
        }
        return shares
    }

    /**
     * Encrypts [keyShare] for storage when a passcode is active.
     *
     * Refuses to write while locked rather than silently storing a share in the clear, which would
     * quietly undo the at-rest protection the user asked for.
     */
    private fun protect(
        keyShare: String,
        identity: KeyShareIdentity,
        dataKey: ByteArray?,
        isLocked: Boolean,
    ): String {
        if (dataKey != null) return keyShareCipher.encrypt(keyShare, dataKey, identity)
        check(!isLocked) { "Refusing to persist a keyshare while the app is locked" }
        return keyShare
    }

    private suspend fun VaultWithKeySharesAndTokens.toVault(): Vault {
        val vault = this
        return Vault(
            id = vault.vault.id,
            name = vault.vault.name,
            pubKeyECDSA = vault.vault.pubKeyEcdsa,
            pubKeyEDDSA = vault.vault.pubKeyEddsa,
            pubKeyMLDSA = vault.vault.pubKeyMldsa,
            hexChainCode = vault.vault.hexChainCode,
            localPartyID = vault.vault.localPartyID,
            signers = vault.signers.sortedBy { it.index }.map { it.title },
            resharePrefix = vault.vault.resharePrefix,
            keyshares = vault.keyShares.toKeyShares(vault.vault.id),
            coins =
                vault.coins.mapNotNull { coinEntity ->
                    val chain =
                        try {
                            Chain.fromRaw(coinEntity.chain)
                        } catch (e: NoSuchElementException) {
                            Timber.e(
                                e,
                                "Failed to map coin %s on chain %s, skipping",
                                coinEntity.id,
                                coinEntity.chain,
                            )
                            return@mapNotNull null
                        }

                    val logo =
                        try {
                            coinEntity.logo.takeIf { it.isNotBlank() }
                                ?: tokenRepository.getToken(coinEntity.id)?.logo
                                ?: ""
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to resolve logo for coin %s", coinEntity.id)
                            ""
                        }

                    Coin(
                        chain = chain,
                        ticker = coinEntity.ticker,
                        logo = logo,
                        address = coinEntity.address,
                        decimal = coinEntity.decimals,
                        hexPublicKey = coinEntity.hexPublicKey,
                        priceProviderID = coinEntity.priceProviderID,
                        contractAddress = coinEntity.contractAddress,
                        isNativeToken =
                            when (chain) {
                                Chain.MayaChain -> coinEntity.ticker == "CACAO"
                                else -> coinEntity.contractAddress.isBlank()
                            },
                    )
                },
            chainPublicKeys =
                vault.chainPublicKeys.map {
                    ChainPublicKey(chain = it.chain, publicKey = it.publicKey, isEddsa = it.isEddsa)
                },
            libType = vault.vault.libType,
        )
    }

    private fun Vault.toVaultDb(): VaultWithKeySharesAndTokens {
        val vault = this
        val vaultId = vault.id
        // One snapshot for the whole vault: reading the lock state per share would let a lock
        // landing mid-loop write some rows encrypted and the rest in the clear.
        val dataKey = passcodeDataKeySource.dataKeyOrNull()
        val isLocked = passcodeDataKeySource.isLocked()
        // Refuse here rather than inside the per-share mapping below. A vault read while locked
        // comes back with its shares dropped, so that mapping is empty and never reaches protect()
        // at all — the refusal would not fire, and only Room's habit of leaving keyshare rows
        // alone on an empty upsert would stand between this and silently erasing them.
        check(!(isLocked && dataKey == null)) {
            "Refusing to persist vault $vaultId while its keyshares are unreadable"
        }
        return VaultWithKeySharesAndTokens(
            vault =
                VaultEntity(
                    id = vaultId,
                    name = vault.name,
                    pubKeyEcdsa = vault.pubKeyECDSA,
                    pubKeyEddsa = vault.pubKeyEDDSA,
                    pubKeyMldsa = vault.pubKeyMLDSA,
                    hexChainCode = vault.hexChainCode,
                    localPartyID = vault.localPartyID,
                    resharePrefix = vault.resharePrefix,
                    libType = vault.libType,
                ),
            keyShares =
                vault.keyshares.map {
                    val identity = KeyShareIdentity(vaultId = vaultId, pubKey = it.pubKey)
                    KeyShareEntity(
                        vaultId = vaultId,
                        pubKey = it.pubKey,
                        keyShare = protect(it.keyShare, identity, dataKey, isLocked),
                    )
                },
            signers =
                vault.signers.mapIndexed { index, signer ->
                    SignerEntity(index = index, vaultId = vaultId, title = signer)
                },
            coins = vault.coins.map { it.toCoinEntity(vaultId) },
            chainPublicKeys =
                vault.chainPublicKeys.map {
                    ChainPublicKeyEntity(
                        vaultId = vaultId,
                        chain = it.chain,
                        publicKey = it.publicKey,
                        isEddsa = it.isEddsa,
                    )
                },
        )
    }

    private fun Coin.toCoinEntity(vaultId: String): CoinEntity =
        CoinEntity(
            vaultId = vaultId,
            // Coin.id is the single source of truth (contract-qualified for secured assets) —
            // reconstructing it manually here previously diverged from it silently.
            id = this.id,
            chain = this.chain.raw,
            ticker = this.ticker,
            address = this.address,
            decimals = this.decimal,
            hexPublicKey = this.hexPublicKey,
            priceProviderID = this.priceProviderID,
            contractAddress = this.contractAddress,
            logo = this.logo,
        )
}
