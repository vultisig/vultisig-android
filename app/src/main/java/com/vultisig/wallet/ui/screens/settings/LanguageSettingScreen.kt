package com.vultisig.wallet.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Language
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.settings.LanguageSettingUiModel
import com.vultisig.wallet.ui.models.settings.LanguageSettingViewModel
import com.vultisig.wallet.ui.models.settings.SettingsItemUiModel

@Composable
fun LanguageSettingScreen(navController: NavHostController) {
    val viewModel = hiltViewModel<LanguageSettingViewModel>()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(key1 = Unit) {
        viewModel.initSelectedLanguage()
    }

    LanguageSettingScreen(
        state = state,
        onBackClick = {
            navController.popBackStack()
        },
        onLanguageClick = { language ->
            viewModel.changeLanguage(language)
            navController.popBackStack()
        }
    )
}

@Composable
private fun LanguageSettingScreen(
    state: LanguageSettingUiModel,
    onBackClick: () -> Unit,
    onLanguageClick: (Language) -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        topBar = {
            VsTopAppBar(
                iconLeft = R.drawable.ic_caret_left,
                onIconLeftClick = onBackClick,
                title = stringResource(R.string.language_setting_screen_title),
            )
        }
    ) {

        SettingsBox(
            Modifier
                .padding(it)
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                )
        ) {
            LazyColumn {
                itemsIndexed(state.languages) { index, language ->
                    LanguageSettingItem(
                        name = language.mainName,
                        englishName = language.englishName,
                        isSelected = language == state.selectedLanguage,
                        isLastItem = index == state.languages.lastIndex,
                        onClick = {
                            onLanguageClick(language)
                        }
                    )
                }
            }
        }

    }
}

@Composable
private fun LanguageSettingItem(
    name: String,
    englishName: String?,
    isSelected: Boolean,
    isLastItem: Boolean = false,
    onClick: () -> Unit
) {

    SettingItem(
        item = SettingsItemUiModel(
            title = name,
            subTitle = englishName,
            leadingIcon = null,
            trailingIcon = if(isSelected)  R.drawable.check else null,
        ),
        onClick = onClick,
        isLastItem = isLastItem
    )
}

@Preview(showBackground = true)
@Composable
private fun LanguageSettingScreenPreview() {
    LanguageSettingScreen(
        state = LanguageSettingUiModel(
            languages = listOf(
                Language(
                    mainName = "English",
                    englishName = "English"
                ),
                Language(
                    mainName = "Français",
                    englishName = "French"
                )
            ),
            selectedLanguage = Language(
                mainName = "English",
                englishName = "English"
            )
        ),
        onBackClick = {},
        onLanguageClick = {}
    )
}