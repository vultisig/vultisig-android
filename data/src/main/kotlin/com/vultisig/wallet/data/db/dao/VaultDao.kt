package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.vultisig.wallet.data.db.models.ChainPublicKeyEntity
import com.vultisig.wallet.data.db.models.CoinEntity
import com.vultisig.wallet.data.db.models.DisabledCoinEntity
import com.vultisig.wallet.data.db.models.KeyShareEntity
import com.vultisig.wallet.data.db.models.SignerEntity
import com.vultisig.wallet.data.db.models.VaultEntity
import com.vultisig.wallet.data.db.models.VaultWithKeySharesAndTokens
import com.vultisig.wallet.data.models.VaultId
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    @Transaction
    @Query("SELECT * FROM vault WHERE id = :vaultId")
    suspend fun loadById(vaultId: String): VaultWithKeySharesAndTokens?

    @Transaction
    @Query("SELECT * FROM vault WHERE id = :vaultId")
    fun loadByIdAsFlow(vaultId: String): Flow<VaultWithKeySharesAndTokens?>

    @Transaction
    @Query("SELECT * FROM vault WHERE pubKeyEcdsa = :pubKeyEcdsa")
    suspend fun loadByEcdsa(pubKeyEcdsa: String): VaultWithKeySharesAndTokens?

    @Transaction
    @Query("SELECT * FROM vault")
    suspend fun loadAll(): List<VaultWithKeySharesAndTokens>

    @Query("SELECT coinId FROM disabledCoin WHERE vaultId = :vaultId")
    suspend fun loadDisabledCoinIds(vaultId: VaultId): List<String>

    @Query("SELECT COUNT(*) > 0 FROM vault")
    suspend fun hasVaults(): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVault(vault: VaultEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoins(coins: List<CoinEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertKeyshares(keyshares: List<KeyShareEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSigners(signers: List<SignerEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertChainPublicKeys(chainPublicKeys: List<ChainPublicKeyEntity>)

    @Transaction
    suspend fun insert(vault: VaultWithKeySharesAndTokens) {
        insertVault(vault.vault)
        insertCoins(vault.coins)
        insertKeyshares(vault.keyShares)
        insertSigners(vault.signers)
        insertChainPublicKeys(vault.chainPublicKeys)
    }

    @Transaction
    suspend fun enableCoins(coins: List<CoinEntity>) {
        insertCoins(coins)
        coins.forEach {
            deleteFromDisabledCoin(vaultId = it.vaultId, coinId = it.id)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDisabledCoin(disabledCoin: DisabledCoinEntity)

    @Upsert
    suspend fun upsertVault(vault: VaultEntity)

    @Upsert
    suspend fun upsertCoins(coins: List<CoinEntity>)

    @Upsert
    suspend fun upsertKeyshares(keyshares: List<KeyShareEntity>)

    @Upsert
    suspend fun upsertSigners(signers: List<SignerEntity>)

    @Upsert
    suspend fun upsertChainPublicKeys(chainPublicKeys: List<ChainPublicKeyEntity>)

    @Query("DELETE FROM chainPublicKey WHERE vaultId = :vaultId")
    suspend fun deleteChainPublicKeys(vaultId: String)

    @Transaction
    suspend fun upsert(vault: VaultWithKeySharesAndTokens) {
        upsertVault(vault.vault)
        upsertCoins(vault.coins)
        upsertKeyshares(vault.keyShares)
        // it is upsert, so we need to delete all signers and insert them again
        // otherwise we need to figure who get removed , and remove them
        deleteSigners(vault.vault.id)
        upsertSigners(vault.signers)
        deleteChainPublicKeys(vault.vault.id)
        upsertChainPublicKeys(vault.chainPublicKeys)
    }

    @Query("DELETE FROM coin WHERE vaultId = :vaultId AND id = :tokenId")
    suspend fun deleteTokenFromVault(vaultId: String, tokenId: String)

    @Query("DELETE FROM coin WHERE vaultId = :vaultId AND chain == :chainId")
    suspend fun deleteChainFromVault(vaultId: String, chainId: String)

    @Transaction
    suspend fun disableChainFromVault(vaultId: String, chainId: String) {
        deleteChainFromVault(vaultId, chainId)
        deleteChainFromDisabledCoin(vaultId, chainId)
    }

    @Query("UPDATE vault SET name = :name WHERE id = :vaultId")
    suspend fun setVaultName(vaultId: String, name: String)

    @Query("DELETE FROM signer WHERE vaultId = :vaultId")
    suspend fun deleteSigners(vaultId: String)
    @Query("DELETE FROM vault WHERE id = :vaultId")
    suspend fun delete(vaultId: String)

    @Query("DELETE FROM disabledCoin WHERE vaultId = :vaultId AND coinId = :coinId")
    suspend fun deleteFromDisabledCoin(vaultId: VaultId, coinId: String)

    @Query("DELETE FROM disabledCoin WHERE vaultId = :vaultId AND chain = :chain")
    suspend fun deleteChainFromDisabledCoin(vaultId: VaultId, chain: String)

    @Transaction
    suspend fun disableTokenFromVault(vaultId: String, tokenId: String, chain: String) {
        deleteTokenFromVault(vaultId, tokenId)
        insertDisabledCoin(
            DisabledCoinEntity(
                vaultId = vaultId,
                coinId = tokenId,
                chain = chain,
            )
        )
    }
}