package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.db.models.FolderEntity

private const val FOLDER_PREFIX = "folder_"

sealed class VaultListEntity(
    val id: String,
    val name: String,
) {
    data class VaultListItem(
        val vault: Vault,
    ) : VaultListEntity(vault.id, vault.name)

    data class FolderListItem(
        val folder: FolderEntity,
    ) : VaultListEntity(FOLDER_PREFIX + folder.id.toString(), folder.name)
}