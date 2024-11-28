package com.vultisig.wallet.ui.screens.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.library.form.FormCard
import com.vultisig.wallet.ui.models.settings.FAQSettingViewModel
import com.vultisig.wallet.ui.models.settings.Faq
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun FAQSettingScreen(navController: NavHostController) {
    val colors = Theme.colors
    val viewModel = hiltViewModel<FAQSettingViewModel>()
    val state by viewModel.state.collectAsState()
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.oxfordBlue800),
        topBar = {
            TopBar(
                navController = navController,
                centerText = stringResource(R.string.faq_setting_screen_title),
                startIcon = R.drawable.ic_caret_left
            )
        }
    ) {

        LazyColumn(
            modifier = Modifier
                .padding(it)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.questions) { faq -> FAQSettingItem(faq = faq) }
        }

    }
}


@Composable
private fun FAQSettingItem(faq: Faq) {
    val colors = Theme.colors
    var isExpanded by remember {
        mutableStateOf(false)
    }
    val rotation = animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f, label = "arrow angle"
    )
    FormCard(modifier = Modifier.animateContentSize()) {
        Row(
            modifier = Modifier
                .padding(all = 12.dp)
                .clickable {
                    isExpanded = !isExpanded
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Text(
                text = faq.question,
                color = colors.neutral0,
                style = Theme.montserrat.body2,
                lineHeight = 25.sp,
                modifier = Modifier.weight(0.8f)
            )

            Icon(
                modifier = Modifier.rotate(rotation.value),
                painter = painterResource(id = R.drawable.ic_small_caret_right),
                contentDescription = null,
                tint = colors.neutral0,
            )
        }
        if (isExpanded)
            Row(
                modifier = Modifier
                    .padding(all = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = faq.answer,
                    color = colors.neutral0,
                    style = Theme.montserrat.body2,
                    lineHeight = 25.sp,
                    modifier = Modifier.weight(0.8f)
                )
            }
    }
}