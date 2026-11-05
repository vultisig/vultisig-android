package com.vultisig.wallet.ui.screens.settings.bottomsheets.sharelink

import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.rememberAsyncImagePainter
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.CopyIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.bottomsheet.VsModalBottomSheet
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsAuxiliaryLinks

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareLinkBottomSheet(onDismissRequest: () -> Unit) {
    val viewModel = hiltViewModel<ShareLinkViewModel>()
    val context = LocalContext.current
    val uiModel = remember(context) { viewModel.getUiModel(context) }

    VsModalBottomSheet(
        onDismissRequest = onDismissRequest,
        dragHandle = { ShareSheetGrabber() },
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        scrimColor = Theme.v2.colors.neutrals.n900.copy(alpha = 0.5f),
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
private fun ShareSheetGrabber() {
    Box(
        modifier =
            Modifier.padding(top = 8.dp, bottom = 13.dp)
                .size(width = 36.dp, height = 5.dp)
                .background(
                    color = Theme.v2.colors.neutrals.n50.copy(alpha = 0.3f),
                    shape = Theme.v2.radius.pill,
                )
    )
}

@Composable
private fun ShareLinkContent(
    uiModel: ShareLinkUiModel,
    onShareClick: (ShareOptionUiModel) -> Unit,
) {
    val colors = Theme.v2.colors
    val linkFieldShape = Theme.v2.radius.lg

    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 25.dp)) {
        Text(
            text = stringResource(R.string.app_name),
            style = Theme.brockmann.headings.title3,
            color = colors.text.primary,
        )

        UiSpacer(size = SECTION_GAP - 1.dp)

        HorizontalDivider(color = colors.border.light, thickness = 1.dp)

        UiSpacer(size = SECTION_GAP)

        Text(
            text = stringResource(R.string.settings_screen_share_the_app),
            color = colors.text.tertiary,
            style = Theme.brockmann.supplementary.footnote,
        )

        UiSpacer(size = SHARE_OPTION_GAP)

        FlowRow(
            verticalArrangement = Arrangement.spacedBy(SHARE_OPTION_GAP),
            horizontalArrangement = Arrangement.spacedBy(SHARE_OPTION_GAP),
        ) {
            uiModel.shareOptions.forEach { shareOption ->
                ShareOptionItem(shareOption = shareOption, onClick = { onShareClick(shareOption) })
            }
        }

        UiSpacer(size = SECTION_GAP)

        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .background(color = colors.variables.inputFieldsBackground, linkFieldShape)
                    .border(1.dp, colors.variables.bordersExtraLight, linkFieldShape)
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = uiModel.link,
                style = Theme.brockmann.body.s.medium,
                color = colors.text.secondary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            CopyIcon(
                textToCopy = uiModel.link,
                icon = R.drawable.copy2,
                tint = Color.Unspecified,
                size = 20.dp,
            )
        }
    }
}

/** The tap target stays 48dp while the icon and the row's spacing follow the 38dp design. */
@Composable
private fun ShareOptionItem(shareOption: ShareOptionUiModel, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier.size(SHARE_OPTION_SIZE)
                .wrapContentSize(unbounded = true)
                .size(SHARE_OPTION_TOUCH_SIZE)
                .clip(shape = CircleShape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = rememberAsyncImagePainter(model = shareOption.icon),
            contentDescription = shareOption.label,
            modifier = Modifier.size(SHARE_OPTION_SIZE),
        )
    }
}

private val SECTION_GAP = 24.dp
private val SHARE_OPTION_GAP = 14.dp
private val SHARE_OPTION_SIZE = 38.dp
private val SHARE_OPTION_TOUCH_SIZE = 48.dp

@Preview
@Composable
private fun ShareLinkContentPreview() {
    ShareLinkContent(
        uiModel =
            ShareLinkUiModel(
                link = VsAuxiliaryLinks.GOOGLE_PLAY,
                shareOptions =
                    listOf(
                        ShareOptionUiModel(
                            packageName = "",
                            label = "More",
                            icon =
                                AppCompatResources.getDrawable(
                                    LocalContext.current,
                                    R.drawable.plus,
                                )!!,
                        ),
                        ShareOptionUiModel(
                            packageName = "",
                            label = "chrome",
                            icon =
                                AppCompatResources.getDrawable(
                                    LocalContext.current,
                                    R.drawable.plus,
                                )!!,
                            isSpecial = true,
                        ),
                        ShareOptionUiModel(
                            packageName = "",
                            label = "chrome",
                            icon =
                                AppCompatResources.getDrawable(
                                    LocalContext.current,
                                    R.drawable.plus,
                                )!!,
                            isSpecial = true,
                        ),
                        ShareOptionUiModel(
                            packageName = "",
                            label = "chrome",
                            icon =
                                AppCompatResources.getDrawable(
                                    LocalContext.current,
                                    R.drawable.plus,
                                )!!,
                            isSpecial = true,
                        ),
                        ShareOptionUiModel(
                            packageName = "",
                            label = "chrome",
                            icon =
                                AppCompatResources.getDrawable(
                                    LocalContext.current,
                                    R.drawable.plus,
                                )!!,
                            isSpecial = true,
                        ),
                        ShareOptionUiModel(
                            packageName = "",
                            label = "chrome",
                            icon =
                                AppCompatResources.getDrawable(
                                    LocalContext.current,
                                    R.drawable.plus,
                                )!!,
                            isSpecial = true,
                        ),
                        ShareOptionUiModel(
                            packageName = "",
                            label = "chrome",
                            icon =
                                AppCompatResources.getDrawable(
                                    LocalContext.current,
                                    R.drawable.plus,
                                )!!,
                            isSpecial = true,
                        ),
                    ),
            ),
        onShareClick = {},
    )
}
