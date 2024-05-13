package com.vultisig.wallet.presenter.settings.faq_setting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun FAQSettingScreen(navController: NavHostController) {
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
                startIcon = R.drawable.caret_left
            )
        }
    ) {

        LazyColumn(
            modifier = Modifier
                .padding(it)
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            items(state.questions) { q ->
                FAQSettingItem(
                    question = q.text
                ) {

                }
            }
        }

    }
}


@Composable
private fun FAQSettingItem(question: String, onClick: () -> Unit = {}) {
    val colors = Theme.colors
    Card(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.oxfordBlue600Main
        )
    ) {
        Row(
            modifier = Modifier.padding(all = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Text(
                text = question,
                color = colors.neutral0,
                style = Theme.montserrat.body2,
                lineHeight = 25.sp,
                modifier = Modifier.weight(0.8f)
            )

            Icon(
                painter = painterResource(id = R.drawable.caret_right),
                contentDescription = null,
                tint = colors.neutral0,
            )
        }
    }
}