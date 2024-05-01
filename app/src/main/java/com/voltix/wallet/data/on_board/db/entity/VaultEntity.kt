package com.voltix.wallet.data.on_board.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.voltix.wallet.models.Chain
import java.util.Date

@Entity(
    tableName = "Vault",
    indices = [Index(value = ["local_party_id"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = KeyShareEntity::class,
            parentColumns = ["vault_local_party_id"],
            childColumns = ["local_party_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CoinEntity::class,
            parentColumns = ["vault_local_party_id"],
            childColumns = ["local_party_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)

data class VaultEntity(
    @PrimaryKey()
    @ColumnInfo("id")
    var Id: String = "",

    @ColumnInfo("local_party_id")
    var localPartyID: String = "",

    @ColumnInfo("pubkey_ecdsa")
    var pubKeyECDSA: String = "",
    @ColumnInfo("pubkey_eddsa")
    var pubKeyEDDSA: String = "",
    @ColumnInfo("create_at")
    var createdAt: Date = Date(),
    @ColumnInfo("hex_chain_code")
    var hexChainCode: String = "",
    @ColumnInfo("reshare_prefix")
    var resharePrefix: String = "",
)

@Entity(
    tableName = "KeyShare",
    indices = [Index(value = ["vault_local_party_id"], unique = false)]
)
data class KeyShareEntity(
    @PrimaryKey()
    @ColumnInfo("id")
    var Id: String = "",

    @ColumnInfo("vault_local_party_id")
    val vaultId: String,

    @ColumnInfo("pub_key")
    val pubKey: String,
    @ColumnInfo("key_share")
    val keyShare: String,
)

@Entity(
    tableName = "Coin",
    indices = [Index(value = ["vault_local_party_id"], unique = false)]

)
data class CoinEntity(

    @PrimaryKey()
    @ColumnInfo("id")
    var Id: String = "",

    @ColumnInfo("vault_local_party_id")
    val vaultId: String,

    @ColumnInfo("chain")
    val chain: Chain,
    @ColumnInfo("ticker")
    val ticker: String,
    @ColumnInfo("logo")
    val logo: String,
    @ColumnInfo("address")
    var address: String,
    @ColumnInfo("decimal")
    val decimal: Int,
    @ColumnInfo("hex_publickey")
    val hexPublicKey: String,
    @ColumnInfo("fee_unit")
    val feeUnit: String,
    @ColumnInfo("fee_default")
    val feeDefault: Double,
    @ColumnInfo("price_provider_id")
    val priceProviderID: String,
    @ColumnInfo("contract_address")
    var contractAddress: String,
    @ColumnInfo("raw_balance")
    var rawBalance: Long,
    @ColumnInfo("is_native_token")
    val isNativeToken: Boolean,
    @ColumnInfo("price_rate")
    var priceRate: Double,
)

data class VaultWithKeyShare(
    @Embedded val vaultEntity: VaultEntity,

    @Relation(
        parentColumn = "local_party_id",
        entityColumn = "vault_local_party_id"
    )
    val keyShareEntityList: List<KeyShareEntity>?,

    @Relation(
        parentColumn = "local_party_id",
        entityColumn = "vault_local_party_id"
    )
    val coinEntityList: List<CoinEntity>?
)

