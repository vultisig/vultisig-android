package com.vultisig.wallet.ui.screens.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.settings.FAQSettingUiModel
import com.vultisig.wallet.ui.models.settings.FAQSettingViewModel
import com.vultisig.wallet.ui.models.settings.Faq
import com.vultisig.wallet.ui.screens.send.FadingHorizontalDivider
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun FaqSettingScreen(navController: NavHostController) {
    val viewModel = hiltViewModel<FAQSettingViewModel>()
    val state by viewModel.state.collectAsState()
    FaqSettingScreen(onBackClick = {
        navController.popBackStack()
    }, state)
}

@Composable
private fun FaqSettingScreen(
    onBackClick: () -> Unit,
    state: FAQSettingUiModel
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        topBar = {
            VsTopAppBar(
                onIconLeftClick = onBackClick,
                title = stringResource(R.string.faq_setting_screen_title),
                iconLeft = R.drawable.ic_caret_left
            )
        }
    ) {

        SettingsBox(
            modifier = Modifier
                .padding(it)
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                )
        ) {
            LazyColumn(
                contentPadding = PaddingValues(
                    horizontal = 20.dp,
                    vertical = 16.dp
                )
            ) {
                itemsIndexed(state.questions) { index, faq ->
                    FAQSettingItem(
                        faq = faq,
                        isLastItem = index == state.questions.lastIndex
                    )
                }
            }
        }


    }
}


@Composable
private fun FAQSettingItem(faq: Faq, isLastItem: Boolean) {
    val colors = Theme.colors
    var isExpanded by remember {
        mutableStateOf(false)
    }
    val rotation = animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f, label = "arrow angle"
    )
    Column(
        modifier = Modifier
            .animateContentSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    isExpanded = !isExpanded
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Text(
                text = faq.question,
                color = Theme.colors.text.light,
                style = Theme.brockmann.body.s.medium,
                lineHeight = 20.sp,
                modifier = Modifier.weight(1f)
            )

            UiSpacer(16.dp)

            Icon(
                modifier = Modifier.rotate(rotation.value),
                painter = painterResource(id = R.drawable.ic_small_caret_right),
                contentDescription = null,
                tint = colors.text.extraLight,
            )
        }

        if (isExpanded) {
            UiSpacer(4.dp)
            Text(
                text = faq.answer,
                color = colors.neutral0,
                style = Theme.brockmann.supplementary.footnote,
                lineHeight = 18.sp,
            )
        }

        if (isLastItem.not()) {
            FadingHorizontalDivider(Modifier.padding(vertical = 16.dp))
        }

    }
}

@Preview
@Composable
private fun FaqSettingScreenPreview() {
    FaqSettingScreen(
        onBackClick = {}, state = FAQSettingUiModel(
            questions = listOf(
                Faq(
                    question = "What is Vultisig?",
                    answer = "It is a secure, multi-authentication wallet based on MPC technology that is used to manage digital assets. Transactions require approval from multiple devices."
                ),
                Faq(
                    question = "What are the benefits of using Vultisig?",
                    answer = "Vultisig offers enhanced security with multi-device authentication, support for many blockchains, easy recovery options, and no seed phrases or user tracking."
                ),
                Faq(
                    question = "Can I recover my assets if I lose a device?",
                    answer = "Yes, as long as you saved and have access to your backups when creating the vault. You can import these backups on a new device to regain access to your assets."
                )
            )
        )
    )
}