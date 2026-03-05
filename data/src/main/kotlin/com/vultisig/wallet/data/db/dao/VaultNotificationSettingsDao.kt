package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.vultisig.wallet.data.db.models.VaultNotificationSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultNotificationSettingsDao {

    @Upsert suspend fun upsert(entity: VaultNotificationSettingsEntity)

    @Query("SELECT * FROM vault_notification_settings WHERE vaultId = :vaultId")
    suspend fun getByVaultId(vaultId: String): VaultNotificationSettingsEntity?

    @Query("SELECT * FROM vault_notification_settings WHERE vaultId = :vaultId")
    fun observeByVaultId(vaultId: String): Flow<VaultNotificationSettingsEntity?>

    @Query("SELECT * FROM vault_notification_settings WHERE notificationsEnabled = 1")
    suspend fun getAllEnabled(): List<VaultNotificationSettingsEntity>

    @Query("SELECT * FROM vault_notification_settings")
    fun observeAll(): Flow<List<VaultNotificationSettingsEntity>>

    @Query(
        "UPDATE vault_notification_settings SET notificationsEnabled = :enabled WHERE vaultId = :vaultId"
    )
    suspend fun setEnabled(vaultId: String, enabled: Boolean)

    @Query(
        "UPDATE vault_notification_settings SET notificationsPrompted = 1 WHERE vaultId = :vaultId"
    )
    suspend fun markPrompted(vaultId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNotExists(entity: VaultNotificationSettingsEntity)
}
