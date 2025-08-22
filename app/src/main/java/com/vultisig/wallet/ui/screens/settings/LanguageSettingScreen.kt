package com.vultisig.wallet.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Language
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.settings.LanguageSettingUiModel
import com.vultisig.wallet.ui.models.settings.LanguageSettingViewModel
import com.vultisig.wallet.ui.screens.send.FadingHorizontalDivider
import com.vultisig.wallet.ui.theme.Theme

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
    Column {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .clickOnce(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = name,
                    color = Theme.colors.text.primary,
                    style = Theme.brockmann.headings.subtitle,
                )

                englishName?.let {
                    Text(
                        text = it,
                        color = Theme.colors.text.light,
                        style = Theme.brockmann.supplementary.caption,
                    )
                }
            }

            UiSpacer(weight = 1.0f)

            if (isSelected)
                Icon(
                    modifier = Modifier
                        .size(16.dp)
                        .background(
                            color = Theme.colors.primary.accent3,
                            shape = CircleShape
                        )
                        .padding(4.dp),
                    painter = painterResource(id = R.drawable.check),
                    contentDescription = "check mark",
                    tint = Theme.colors.text.button.light,
                )
        }

        if (isLastItem.not()) {
            FadingHorizontalDivider()
        }
    }
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