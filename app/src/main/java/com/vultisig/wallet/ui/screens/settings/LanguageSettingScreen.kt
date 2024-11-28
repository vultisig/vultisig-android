package com.vultisig.wallet.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.models.settings.LanguageSettingViewModel
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun LanguageSettingScreen(navController: NavHostController) {
    val colors = Theme.colors
    val viewModel = hiltViewModel<LanguageSettingViewModel>()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(key1 = Unit) {
        viewModel.initSelectedLanguage()
    }


    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.oxfordBlue800),
        topBar = {
            TopBar(
                navController = navController,
                centerText = stringResource(R.string.language_setting_screen_title),
                startIcon = R.drawable.ic_caret_left
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .padding(it)
        ) {
            items(state.languages) { language ->
                LanguageSettingItem(
                    name = language.mainName,
                    englishName = language.englishName,
                    isSelected = language == state.selectedLanguage,
                    onClick = {
                        viewModel.changeLanguage(language)
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}

@Composable
private fun LanguageSettingItem(
    name: String,
    englishName: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = Theme.colors
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clickOnce(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.oxfordBlue600Main
        )
    ) {
        Row(
            modifier = Modifier.padding(all = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = name,
                    color = colors.neutral0,
                    style = Theme.menlo.body2,
                )


                englishName?.let {
                    Text(
                        text = it,
                        color = colors.neutral300,
                        style = Theme.menlo.body3,
                    )
                }
            }

            UiSpacer(weight = 1.0f)

            Icon(
                modifier = Modifier.alpha(if (isSelected) 1f else 0f),
                painter = painterResource(id = R.drawable.check),
                contentDescription = null,
                tint = colors.neutral0,
            )
        }
    }
}
@Preview(showBackground = true)
@Composable
fun LanguageSettingScreenPreview() {
    LanguageSettingItem(
        name = "English",
        englishName = "English",
        isSelected = true,
        onClick = {})
}