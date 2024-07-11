package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.VaultDao
import com.vultisig.wallet.data.db.models.CoinEntity
import com.vultisig.wallet.data.db.models.KeyShareEntity
import com.vultisig.wallet.data.db.models.SignerEntity
import com.vultisig.wallet.data.db.models.VaultEntity
import com.vultisig.wallet.data.db.models.VaultWithKeySharesAndTokens
import com.vultisig.wallet.data.managers.VaultDataStoreRepository
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.KeyShare
import com.vultisig.wallet.models.Vault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal interface VaultRepository {

    fun getEnabledTokens(vaultId: String): Flow<List<Coin>>

    fun getEnabledChains(vaultId: String): Flow<Set<Chain>>


    suspend fun get(vaultId: String): Vault?

    suspend fun getAll(): List<Vault>

    suspend fun hasVaults(): Boolean

    suspend fun add(vault: Vault, haveBackup: Boolean = false)

    suspend fun upsert(vault: Vault)

    suspend fun setVaultName(vaultId: String, name: String)

    suspend fun delete(vaultId: String)

    suspend fun deleteTokenFromVault(vaultId: String, tokenId: String)

    suspend fun deleteChainFromVault(vaultId: String, chain: Chain)

    suspend fun addTokenToVault(vaultId: String, token: Coin)

    suspend fun setVaultBackupStatus(vaultId: String, status: Boolean)

    suspend fun getVaultBackupStatus(vaultId: String): Flow<Boolean>

}

internal class VaultRepositoryImpl @Inject constructor(
    private val vaultDao: VaultDao,
    private val tokenRepository: TokenRepository,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
) : VaultRepository {

    override fun getEnabledTokens(vaultId: String): Flow<List<Coin>> = flow {
        emit(requireNotNull(get(vaultId)?.coins))
    }

    override fun getEnabledChains(vaultId: String): Flow<Set<Chain>> =
        getEnabledTokens(vaultId)
            .map { enabledTokens ->
                enabledTokens.asSequence()
                    .filter { it.isNativeToken }
                    .map { it.chain }
                    .toSet()
            }

    override suspend fun get(vaultId: String): Vault? =
        vaultDao.loadById(vaultId)?.toVault()

    override suspend fun getAll(): List<Vault> =
        vaultDao.loadAll().map { it.toVault() }

    override suspend fun hasVaults(): Boolean =
        vaultDao.hasVaults()

    override suspend fun add(vault: Vault, haveBackup: Boolean) {
        vaultDao.insert(vault.toVaultDb())
        vaultDataStoreRepository.setBackupStatus(
            vaultId = vault.id,
            status = haveBackup,
        )
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

    override suspend fun deleteTokenFromVault(vaultId: String, tokenId: String) {
        vaultDao.deleteTokenFromVault(vaultId, tokenId)
    }

    override suspend fun deleteChainFromVault(vaultId: String, chain: Chain) {
        vaultDao.deleteChainFromVault(vaultId, chain.id)
    }

    override suspend fun addTokenToVault(vaultId: String, token: Coin) {
        vaultDao.insertCoins(listOf(token.toCoinEntity(vaultId)))
    }

    override suspend fun setVaultBackupStatus(vaultId: String, status: Boolean) {
        vaultDataStoreRepository.setBackupStatus(vaultId, status)
    }

    override suspend fun getVaultBackupStatus(vaultId: String): Flow<Boolean> {
        return vaultDataStoreRepository.readBackupStatus(vaultId)
    }

    private suspend fun VaultWithKeySharesAndTokens.toVault(): Vault {
        val vault = this
        return Vault(
            id = vault.vault.id,
            name = vault.vault.name,
            pubKeyECDSA = vault.vault.pubKeyEcdsa,
            pubKeyEDDSA = vault.vault.pubKeyEddsa,
            hexChainCode = vault.vault.hexChainCode,
            localPartyID = vault.vault.localPartyID,
            signers = vault.signers.map { it.title },
            resharePrefix = vault.vault.resharePrefix,
            keyshares = vault.keyShares.map {
                KeyShare(
                    it.pubKey,
                    it.keyShare,
                )
            },
            coins = vault.coins.map {
                Coin(
                    chain = Chain.fromRaw(it.chain),
                    ticker = it.ticker,
                    logo = it.logo.takeIf { it.isNotBlank() }
                        ?: tokenRepository.getToken(it.id)?.logo ?: "",
                    address = it.address,
                    decimal = it.decimals,
                    hexPublicKey = it.hexPublicKey,
                    priceProviderID = it.priceProviderID,
                    contractAddress = it.contractAddress,
                    isNativeToken = it.contractAddress.isBlank(),
                )
            },
        )
    }

    private fun Vault.toVaultDb(): VaultWithKeySharesAndTokens {
        val vault = this
        val vaultId = vault.id
        return VaultWithKeySharesAndTokens(
            vault = VaultEntity(
                id = vaultId,
                name = vault.name,
                pubKeyEcdsa = vault.pubKeyECDSA,
                pubKeyEddsa = vault.pubKeyEDDSA,
                hexChainCode = vault.hexChainCode,
                localPartyID = vault.localPartyID,
                resharePrefix = vault.resharePrefix,
            ),
            keyShares = vault.keyshares.map {
                KeyShareEntity(
                    vaultId = vaultId,
                    pubKey = it.pubKey,
                    keyShare = it.keyshare,
                )
            },
            signers = vault.signers.map {
                SignerEntity(
                    vaultId = vaultId,
                    title = it
                )
            },
            coins = vault.coins.map {
                it.toCoinEntity(vaultId)
            },
        )
    }

    private fun Coin.toCoinEntity(vaultId: String): CoinEntity =
        CoinEntity(
            vaultId = vaultId,
            id = "${this.ticker}-${this.chain.raw}",
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