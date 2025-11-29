package com.vultisig.wallet.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.AppVersionText
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VsSwitch
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.referral.ReferralCodeBottomSheet
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.settings.SettingsItem
import com.vultisig.wallet.ui.models.settings.SettingsItemUiModel
import com.vultisig.wallet.ui.models.settings.SettingsUiEvent
import com.vultisig.wallet.ui.models.settings.SettingsUiModel
import com.vultisig.wallet.ui.models.settings.SettingsViewModel
import com.vultisig.wallet.ui.screens.send.FadingHorizontalDivider
import com.vultisig.wallet.ui.screens.settings.bottomsheets.sharelink.ShareLinkBottomSheet
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsUriHandler
import com.vultisig.wallet.ui.utils.asString
import com.vultisig.wallet.ui.utils.asUiText

@Composable
fun SettingsScreen() {
    val viewModel = hiltViewModel<SettingsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = VsUriHandler()

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect {
            when (it) {
                is SettingsUiEvent.OpenLink -> uriHandler.openUri(it.url)
            }
        }
    }

    SettingsScreen(
        state = state,
        onSettingsItemClick = viewModel::onSettingsItemClick,
        onBackClick = viewModel::back,
        onContinueReferral = viewModel::onContinueReferralBottomSheet,
        onDismissReferral = viewModel::onDismissReferralBottomSheet,
        onShareVaultQrClick = viewModel::onShareVaultQrClick,
        onDismissShareLink = viewModel::onDismissShareLinkBottomSheet,
    )
}

@Composable
private fun SettingsScreen(
    state: SettingsUiModel,
    onSettingsItemClick: (SettingsItem) -> Unit,
    onShareVaultQrClick: () -> Unit,
    onBackClick: () -> Unit,
    onContinueReferral: () -> Unit,
    onDismissReferral: () -> Unit,
    onDismissShareLink: () -> Unit,
) {
    V2Scaffold(
        title = stringResource(R.string.settings_screen_title),
        onBackClick = onBackClick,
        rightIcon = R.drawable.navigation_qr_code,
        onRightIconClick = onShareVaultQrClick,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                state.items.forEach { groupItem ->
                    SettingsBox(title = groupItem.title.asString()) {
                        val enabledSettings = groupItem.items.filter(SettingsItem::enabled)
                        enabledSettings
                            .forEachIndexed { index, settingItem ->
                                SettingItem(
                                    item = settingItem.value,
                                    isLastItem = index == enabledSettings.lastIndex,
                                    onClick = {
                                        onSettingsItemClick(settingItem)
                                    }
                                )
                            }
                    }
                }
            }

            UiSpacer(
                size = 15.dp
            )

            AppVersionText()
        }

        if (state.hasToShowReferralCodeSheet) {
            ReferralCodeBottomSheet(
                onContinue = onContinueReferral,
                onDismissRequest = onDismissReferral,
            )
        }

        if (state.showShareBottomSheet) {
            ShareLinkBottomSheet(
                onDismissRequest = onDismissShareLink
            )
        }

    }
}


@Composable
internal fun SettingsBox(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier.fillMaxWidth()
    ) {
        title?.let {
            Text(
                text = it,
                color = Theme.v2.colors.text.extraLight,
                style = Theme.brockmann.supplementary.caption

            )
            UiSpacer(size = 12.dp)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Theme.v2.colors.backgrounds.secondary,
                    shape = RoundedCornerShape(12.dp)
                )
                .clip(RoundedCornerShape(12.dp))
        ) {
            content()
        }
    }
}

@Composable
internal fun SettingItem(
    item: SettingsItemUiModel,
    onClick: () -> Unit,
    isLastItem: Boolean,
    tint: Color? = null,
) {
    Column {
        Row(
            modifier = Modifier
                .then(
                    if (item.backgroundColor != null)
                        Modifier.background(item.backgroundColor)
                    else Modifier
                )
                .fillMaxWidth()
                .clickOnce(onClick = onClick)
                .padding(
                    horizontal = 12.dp,
                    vertical = 16.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item.leadingIcon?.let { icon ->
                UiIcon(
                    drawableResId = icon,
                    size = 20.dp,
                    tint = tint ?: item.leadingIconTint ?: Theme.v2.colors.primary.accent4
                )
                UiSpacer(size = 16.dp)
            }

            Column {
                Text(
                    text = item.title.asString(),
                    style = Theme.brockmann.supplementary.footnote,
                    color = tint ?: Theme.v2.colors.text.primary
                )

                item.subTitle?.let {
                    Text(
                        text = it.asString(),
                        color = tint ?: Theme.v2.colors.text.light,
                        style = Theme.brockmann.supplementary.caption,
                    )
                }

            }

            UiSpacer(weight = 1f)

            item.trailingSwitch?.let { isChecked ->

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VsSwitch(
                        checked = isChecked,
                        onCheckedChange = null
                    )
                    Text(
                        text = if (isChecked) "ON" else "OFF",
                        style = Theme.brockmann.button.medium.medium,
                        color = tint ?: Theme.v2.colors.text.primary
                    )
                }

                UiSpacer(size = 8.dp)
            }

            item.value?.let { value ->
                Text(
                    text = value,
                    style = Theme.brockmann.supplementary.footnote,
                    color = tint ?: Theme.v2.colors.text.primary
                )
                UiSpacer(size = 12.dp)
            }

            item.trailingIcon?.let { trailingIcon ->
                Image(
                    imageVector = ImageVector.vectorResource(trailingIcon),
                    modifier = Modifier.size(16.dp),
                    contentDescription = "trailing icon"
                )
            }
        }

        if (isLastItem.not()) {
            FadingHorizontalDivider()
        }
    }
}

@Preview
@Composable
private fun SettingsItemPreview() {
    SettingItem(
        item = SettingsItemUiModel(
            title = "title".asUiText(),
            subTitle = "subTitle".asUiText(),
            leadingIcon = R.drawable.currency,
            trailingIcon = R.drawable.ic_small_caret_right,
            value = "value",
            backgroundColor = Theme.v2.colors.backgrounds.primary
        ),
        onClick = {},
        isLastItem = false
    )
}
