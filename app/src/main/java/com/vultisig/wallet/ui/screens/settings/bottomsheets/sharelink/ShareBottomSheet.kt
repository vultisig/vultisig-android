package com.vultisig.wallet.ui.screens.settings.bottomsheets.sharelink

import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.CopyIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.bottomsheet.VsModalBottomSheet
import com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.screens.send.FadingHorizontalDivider
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsAuxiliaryLinks

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareLinkBottomSheet(
    onDismissRequest: () -> Unit,
) {
    val viewModel = hiltViewModel<ShareLinkViewModel>()
    val context = LocalContext.current
    val uiModel = remember(context) { viewModel.getUiModel(context) }

    VsModalBottomSheet(
        onDismissRequest = onDismissRequest
    ) {
        ShareLinkContent(
            uiModel = uiModel,
            onShareClick = { shareOption ->
                viewModel.onShareClick(shareOption, context)
                onDismissRequest()
            },
        )
    }
}

@Composable
private fun ShareLinkContent(
    uiModel: ShareLinkUiModel,
    onShareClick: (ShareOptionUiModel) -> Unit,
) {

    Column(
        modifier = Modifier
            .padding(8.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = Theme.brockmann.headings.subtitle,
            color = Theme.colors.text.primary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        FadingHorizontalDivider(
            modifier = Modifier.padding(
                vertical = 24.dp
            )
        )

        Text(
            text = stringResource(R.string.settings_screen_share_the_app),
            color = Theme.colors.text.extraLight,
            style = Theme.brockmann.supplementary.footnote,
            modifier = Modifier
                .padding(
                    horizontal = 20.dp
                )
        )

        UiSpacer(
            size = 14.dp,
        )


        FlowRow(
            modifier = Modifier
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            uiModel.shareOptions.forEach { shareOption ->
                ShareOptionItem(
                    shareOption = shareOption,
                    onClick = { onShareClick(shareOption) }
                )
            }
        }


        UiSpacer(
            size = 24.dp,
        )

        V2Container(
            modifier = Modifier.padding(horizontal = 20.dp),
            type = ContainerType.SECONDARY,
            borderType = ContainerBorderType.Bordered()
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiModel.link,
                    style = Theme.brockmann.body.m.regular,
                    color = Theme.colors.text.light,
                    modifier = Modifier
                        .weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                UiSpacer(
                    size = 40.dp
                )

                CopyIcon(
                    textToCopy = uiModel.link,
                    tint = Theme.colors.primary.accent4,
                    size = 16.dp,
                )

            }
        }


        UiSpacer(
            size = 24.dp,
        )
    }

}

@Composable
private fun ShareOptionItem(
    shareOption: ShareOptionUiModel,
    onClick: () -> Unit,
) {

    Box(
        modifier = Modifier
            .size(
                size = 54.dp
            )
            .clip(
                shape = CircleShape
            )
            .clickable(
                onClick = onClick
            )
            .background(
                Theme.colors.backgrounds.secondary
            ),
        contentAlignment = Alignment.Center,
    ) {

        Image(
            painter = rememberAsyncImagePainter(model = shareOption.icon),
            contentDescription = shareOption.label,
            modifier = Modifier
                .size(38.dp),
        )
    }

}


@Preview
@Composable
private fun ShareLinkContentPreview() {
    ShareLinkContent(
        uiModel = ShareLinkUiModel(
            link = VsAuxiliaryLinks.GOOGLE_PLAY,
            shareOptions = listOf(
                ShareOptionUiModel(
                    packageName = "",
                    label = "More",
                    icon = AppCompatResources.getDrawable(
                        LocalContext.current,
                        R.drawable.plus
                    )!!,
                ),
                ShareOptionUiModel(
                    packageName = "",
                    label = "chrome",
                    icon = AppCompatResources.getDrawable(
                        LocalContext.current,
                        R.drawable.plus
                    )!!,
                    isSpecial = true
                ),
                ShareOptionUiModel(
                    packageName = "",
                    label = "chrome",
                    icon = AppCompatResources.getDrawable(
                        LocalContext.current,
                        R.drawable.plus
                    )!!,
                    isSpecial = true
                ),
                ShareOptionUiModel(
                    packageName = "",
                    label = "chrome",
                    icon = AppCompatResources.getDrawable(
                        LocalContext.current,
                        R.drawable.plus
                    )!!,
                    isSpecial = true
                ),
                ShareOptionUiModel(
                    packageName = "",
                    label = "chrome",
                    icon = AppCompatResources.getDrawable(
                        LocalContext.current,
                        R.drawable.plus
                    )!!,
                    isSpecial = true
                ),
                ShareOptionUiModel(
                    packageName = "",
                    label = "chrome",
                    icon = AppCompatResources.getDrawable(
                        LocalContext.current,
                        R.drawable.plus
                    )!!,
                    isSpecial = true
                ),
                ShareOptionUiModel(
                    packageName = "",
                    label = "chrome",
                    icon = AppCompatResources.getDrawable(
                        LocalContext.current,
                        R.drawable.plus
                    )!!,
                    isSpecial = true
                )
            )
        ),
        onShareClick = {}
    )
}
