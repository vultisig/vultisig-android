package com.vultisig.wallet.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.HomeBalanceDisplay
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.settings.HomeDisplaySettingViewModel
import com.vultisig.wallet.ui.models.settings.SettingsItemUiModel
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
        SettingsBox(title = stringResource(R.string.home_balance_display)) {
            val options = HomeBalanceDisplay.entries
            options.forEachIndexed { index, option ->
                SettingItem(
                    item =
                        SettingsItemUiModel(
                            title = UiText.StringResource(option.titleRes),
                            subTitle = UiText.StringResource(option.descriptionRes),
                            trailingIcon = R.drawable.check_2,
                            isTrailingIconVisible = option == selected,
                        ),
                    onClick = { onSelect(option) },
                    isLastItem = index == options.lastIndex,
                    isSelected = option == selected,
                )
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
