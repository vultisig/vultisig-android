package com.vultisig.wallet.ui.components

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
import com.vultisig.wallet.ui.components.library.form.FormCard
import com.vultisig.wallet.ui.theme.Theme

internal data class VaultCeilUiModel(
    val id: String,
    val name: String,
    val isFolder: Boolean,
    val isFastVault: Boolean = false,
    val vaultPart: Int = 0,
    val signersSize: Int = 0,
)


@Composable
internal fun VaultCeil(
    model: VaultCeilUiModel,
    isInEditMode: Boolean,
    onSelect: (id: String) -> Unit,
    trailingIcon: @Composable (() -> Unit)? = null,
) {

    FormCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickOnce(onClick = { onSelect(model.id) })
                .padding(all = 14.dp)
        ) {
            if (isInEditMode) {
                Icon(
                    painter = painterResource(id = R.drawable.hamburger_menu),
                    contentDescription = "draggable item",
                    modifier = Modifier.width(16.dp)
                )
            }
            if (model.isFolder) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_folder),
                    contentDescription = "draggable item",
                    modifier = Modifier.width(16.dp)
                )
            }
            Text(
                text = model.name,
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
                if (!model.isFolder) {
                    if (model.isFastVault) {
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
                            model.vaultPart,
                            model.signersSize
                        ),
                        style = Theme.menlo.body2,
                        color = Theme.colors.body,
                    )
                }
                if (trailingIcon != null) {
                    trailingIcon()
                } else {
                    UiIcon(
                        R.drawable.ic_small_caret_right, size = 20.dp
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
            model = VaultCeilUiModel(
                id = "1",
                name = "Vault 1",
                isFolder = false,
            ),
            isInEditMode = true,
            onSelect = {}
        )

        VaultCeil(
            model = VaultCeilUiModel(
                id = "2",
                name = "Vault 2",
                isFolder = false,
                isFastVault = true,
                vaultPart = 2,
                signersSize = 3,
            ),
            isInEditMode = false,
            onSelect = {}
        )

        VaultCeil(
            model = VaultCeilUiModel(
                id = "1",
                name = "Folder 1",
                isFolder = true,
            ),
            isInEditMode = false,
            onSelect = {}
        )

        VaultCeil(
            model = VaultCeilUiModel(
                id = "2",
                name = "Folder 2",
                isFolder = true,
            ),
            isInEditMode = true,
            onSelect = {}
        )
    }
}