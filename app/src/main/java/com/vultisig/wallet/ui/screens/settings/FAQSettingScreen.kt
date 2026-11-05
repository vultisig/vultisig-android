package com.vultisig.wallet.ui.screens.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.settings.FAQSettingUiModel
import com.vultisig.wallet.ui.models.settings.FAQSettingViewModel
import com.vultisig.wallet.ui.models.settings.Faq
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun FaqSettingScreen(navController: NavHostController) {
    val viewModel = hiltViewModel<FAQSettingViewModel>()
    val state by viewModel.state.collectAsState()
    FaqSettingScreen(onBackClick = { navController.popBackStack() }, state = state)
}

@Composable
private fun FaqSettingScreen(onBackClick: () -> Unit, state: FAQSettingUiModel) {

    V2Scaffold(
        title = stringResource(R.string.faq_setting_screen_title),
        onBackClick = onBackClick,
    ) {
        V2Container(type = ContainerType.SECONDARY) {
            LazyColumn {
                itemsIndexed(state.questions, key = { _, faq -> faq.id }) { index, faq ->
                    FAQSettingItem(faq = faq, isLastItem = index == state.questions.lastIndex)
                }
            }
        }
    }
}

@Composable
private fun FAQSettingItem(faq: Faq, isLastItem: Boolean) {
    val colors = Theme.v2.colors
    var isExpanded by remember { mutableStateOf(false) }
    val rotation =
        animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "arrow angle")
    Column(
        modifier =
            Modifier.animateContentSize()
                .then(if (isLastItem) Modifier else Modifier.rowBottomDivider(colors.border.light))
                .fillMaxWidth()
                .heightIn(min = COLLAPSED_ROW_HEIGHT)
                .padding(
                    horizontal = 20.dp,
                    vertical = if (isExpanded) EXPANDED_ROW_PADDING else COLLAPSED_ROW_PADDING,
                ),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CHEVRON_GAP),
        ) {
            Text(
                text = faq.question,
                color = colors.text.secondary,
                style = Theme.brockmann.body.s.medium,
                modifier = Modifier.weight(1f),
            )

            Icon(
                modifier = Modifier.size(CHEVRON_SIZE).rotate(rotation.value),
                painter = painterResource(id = R.drawable.icon_chevron_down_small),
                contentDescription = null,
                tint = colors.text.tertiary,
            )
        }

        if (isExpanded) {
            UiSpacer(12.dp)
            Text(
                text = faq.answer,
                color = colors.neutrals.n50,
                style = Theme.brockmann.supplementary.footnote,
                modifier = Modifier.padding(end = CHEVRON_GAP + CHEVRON_SIZE),
            )
        }
    }
}

private val CHEVRON_GAP = 12.dp
private val CHEVRON_SIZE = 16.dp
private val COLLAPSED_ROW_HEIGHT = 70.dp
private val COLLAPSED_ROW_PADDING = 15.dp
private val EXPANDED_ROW_PADDING = 16.dp

@Preview
@Composable
private fun FaqSettingScreenPreview() {
    FaqSettingScreen(
        onBackClick = {},
        state =
            FAQSettingUiModel(
                questions =
                    listOf(
                        Faq(
                            id = 1,
                            question = "What is Vultisig?",
                            answer =
                                "It is a secure, multi-authentication wallet based on MPC technology that is used to manage digital assets. Transactions require approval from multiple devices.",
                        ),
                        Faq(
                            id = 2,
                            question = "What are the benefits of using Vultisig?",
                            answer =
                                "Vultisig offers enhanced security with multi-device authentication, support for many blockchains, easy recovery options, and no seed phrases or user tracking.",
                        ),
                        Faq(
                            id = 3,
                            question = "Can I recover my assets if I lose a device?",
                            answer =
                                "Yes, as long as you saved and have access to your backups when creating the vault. You can import these backups on a new device to regain access to your assets.",
                        ),
                    )
            ),
    )
}
