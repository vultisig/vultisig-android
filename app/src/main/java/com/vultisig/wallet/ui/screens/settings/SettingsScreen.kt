package com.vultisig.wallet.ui.screens.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.settings.SettingsItem
import com.vultisig.wallet.ui.models.settings.SettingsUiEvent
import com.vultisig.wallet.ui.models.settings.SettingsUiModel
import com.vultisig.wallet.ui.models.settings.SettingsViewModel
import com.vultisig.wallet.ui.screens.send.FadingHorizontalDivider
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsAuxiliaryLinks
import com.vultisig.wallet.ui.utils.VsUriHandler

@Composable
fun SettingsScreen() {
    val viewModel = hiltViewModel<SettingsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = VsUriHandler()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect {
            when (it) {
                is SettingsUiEvent.OpenLink -> uriHandler.openUri(it.url)
                is SettingsUiEvent.OpenGooglePlay -> context.openGooglePlay()
            }
        }
    }

    SettingsScreen(
        state = state,
        onSettingsItemClick = viewModel::onSettingsItemClick
    )
}

@Composable
private fun SettingsScreen(
    state: SettingsUiModel,
    onSettingsItemClick: (SettingsItem) -> Unit
) {
    Scaffold(
        topBar = {
            VsTopAppBar(
                title = "Settings",
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .padding(it)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                state.items.forEach { groupItem ->
                    SettingsBox(title = groupItem.title) {
                        groupItem.items.forEachIndexed { index, settingItem ->
                            SettingItem(
                                item = settingItem,
                                isLastItem = index == groupItem.items.lastIndex,
                                onClick = {
                                    onSettingsItemClick(settingItem)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}


private fun Context.openGooglePlay() {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(
            Intent.EXTRA_TEXT,
            VsAuxiliaryLinks.GOOGLE_PLAY
        )
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(
        sendIntent,
        null
    )
    startActivity(shareIntent)
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
                color = Theme.colors.text.extraLight,
                style = Theme.brockmann.supplementary.caption

            )
            UiSpacer(size = 12.dp)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Theme.colors.backgrounds.neutral,
                    shape = RoundedCornerShape(12.dp)
                )
                .clip(RoundedCornerShape(12.dp))
        ) {
            content()
        }
    }
}

@Composable
private fun SettingItem(
    item: SettingsItem,
    onClick: () -> Unit,
    isLastItem: Boolean
) {
    Column {
        val itemValue = item.value
        Row(
            modifier = Modifier
                .then(
                    if (itemValue.backgroundColor != null)
                        Modifier.background(itemValue.backgroundColor)
                    else Modifier
                )
                .fillMaxWidth()
                .clickOnce(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemValue.icon?.let { icon ->
                UiIcon(
                    drawableResId = icon,
                    size = 20.dp,
                    tint = Theme.colors.primary.accent4
                )
                UiSpacer(size = 16.dp)
            }

            Text(
                text = itemValue.title,
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.colors.text.primary
            )

            UiSpacer(weight = 1f)

            itemValue.value?.let { value ->
                Text(
                    text = value,
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.colors.text.primary
                )
                UiSpacer(size = 16.dp)
            }

            UiIcon(
                drawableResId = R.drawable.ic_small_caret_right,
                size = 16.dp,
                tint = Theme.colors.text.extraLight
            )

        }

        if (isLastItem.not()) {
            FadingHorizontalDivider()
        }
    }
}

private const val REFERRAL_FEATURE_FLAG = false