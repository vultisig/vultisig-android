package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.vultisig.wallet.data.models.SigningLibType

@Entity(
    tableName = "vault",
)
data class VaultEntity(
    @PrimaryKey
    @ColumnInfo("id")
    val id: String,

    @ColumnInfo("name")
    val name: String,

    @ColumnInfo("localPartyId")
    val localPartyID: String,
    @ColumnInfo("pubKeyEcdsa")
    val pubKeyEcdsa: String,
    @ColumnInfo("pubKeyEddsa")
    val pubKeyEddsa: String,
    @ColumnInfo("hexChainCode")
    val hexChainCode: String,
    @ColumnInfo("resharePrefix")
    val resharePrefix: String,
    @ColumnInfo("libType")
    val libType: SigningLibType,
)

@Entity(
    tableName = "keyShare",
    primaryKeys = ["vaultId", "pubKey"],
    foreignKeys = [
        ForeignKey(
            entity = VaultEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaultId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class KeyShareEntity(
    @ColumnInfo("vaultId")
    val vaultId: String,

    @ColumnInfo("pubKey")
    val pubKey: String,
    @ColumnInfo("keyShare")
    val keyShare: String,
)

@Entity(
    tableName = "signer",
    primaryKeys = ["vaultId", "title"],
    foreignKeys = [
        ForeignKey(
            entity = VaultEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaultId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class SignerEntity(
    @ColumnInfo("index")
    val index: Int,

    @ColumnInfo("vaultId")
    val vaultId: String,

    @ColumnInfo("title")
    val title: String,
)

@Entity(
    tableName = "chainPublicKey",
    primaryKeys = ["vaultId", "chain"],
    foreignKeys = [
        ForeignKey(
            entity = VaultEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaultId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class ChainPublicKeyEntity(
    @ColumnInfo("vaultId")
    val vaultId: String,
    @ColumnInfo("chain")
    val chain: String,
    @ColumnInfo("publicKey")
    val publicKey: String,
    @ColumnInfo("isEddsa", defaultValue = "0")
    val isEddsa: Boolean,
)


data class VaultWithKeySharesAndTokens(
    @Embedded val vault: VaultEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "vaultId"
    )
    val keyShares: List<KeyShareEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "vaultId"
    )
    val signers: List<SignerEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "vaultId"
    )
    val coins: List<CoinEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "vaultId"
    )
    val chainPublicKeys: List<ChainPublicKeyEntity>,
)