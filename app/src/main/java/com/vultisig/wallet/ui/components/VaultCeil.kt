package com.vultisig.wallet.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.library.form.FormCard
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.CornerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.icons.VaultIcon
import com.vultisig.wallet.ui.theme.Theme

internal data class VaultCeilUiModel(
    val id: String,
    val name: String,
    val isFolder: Boolean,
    val isFastVault: Boolean = false,
    val vaultPart: Int = 0,
    val signersSize: Int = 0,
    val balance: String? = null,
)


@Composable
internal fun VaultCeil(
    model: VaultCeilUiModel,
    isInEditMode: Boolean,
    onSelect: (id: String) -> Unit,
    isSelected: Boolean,
    activeVaultName: String?,
    vaultCounts: Int?,
    trailingContent: @Composable (() -> Unit)? = null,
) {

    val (logo, containerType) =
        if (isSelected)
            Theme.colors.backgrounds.tertiary to Theme.colors.backgrounds.tertiary to ContainerType.SECONDARY
        else
            Theme.colors.backgrounds.secondary to Theme.colors.backgrounds.tertiary to ContainerType.PRIMARY

    val (logoBackground, logoBorder) = logo
    FormCard {
        V2Container(
            type = containerType,
            cornerType = CornerType.RoundedCornerShape(size = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickOnce(onClick = { onSelect(model.id) })
                    .padding(
                        horizontal = 12.dp,
                        vertical = 12.dp
                    )
            ) {

                AnimatedVisibility(isInEditMode) {
                    if (isInEditMode) {
                        Row {
                            UiIcon(
                                drawableResId = R.drawable.hamburger_menu,
                                tint = Theme.colors.text.extraLight,
                                size = 16.dp,
                            )
                            UiSpacer(
                                size = 12.dp
                            )
                        }
                    }
                }


                Box(
                    modifier = Modifier
                        .size(
                            size = 40.dp
                        )
                        .clip(
                            shape = CircleShape
                        )
                        .border(
                            width = 1.dp,
                            color = logoBorder,
                            shape = CircleShape
                        )
                        .background(
                            color = logoBackground
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (model.isFolder) {
                        UiIcon(
                            size = 16.dp,
                            drawableResId = if (isSelected) R.drawable.folder_selected else R.drawable.folder,
                            tint = Theme.colors.alerts.info
                        )
                    } else {
                        VaultIcon(
                            isFastVault = model.isFastVault
                        )
                    }

                }


                UiSpacer(
                    size = 12.dp
                )

                Column {
                    Text(
                        text = model.name,
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.colors.text.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 150.dp),
                    )
                    UiSpacer(
                        size = 2.dp
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (model.isFolder && isSelected && activeVaultName != null) {
                            UiIcon(
                                drawableResId = R.drawable.ic_check,
                                tint = Theme.colors.alerts.info,
                                size = 16.dp,
                            )
                            UiSpacer(
                                size = 4.dp
                            )
                        }

                        if (activeVaultName != null) {
                            Row {

                                ActiveVaultName(
                                    isSelected = isSelected,
                                    isFolder = model.isFolder,
                                    content = "'"
                                )
                                ActiveVaultName(
                                    isSelected = isSelected,
                                    isFolder = model.isFolder,
                                    content = activeVaultName
                                )
                                ActiveVaultName(
                                    isSelected = isSelected,
                                    isFolder = model.isFolder,
                                    content = "'"
                                )

                                UiSpacer(
                                    size = 4.dp
                                )

                                ActiveVaultName(
                                    isSelected = isSelected,
                                    isFolder = model.isFolder,
                                    content = stringResource(R.string.vault_ceil_active)
                                )

                            }
                        } else {
                            Text(
                                text = if (model.isFolder) "$vaultCounts Vault${if (vaultCounts != 1) "s" else ""}" else model.balance
                                    ?: "",
                                style = Theme.brockmann.supplementary.footnote,
                                color = if (model.isFolder && isSelected)
                                    Theme.colors.alerts.info else Theme.colors.text.extraLight,

                                )
                        }
                    }
                }



                UiSpacer(
                    weight = 1f
                )

                if (trailingContent != null)
                    trailingContent()
                else {
                    if (!model.isFolder) {

                        if (isSelected) {
                            UiIcon(
                                drawableResId = R.drawable.ic_check,
                                tint = Theme.colors.alerts.success,
                                size = 20.dp,
                            )
                            UiSpacer(
                                size = 8.dp
                            )
                        }

                        Text(
                            text = stringResource(
                                id = R.string.vault_list_part_n_of_t,
                                model.vaultPart,
                                model.signersSize
                            ),
                            style = Theme.brockmann.supplementary.caption,
                            color = Theme.colors.text.extraLight,
                            modifier = Modifier
                                .border(
                                    shape = RoundedCornerShape(
                                        size = 8.dp
                                    ),
                                    color = Theme.colors.borders.light,
                                    width = 1.dp
                                )
                                .padding(
                                    horizontal = 8.dp,
                                    vertical = 4.dp
                                )
                        )
                    }

                    if (model.isFolder && isInEditMode.not())
                        UiIcon(
                            drawableResId = R.drawable.ic_small_caret_right,
                            size = 20.dp,
                            tint = Theme.colors.text.primary,
                        )
                }
            }
        }
    }
}

@Composable
private fun ActiveVaultName(
    isSelected: Boolean,
    isFolder: Boolean,
    content: String,
) {
    Text(
        text = content,
        style = Theme.brockmann.supplementary.footnote,
        color = if (isFolder && isSelected)
            Theme.colors.alerts.info else Theme.colors.text.extraLight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 150.dp),
    )
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
            onSelect = {},
            isSelected = true,
            activeVaultName = null,
            vaultCounts = null,
        )

        VaultCeil(
            model = VaultCeilUiModel(
                id = "2",
                name = "Vault 2",
                isFolder = false,
                isFastVault = true,
                vaultPart = 2,
                signersSize = 3,
                balance = "$102.12"
            ),
            isInEditMode = false,
            onSelect = {},
            isSelected = false,
            activeVaultName = null,
            vaultCounts = null,
        )

        VaultCeil(
            model = VaultCeilUiModel(
                id = "2",
                name = "Vault 2",
                isFolder = false,
                isFastVault = true,
                vaultPart = 2,
                signersSize = 3,
                balance = "$102.12"
            ),
            isInEditMode = false,
            onSelect = {},
            isSelected = false,
            activeVaultName = null,
            vaultCounts = null,
            trailingContent = {
                VsSwitch(
                    checked = true,
                    onCheckedChange = {}
                )
            }
        )

        VaultCeil(
            model = VaultCeilUiModel(
                id = "1",
                name = "Folder Name",
                isFolder = true,
            ),
            isInEditMode = false,
            onSelect = {},
            isSelected = true,
            activeVaultName = "Main Vault",
            vaultCounts = 2,
        )

        VaultCeil(
            model = VaultCeilUiModel(
                id = "2",
                name = "Folder 2",
                isFolder = true,
            ),
            isInEditMode = true,
            onSelect = {},
            isSelected = false,
            activeVaultName = null,
            vaultCounts = 3
        )
    }
}