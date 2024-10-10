package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.vultisig.wallet.data.db.models.CoinEntity
import com.vultisig.wallet.data.db.models.KeyShareEntity
import com.vultisig.wallet.data.db.models.SignerEntity
import com.vultisig.wallet.data.db.models.VaultEntity
import com.vultisig.wallet.data.db.models.VaultWithKeySharesAndTokens

@Dao
interface VaultDao {

    @Transaction
    @Query("SELECT * FROM vault WHERE id = :vaultId")
    suspend fun loadById(vaultId: String): VaultWithKeySharesAndTokens?

    @Transaction
    @Query("SELECT * FROM vault WHERE pubKeyEcdsa = :pubKeyEcdsa")
    suspend fun loadByEcdsa(pubKeyEcdsa: String): VaultWithKeySharesAndTokens?

    @Transaction
    @Query("SELECT * FROM vault")
    suspend fun loadAll(): List<VaultWithKeySharesAndTokens>

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

    @Transaction
    suspend fun insert(vault: VaultWithKeySharesAndTokens) {
        insertVault(vault.vault)
        insertCoins(vault.coins)
        insertKeyshares(vault.keyShares)
        insertSigners(vault.signers)
    }

    @Upsert
    suspend fun upsertVault(vault: VaultEntity)

    @Upsert
    suspend fun upsertCoins(coins: List<CoinEntity>)

    @Upsert
    suspend fun upsertKeyshares(keyshares: List<KeyShareEntity>)

    @Upsert
    suspend fun upsertSigners(signers: List<SignerEntity>)

    @Transaction
    suspend fun upsert(vault: VaultWithKeySharesAndTokens) {
        upsertVault(vault.vault)
        upsertCoins(vault.coins)
        upsertKeyshares(vault.keyShares)
        // it is upsert, so we need to delete all signers and insert them again
        // otherwise we need to figure who get removed , and remove them
        deleteSigners(vault.vault.id)
        upsertSigners(vault.signers)
    }

    @Query("DELETE FROM coin WHERE vaultId = :vaultId AND id = :tokenId")
    suspend fun deleteTokenFromVault(vaultId: String, tokenId: String)

    @Query("DELETE FROM coin WHERE vaultId = :vaultId AND chain == :chainId")
    suspend fun deleteChainFromVault(vaultId: String, chainId: String)

    @Query("UPDATE vault SET name = :name WHERE id = :vaultId")
    suspend fun setVaultName(vaultId: String, name: String)

    @Query("DELETE FROM signer WHERE vaultId = :vaultId")
    suspend fun deleteSigners(vaultId: String)
    @Query("DELETE FROM vault WHERE id = :vaultId")
    suspend fun delete(vaultId: String)

}