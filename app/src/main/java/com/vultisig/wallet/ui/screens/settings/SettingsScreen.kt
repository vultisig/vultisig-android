package com.vultisig.wallet.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.AppVersionText
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VsSwitch
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.settings.SettingsItem
import com.vultisig.wallet.ui.models.settings.SettingsItemUiModel
import com.vultisig.wallet.ui.models.settings.SettingsUiEvent
import com.vultisig.wallet.ui.models.settings.SettingsUiModel
import com.vultisig.wallet.ui.models.settings.SettingsViewModel
import com.vultisig.wallet.ui.models.settings.defaultSettingsMenu
import com.vultisig.wallet.ui.screens.settings.bottomsheets.sharelink.ShareLinkBottomSheet
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsUriHandler
import com.vultisig.wallet.ui.utils.asString
import com.vultisig.wallet.ui.utils.asUiText

@Composable
fun SettingsScreen() {
    val viewModel = hiltViewModel<SettingsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = VsUriHandler()

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect {
            when (it) {
                is SettingsUiEvent.OpenLink -> uriHandler.openUri(it.url)
            }
        }
    }

    SettingsScreen(
        state = state,
        onSettingsItemClick = viewModel::onSettingsItemClick,
        onBackClick = viewModel::back,
        onDismissShareLink = viewModel::onDismissShareLinkBottomSheet,
        onVersionClick = viewModel::onVersionClick,
    )
}

@Composable
private fun SettingsScreen(
    state: SettingsUiModel,
    onSettingsItemClick: (SettingsItem) -> Unit,
    onBackClick: () -> Unit,
    onDismissShareLink: () -> Unit,
    onVersionClick: () -> Unit,
) {
    V2Scaffold(title = stringResource(R.string.settings_screen_title), onBackClick = onBackClick) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(SECTION_GAP)) {
                state.items.forEach { group ->
                    SettingsBox(
                        title = group.title.asString(),
                        layout = SettingItemLayout.List,
                        aboveCard =
                            if (group.tiles.isEmpty()) null
                            else {
                                {
                                    SettingsTiles(
                                        tiles = group.tiles,
                                        onClick = onSettingsItemClick,
                                    )
                                }
                            },
                    ) {
                        val enabledSettings = group.items.filter(SettingsItem::enabled)
                        enabledSettings.forEachIndexed { index, settingItem ->
                            SettingItem(
                                item = settingItem.value,
                                isLastItem = index == enabledSettings.lastIndex,
                                onClick = { onSettingsItemClick(settingItem) },
                                titleStyle = Theme.brockmann.body.s.medium,
                                layout = SettingItemLayout.List,
                            )
                        }
                    }
                }
            }

            UiSpacer(size = VERSION_FOOTER_TOP_GAP)

            AppVersionText(
                modifier = Modifier.clickable(onClick = onVersionClick),
                style = Theme.brockmann.supplementary.caption,
            )
        }

        if (state.showShareBottomSheet) {
            ShareLinkBottomSheet(onDismissRequest = onDismissShareLink)
        }
    }
}

/** Equal-width link tiles (icon over a short label) shown above a section's card. */
@Composable
private fun SettingsTiles(tiles: List<SettingsItem>, onClick: (SettingsItem) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
    ) {
        tiles.forEach { tile ->
            Column(
                modifier =
                    Modifier.weight(1f)
                        .height(TILE_HEIGHT)
                        .clip(Theme.v2.radius.lg)
                        .background(Theme.v2.colors.backgrounds.surface1)
                        .clickOnce(onClick = { onClick(tile) })
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TILE_GAP, Alignment.CenterVertically),
            ) {
                tile.value.leadingIcon?.let { icon ->
                    UiIcon(
                        drawableResId = icon,
                        size = 20.dp,
                        tint = Theme.v2.colors.text.secondary,
                    )
                }
                Text(
                    text = tile.value.title.asString(),
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Spacing of a [SettingItem]: [Legacy] outside the redesign, the others inside it. */
internal enum class SettingItemLayout(
    val iconGap: Dp,
    val subtitleGap: Dp,
    val switchEndGap: Dp,
    val cardTopPadding: Dp,
    val horizontalPadding: Dp = 12.dp,
    val verticalPadding: Dp = 16.dp,
    val minHeight: Dp = Dp.Unspecified,
    val trailingGap: Dp = 12.dp,
    val trailingIconSize: Dp = 16.dp,
) {
    Legacy(iconGap = 16.dp, subtitleGap = 0.dp, switchEndGap = 8.dp, cardTopPadding = 0.dp),
    List(iconGap = 12.dp, subtitleGap = 4.dp, switchEndGap = 0.dp, cardTopPadding = 1.dp),
    Choice(
        iconGap = 12.dp,
        subtitleGap = 4.dp,
        switchEndGap = 0.dp,
        cardTopPadding = 0.dp,
        horizontalPadding = 20.dp,
        verticalPadding = 14.dp,
        minHeight = 65.dp,
    ),
    Radio(
        iconGap = 12.dp,
        subtitleGap = 4.dp,
        switchEndGap = 0.dp,
        cardTopPadding = 0.dp,
        horizontalPadding = 14.dp,
        verticalPadding = 14.dp,
        trailingGap = 14.dp,
        trailingIconSize = 20.dp,
    ),
}

@Composable
internal fun SettingsBox(
    modifier: Modifier = Modifier,
    title: String? = null,
    layout: SettingItemLayout = SettingItemLayout.Legacy,
    /** Shown between the title and the card, e.g. a row of link tiles. */
    aboveCard: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = if (layout == SettingItemLayout.List) Theme.v2.radius.xl else Theme.v2.radius.md
    Column(modifier.fillMaxWidth()) {
        title?.let {
            Text(
                text = it,
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.supplementary.caption,
            )
            UiSpacer(size = 12.dp)
        }

        aboveCard?.let {
            it()
            UiSpacer(size = ABOVE_CARD_GAP)
        }

        Column(
            modifier =
                // Clipped before the top padding so that strip stays the screen colour, as drawn.
                Modifier.fillMaxWidth()
                    .clip(shape)
                    .padding(top = layout.cardTopPadding)
                    .background(color = Theme.v2.colors.backgrounds.secondary)
        ) {
            content()
        }
    }
}

/** The sub-screens' card: a surface with a hairline border. */
@Composable
internal fun SettingsBorderedCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = Theme.v2.colors
    val shape = Theme.v2.radius.xl
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(shape)
                .background(colors.backgrounds.surface1)
                .border(width = 1.dp, color = colors.border.light, shape = shape),
        content = content,
    )
}

/** Drawn inside the bottom edge so a divider doesn't add to the row's height. */
internal fun Modifier.rowBottomDivider(color: Color): Modifier = drawBehind {
    val thickness = DividerDefaults.Thickness.toPx()
    drawRect(
        color = color,
        topLeft = Offset(0f, size.height - thickness),
        size = Size(size.width, thickness),
    )
}

@Composable
internal fun SettingItem(
    item: SettingsItemUiModel,
    onClick: () -> Unit,
    isLastItem: Boolean,
    tint: Color? = null,
    /** Set on single-choice rows, whose checkmark then only mirrors this state. */
    isSelected: Boolean? = null,
    titleStyle: TextStyle = Theme.brockmann.headings.subtitle,
    layout: SettingItemLayout = SettingItemLayout.Legacy,
) {
    val isList = layout != SettingItemLayout.Legacy
    val dividerColor = Theme.v2.colors.border.light
    Column {
        Row(
            modifier =
                Modifier.then(
                        if (item.backgroundColor != null) Modifier.background(item.backgroundColor)
                        else Modifier
                    )
                    .then(
                        if (isList && !isLastItem) Modifier.rowBottomDivider(dividerColor)
                        else Modifier
                    )
                    .fillMaxWidth()
                    .heightIn(min = layout.minHeight)
                    .then(
                        if (isSelected != null) Modifier.semantics { selected = isSelected }
                        else Modifier
                    )
                    .then(
                        item.trailingSwitch?.let { isChecked ->
                            Modifier.semantics {
                                role = Role.Switch
                                toggleableState = ToggleableState(isChecked)
                            }
                        } ?: Modifier
                    )
                    .clickOnce(onClick = onClick)
                    .padding(
                        horizontal = layout.horizontalPadding,
                        vertical = layout.verticalPadding,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item.leadingIcon?.let { icon ->
                UiIcon(
                    drawableResId = icon,
                    size = 20.dp,
                    tint =
                        tint
                            ?: item.leadingIconTint
                            ?: if (isList) Theme.v2.colors.text.secondary
                            else Theme.v2.colors.primary.accent4,
                )
                UiSpacer(size = layout.iconGap)
            }

            // Weighted so the text takes what is left after the trailing switch or value, and wraps
            // inside it. Without this the column claims its full intrinsic width and pushes the
            // trailing content off the right edge — invisible until a row carries a long subtitle.
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(layout.subtitleGap),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = item.title.asString(),
                        style = titleStyle,
                        color = tint ?: Theme.v2.colors.text.primary,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    item.titleBadge?.let { badge -> SettingItemBadge(text = badge.asString()) }
                }

                item.subTitle?.let {
                    Text(
                        text = it.asString(),
                        color =
                            tint?.copy(alpha = TINTED_SUBTITLE_ALPHA)
                                ?: Theme.v2.colors.text.secondary,
                        style = Theme.brockmann.supplementary.caption,
                    )
                }
            }

            UiSpacer(size = layout.trailingGap)

            item.trailingSwitch?.let { isChecked ->
                if (isList) {
                    SettingsSwitch(
                        checked = isChecked,
                        // Overhangs the 20dp text line so a toggle row is as tall as a plain one.
                        modifier =
                            Modifier.height(SWITCH_LINE_HEIGHT)
                                .wrapContentHeight(unbounded = true),
                    )
                } else {
                    VsSwitch(checked = isChecked, onCheckedChange = null)
                }
                UiSpacer(size = layout.switchEndGap)
            }

            item.value?.let { value ->
                Text(
                    text = value,
                    style =
                        if (isList) Theme.brockmann.button.medium.medium
                        else Theme.brockmann.headings.subtitle,
                    color = tint ?: Theme.v2.colors.text.primary,
                )
                UiSpacer(size = 12.dp)
            }

            item.trailingIcon?.let { trailingIcon ->
                Image(
                    imageVector = ImageVector.vectorResource(trailingIcon),
                    modifier = Modifier.size(layout.trailingIconSize),
                    contentDescription = null,
                )
            }
        }

        if (!isList && !isLastItem) {
            UiHorizontalDivider()
        }
    }
}

internal enum class SettingsSwitchSize(
    val width: Dp,
    val height: Dp,
    val knobSize: Dp,
    val knobGap: Dp,
) {
    Compact(width = 40.dp, height = 24.dp, knobSize = 20.dp, knobGap = 2.dp),
    Large(width = 56.dp, height = 30.dp, knobSize = 24.dp, knobGap = 3.dp),
}

/** The redesign's toggle track and knob; the caller owns the click and the switch semantics. */
@Composable
internal fun SettingsSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    size: SettingsSwitchSize = SettingsSwitchSize.Compact,
    onTrackColor: Color = Theme.v2.colors.text.secondary,
) {
    val trackColor by animateColorAsState(if (checked) onTrackColor else SWITCH_OFF_TRACK)
    val knobInset by
        animateDpAsState(
            if (checked) size.width - size.knobSize - size.knobGap else size.knobGap
        )
    Box(
        modifier =
            modifier
                .size(width = size.width, height = size.height)
                .background(trackColor, CircleShape)
    ) {
        Box(
            modifier =
                Modifier.offset(x = knobInset, y = size.knobGap)
                    .size(size.knobSize)
                    .background(Theme.v2.colors.neutrals.n50, CircleShape)
        )
    }
}

/** The large redesign switch with its own tap target, 48 tall around the drawn track. */
@Composable
internal fun SettingsLargeSwitch(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    val size = SettingsSwitchSize.Large
    Box(
        modifier =
            Modifier.height(size.height)
                .wrapContentHeight(unbounded = true)
                .height(SWITCH_TOUCH_HEIGHT)
                .semantics { contentDescription = label }
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                ),
        contentAlignment = Alignment.Center,
    ) {
        SettingsSwitch(
            checked = checked,
            size = size,
            onTrackColor = Theme.v2.colors.primary.accent4,
        )
    }
}

private val SWITCH_TOUCH_HEIGHT = 48.dp

/** Pill next to a row title, e.g. the tier a feature needs. */
@Composable
private fun SettingItemBadge(text: String) {
    Text(
        text = text,
        style = Theme.brockmann.supplementary.caption,
        color = Theme.v2.colors.primary.accent4,
        modifier =
            Modifier.background(
                    color = Theme.v2.colors.variables.backgroundsSurface12,
                    shape = Theme.v2.radius.pill,
                )
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.light,
                    shape = Theme.v2.radius.pill,
                )
                .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

private val SECTION_GAP = 14.dp
private val ABOVE_CARD_GAP = 8.dp
private val TILE_GAP = 6.dp
private val TILE_HEIGHT = 70.dp
private val VERSION_FOOTER_TOP_GAP = 79.dp
private val SWITCH_LINE_HEIGHT = 20.dp
private val SWITCH_OFF_TRACK = Color(0xFF7D909E)
private const val TINTED_SUBTITLE_ALPHA = 0.8f

@Preview
@Composable
internal fun SettingsScreenPreview() {
    SettingsScreen(
        state = defaultSettingsMenu,
        onSettingsItemClick = {},
        onBackClick = {},
        onDismissShareLink = {},
        onVersionClick = {},
    )
}

@Preview
@Composable
private fun SettingsItemPreview() {
    SettingItem(
        item =
            SettingsItemUiModel(
                title = "title".asUiText(),
                subTitle = "subTitle".asUiText(),
                leadingIcon = R.drawable.currency,
                trailingIcon = R.drawable.ic_small_caret_right,
                value = "value",
                backgroundColor = Theme.v2.colors.backgrounds.primary,
            ),
        onClick = {},
        isLastItem = false,
    )
}
