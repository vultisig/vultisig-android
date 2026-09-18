package com.vultisig.wallet.ui.components.v2.bottomsheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.buttons.DesignType
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.theme.Theme
import kotlinx.coroutines.launch

/**
 * The floating overview card: off the radius scale for the same reason as [V2SheetShape] — it is
 * the design kit's sheet frame, not an authored Vultisig surface.
 */
private val OverviewSheetShape = RoundedCornerShape(34.dp)

/** The gap between the card and the window edges, on all three sides that touch one. */
private val OverviewSheetInset = 16.dp

/** The diameter of the two round controls flanking the title. */
internal val OverviewSheetControlSize = 32.dp

/**
 * A transaction overview presented as a card floating over the form that produced it, so the
 * figures being confirmed stay visible, dimmed, behind the confirmation.
 *
 * The card wraps its content and grows with it, up to the status bar; past that [content] scrolls
 * while the header and [footer] stay put, which keeps the consent checkboxes and the sign buttons
 * reachable without scrolling to the end of a long decoded payload. Swiping the card down, tapping
 * the scrim, pressing back or the close control all end in [onDismissRequest].
 *
 * @param leadingControl the round control at the title's left, [OverviewSheetControlSize] across.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OverviewBottomSheet(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    leadingControl: @Composable () -> Unit = {},
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // The route that hosts this sheet is a dialog destination, and its window dims whatever is
    // under it on its own. The sheet then opens a second window above that and draws
    // [OverviewSheetScrim] there, so the two would stack and leave the form at a fifth of its
    // brightness instead of half. The host's dim is cleared here, during composition: the window
    // is shown from an effect, and an effect runs too late to stop it from being added dimmed.
    val view = LocalView.current
    remember(view) { (view.parent as? DialogWindowProvider)?.window?.apply { setDimAmount(0f) } }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier.statusBarsPadding(),
        containerColor = Color.Transparent,
        contentColor = Theme.v2.colors.text.primary,
        shape = RectangleShape,
        dragHandle = null,
        scrimColor = OverviewSheetScrim,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        start = OverviewSheetInset,
                        end = OverviewSheetInset,
                        bottom = OverviewSheetInset,
                    )
                    .clip(OverviewSheetShape)
                    .background(Theme.v2.colors.backgrounds.surface1)
                    .border(width = 1.dp, color = OverviewSheetBorder, shape = OverviewSheetShape)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        ) {
            DragHandler(color = Theme.v2.colors.vibrant.primary)

            UiSpacer(5.dp)

            OverviewSheetHeader(
                title = title,
                leadingControl = leadingControl,
                onClose = {
                    // Slide out first, then leave: the route behind this sheet is popped by
                    // [onDismissRequest], which would otherwise cut the card off mid-frame.
                    scope.launch {
                        sheetState.hide()
                        onDismissRequest()
                    }
                },
            )

            UiSpacer(28.dp)

            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                content = content,
            )

            if (footer != null) {
                UiSpacer(20.dp)
                footer()
            }
        }
    }
}

@Composable
private fun OverviewSheetHeader(
    title: String,
    onClose: () -> Unit,
    leadingControl: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.size(OverviewSheetControlSize)) { leadingControl() }

        Text(
            text = title,
            style = Theme.brockmann.headings.title3,
            color = Theme.v2.colors.text.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        VsCircleButton(
            drawableResId = R.drawable.x,
            tint = Theme.v2.colors.neutrals.n500,
            iconSize = 16.dp,
            size = VsCircleButtonSize.Custom(OverviewSheetControlSize),
            type = VsCircleButtonType.Tertiary,
            designType = DesignType.Solid,
            onClick = onClose,
        )
    }
}

private val OverviewSheetScrim = Color.Black.copy(alpha = 0.5f)
private val OverviewSheetBorder = Color.White.copy(alpha = 0.03f)

@Preview
@Composable
private fun OverviewBottomSheetPreview() {
    OverviewBottomSheet(
        title = stringResource(R.string.verify_send_send_overview),
        onDismissRequest = {},
        footer = {
            Text(
                text = "Footer",
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.body.s.medium,
            )
        },
    ) {
        Text(
            text = "Content",
            color = Theme.v2.colors.text.primary,
            style = Theme.brockmann.body.s.medium,
        )
    }
}
