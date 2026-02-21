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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface VaultRepository {

    fun getEnabledTokens(vaultId: VaultId): Flow<List<Coin>>

    fun getEnabledChains(vaultId: VaultId): Flow<Set<Chain>>

    suspend fun getDisabledCoinIds(vaultId: VaultId): List<String>

    suspend fun get(vaultId: VaultId): Vault?

    fun getAsFlow(vaultId: VaultId): Flow<Vault?>

    suspend fun getByEcdsa(pubKeyEcdsa: String): Vault?

    suspend fun getAll(): List<Vault>

    suspend fun hasVaults(): Boolean

    suspend fun add(vault: Vault)

    suspend fun upsert(vault: Vault)

    suspend fun setVaultName(vaultId: VaultId, name: String)

    suspend fun delete(vaultId: VaultId)

    suspend fun disableTokenFromVault(vaultId: VaultId, token: Coin)

    suspend fun deleteTokenFromVault(vaultId: VaultId, token: Coin)

    suspend fun deleteChainFromVault(vaultId: VaultId, chain: Chain)

    suspend fun addTokenToVault(vaultId: VaultId, token: Coin)

}

internal class VaultRepositoryImpl @Inject constructor(
    private val vaultDao: VaultDao,
    private val tokenRepository: TokenRepository,
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

    override suspend fun getDisabledCoinIds(vaultId: VaultId) =
        vaultDao.loadDisabledCoinIds(vaultId)

    override suspend fun get(vaultId: String): Vault? =
        vaultDao.loadById(vaultId)?.toVault()

    override fun getAsFlow(vaultId: String): Flow<Vault?> =
        vaultDao.loadByIdAsFlow(vaultId).map { it?.toVault() }

    override suspend fun getByEcdsa(pubKeyEcdsa: String): Vault? =
        vaultDao.loadByEcdsa(pubKeyEcdsa)?.toVault()

    override suspend fun getAll(): List<Vault> =
        vaultDao.loadAll().map { it.toVault() }

    override suspend fun hasVaults(): Boolean =
        vaultDao.hasVaults()

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

    private suspend fun VaultWithKeySharesAndTokens.toVault(): Vault {
        val vault = this
        return Vault(
            id = vault.vault.id,
            name = vault.vault.name,
            pubKeyECDSA = vault.vault.pubKeyEcdsa,
            pubKeyEDDSA = vault.vault.pubKeyEddsa,
            hexChainCode = vault.vault.hexChainCode,
            localPartyID = vault.vault.localPartyID,
            signers = vault.signers.sortedBy { it.index }.map { it.title },
            resharePrefix = vault.vault.resharePrefix,
            keyshares = vault.keyShares.map {
                KeyShare(
                    it.pubKey,
                    it.keyShare,
                )
            },
            coins = vault.coins.map { it ->
                val chain = Chain.fromRaw(it.chain)
                Coin(
                    chain = chain,
                    ticker = it.ticker,
                    logo = it.logo.takeIf { it.isNotBlank() }
                        ?: tokenRepository.getToken(it.id)?.logo ?: "",
                    address = it.address,
                    decimal = it.decimals,
                    hexPublicKey = it.hexPublicKey,
                    priceProviderID = it.priceProviderID,
                    contractAddress = it.contractAddress,
                    isNativeToken = when (chain) {
                        Chain.MayaChain -> it.ticker == "CACAO"
                        else -> it.contractAddress.isBlank()
                    },
                )
            },
            chainPublicKeys = vault.chainPublicKeys.map {
                ChainPublicKey(
                    chain = it.chain,
                    publicKey = it.publicKey,
                    isEddsa = it.isEddsa,
                )
            },
            libType = vault.vault.libType,
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
                libType = vault.libType,
            ),
            keyShares = vault.keyshares.map {
                KeyShareEntity(
                    vaultId = vaultId,
                    pubKey = it.pubKey,
                    keyShare = it.keyShare,
                )
            },
            signers = vault.signers.mapIndexed { index, signer ->
                SignerEntity(
                    index = index,
                    vaultId = vaultId,
                    title = signer,
                )
            },
            coins = vault.coins.map {
                it.toCoinEntity(vaultId)
            },
            chainPublicKeys = vault.chainPublicKeys.map {
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