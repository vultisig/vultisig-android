package com.vultisig.wallet.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.db.models.FolderEntity
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.getVaultPart
import com.vultisig.wallet.data.models.isFastVault
import com.vultisig.wallet.ui.components.library.form.FormCard
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VaultCeil(
    folder: FolderEntity? = null,
    vault: Vault? = null,
    isInEditMode: Boolean,
    onSelect: (id: String) -> Unit,
    @SuppressLint("ComposableLambdaParameterNaming")
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val isFolder = when {
        folder != null -> true
        vault != null -> false
        else -> throw IllegalArgumentException("Either folderEntity or vault must be provided")
    }
    val id = (if (isFolder) folder?.id.toString() else vault?.id) ?: ""
    val name = (if (isFolder) folder?.name else vault?.name) ?: ""
    FormCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickOnce(onClick = { onSelect(id) })
                .padding(all = 14.dp)
        ) {
            if (isInEditMode) {
                Icon(
                    painter = painterResource(id = R.drawable.hamburger_menu),
                    contentDescription = "draggable item",
                    modifier = Modifier.width(16.dp)
                )
            }
            if (isFolder) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_folder),
                    contentDescription = "draggable item",
                    modifier = Modifier.width(16.dp)
                )
            }
            Text(
                text = name,
                style = Theme.menlo.body3,
                color = Theme.colors.neutral0,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isFolder) {
                    if (vault?.isFastVault() == true) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Theme.colors.oxfordBlue200,
                            ),
                        ) {
                            Text(
                                modifier = Modifier.padding(4.dp),
                                text = stringResource(id = R.string.vault_list_fast_badge),
                                style = Theme.menlo.body2,
                                color = Theme.colors.body,
                            )
                        }
                        UiSpacer(size = 4.dp)
                    }
                    Text(
                        text = stringResource(
                            id = R.string.vault_list_part_n_of_t,
                            vault?.getVaultPart() ?: "",
                            vault?.signers?.size ?: 0
                        ),
                        style = Theme.menlo.body2,
                        color = Theme.colors.body,
                    )
                }
                if (trailingIcon != null) {
                    trailingIcon()
                } else {
                    UiIcon(
                        R.drawable.caret_right, size = 20.dp
                    )
                }
            }
        }
    }
    UiSpacer(size = 12.dp)
}

@Preview
@Composable
private fun VaultCeilPreview() {
    Column {
        VaultCeil(
            vault = Vault(
                id = "1",
                name = "Vault 1",
            ),
            isInEditMode = true,
            onSelect = {}
        )

        VaultCeil(
            vault = Vault(
                id = "2",
                localPartyID = "1",
                signers = listOf("1", "server-2", "3"),
                name = "Vault 2",
            ),
            isInEditMode = false,
            onSelect = {}
        )

        VaultCeil(
            folder = FolderEntity(
                id = 1,
                name = "Folder 1",
            ),
            isInEditMode = false,
            onSelect = {}
        )

        VaultCeil(
            folder = FolderEntity(
                id = 2,
                name = "Folder 2",
            ),
            isInEditMode = true,
            onSelect = {}
        )
    }
}