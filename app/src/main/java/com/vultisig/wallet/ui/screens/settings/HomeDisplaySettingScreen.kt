package com.vultisig.wallet.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.HomeBalanceDisplay
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.settings.HomeDisplaySettingViewModel
import com.vultisig.wallet.ui.models.settings.SettingsItemUiModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText

@Composable
internal fun HomeDisplaySettingScreen(navController: NavHostController) {
    val viewModel = hiltViewModel<HomeDisplaySettingViewModel>()
    val selected by viewModel.selected.collectAsState()

    HomeDisplaySettingScreen(
        selected = selected,
        onBackClick = { navController.popBackStack() },
        onSelect = viewModel::select,
    )
}

/** Picks what the home header's balance shows, for every vault. */
@Composable
internal fun HomeDisplaySettingScreen(
    selected: HomeBalanceDisplay?,
    onBackClick: () -> Unit,
    onSelect: (HomeBalanceDisplay) -> Unit,
) {
    V2Scaffold(title = stringResource(R.string.home_display), onBackClick = onBackClick) {
        Column {
            Text(
                text = stringResource(R.string.home_balance_display),
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.text.secondary,
            )

            UiSpacer(size = 6.dp)

            SettingsBorderedCard {
                Column(modifier = Modifier.padding(1.dp)) {
                    val options = HomeBalanceDisplay.entries
                    options.forEachIndexed { index, option ->
                        SettingItem(
                            item =
                                SettingsItemUiModel(
                                    title = UiText.StringResource(option.titleRes),
                                    subTitle = UiText.StringResource(option.descriptionRes),
                                    trailingIcon =
                                        if (option == selected) R.drawable.toggle_on
                                        else R.drawable.toggle_off,
                                ),
                            onClick = { onSelect(option) },
                            // This frame's divider adds to the card's height, unlike the row's own.
                            isLastItem = true,
                            isSelected = option == selected,
                            titleStyle = Theme.brockmann.body.m.medium,
                            layout = SettingItemLayout.Radio,
                        )
                        if (index != options.lastIndex) {
                            HorizontalDivider(
                                color = Theme.v2.colors.border.light,
                                thickness = 1.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val HomeBalanceDisplay.titleRes: Int
    get() =
        when (this) {
            HomeBalanceDisplay.Separate -> R.string.home_display_separate_title
            HomeBalanceDisplay.Combined -> R.string.home_display_combined_title
        }

private val HomeBalanceDisplay.descriptionRes: Int
    get() =
        when (this) {
            HomeBalanceDisplay.Separate -> R.string.home_display_separate_description
            HomeBalanceDisplay.Combined -> R.string.home_display_combined_description
        }

@Preview
@Composable
private fun HomeDisplaySettingScreenPreview() {
    HomeDisplaySettingScreen(
        selected = HomeBalanceDisplay.Separate,
        onBackClick = {},
        onSelect = {},
    )
}
