package com.vultisig.wallet.ui.screens.reshare

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.bottomsheet.VsModalBottomSheet
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.reshare.ReshareStartViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ReshareStartScreen(model: ReshareStartViewModel = hiltViewModel()) {
    val bottomSheetAction by model.bottomSheetAction.collectAsStateWithLifecycle()

    ReshareStartScreen(
        onBackClick = model::back,
        onStartClick = model::start,
        onJoinClick = model::join,
    )

    if (bottomSheetAction != null) {
        BeforeYouReshareBottomSheet(onDismiss = model::dismissSheet, onConfirm = model::onConfirm)
    }
}

@Composable
private fun ReshareStartScreen(
    onBackClick: () -> Unit,
    onStartClick: () -> Unit,
    onJoinClick: () -> Unit,
) {
    V2Scaffold(onBackClick = onBackClick) {
        Column(modifier = Modifier.fillMaxSize()) {
            UiSpacer(size = 8.dp)

            Text(
                text = stringResource(id = R.string.reshare_start_screen_title),
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.headings.title1,
            )

            UiSpacer(size = 12.dp)

            Text(
                text = stringResource(id = R.string.reshare_start_screen_subtitle),
                color = Theme.v2.colors.text.secondary,
                style = Theme.brockmann.body.s.medium,
            )

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_reshare_devices),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            ReshareOptionCard(
                title = stringResource(id = R.string.reshare_start_screen_start_reshare),
                subtitle = stringResource(id = R.string.reshare_start_start_subtitle),
                onClick = onStartClick,
            )

            UiSpacer(size = 10.dp)

            ReshareOptionCard(
                title = stringResource(id = R.string.reshare_start_join_reshare_button),
                subtitle = stringResource(id = R.string.reshare_start_join_subtitle),
                onClick = onJoinClick,
            )

            UiSpacer(size = 16.dp)
        }
    }
}

@Composable
private fun ReshareOptionCard(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Theme.v2.colors.backgrounds.secondary)
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.light,
                    shape = RoundedCornerShape(16.dp),
                )
                .clickOnce(onClick = onClick)
                .padding(all = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.body.m.medium,
            )
            UiSpacer(size = 12.dp)
            Text(
                text = subtitle,
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.body.s.medium,
            )
        }

        UiSpacer(size = 16.dp)

        UiIcon(
            drawableResId = R.drawable.ic_chevron_right_small,
            size = 20.dp,
            tint = Theme.v2.colors.text.tertiary,
        )
    }
}

@Composable
private fun BeforeYouReshareBottomSheet(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    VsModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)
        ) {
            UiSpacer(size = 8.dp)

            Text(
                text = stringResource(id = R.string.before_reshare_title),
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.body.m.medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            UiSpacer(size = 8.dp)

            Text(
                text = stringResource(id = R.string.before_reshare_subtitle),
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.body.s.medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            UiSpacer(size = 24.dp)

            ReshareWarningCard(
                icon = R.drawable.ic_traffic_cone,
                title = stringResource(id = R.string.before_reshare_backups_title),
                body = stringResource(id = R.string.before_reshare_backups_body),
            )

            UiSpacer(size = 10.dp)

            ReshareWarningCard(
                icon = R.drawable.ic_circles_5,
                title = stringResource(id = R.string.before_reshare_cosigners_title),
                body = stringResource(id = R.string.before_reshare_cosigners_body),
            )

            UiSpacer(size = 24.dp)

            VsButton(
                label = stringResource(id = R.string.vault_setup_i_understand),
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ReshareWarningCard(icon: Int, title: String, body: String) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Theme.v2.colors.backgrounds.secondary)
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.light,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(all = 20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        UiIcon(drawableResId = icon, size = 24.dp, tint = Theme.v2.colors.alerts.warning)

        UiSpacer(size = 12.dp)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.headings.subtitle,
            )
            UiSpacer(size = 8.dp)
            Text(
                text = body,
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.supplementary.footnote,
            )
        }
    }
}

@Preview
@Composable
private fun PreviewReshareScreen() {
    ReshareStartScreen(onBackClick = {}, onStartClick = {}, onJoinClick = {})
}
