package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldType
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.keygen.FastVaultPasswordUiModel
import com.vultisig.wallet.ui.models.keygen.FastVaultPasswordViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun FastVaultPasswordScreen(
    model: FastVaultPasswordViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    FastVaultPasswordScreen(
        state = state,
        passwordTextFieldState = model.passwordTextFieldState,
        confirmPasswordTextFieldState = model.confirmPasswordTextFieldState,
        onNextClick = model::navigateToHint,
        onBackClick = model::back,
        onShowMoreInfo = model::showMoreInfo,
        onHideMoreInfo = model::hideMoreInfo,
        onTogglePasswordVisibilityClick = model::togglePasswordVisibility,
        onToggleConfirmPasswordVisibilityClick = model::toggleConfirmPasswordVisibility,
    )
}

@Composable
private fun FastVaultPasswordScreen(
    state: FastVaultPasswordUiModel,
    passwordTextFieldState: TextFieldState,
    confirmPasswordTextFieldState: TextFieldState,
    onNextClick: () -> Unit,
    onBackClick: () -> Unit,
    onShowMoreInfo: () -> Unit,
    onHideMoreInfo: () -> Unit,
    onTogglePasswordVisibilityClick: () -> Unit,
    onToggleConfirmPasswordVisibilityClick: () -> Unit,
) {
    var hintBoxOffset by remember { mutableIntStateOf(0) }
    val statusBarHeight = WindowInsets.statusBars.getTop(LocalDensity.current)

    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                onBackClick = onBackClick
            )
        },
        bottomBar = {
            VsButton(
                label = stringResource(R.string.fast_vault_password_screen_next),
                state = if (state.isNextButtonEnabled)
                    VsButtonState.Enabled else VsButtonState.Disabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                onClick = onNextClick,
            )
        }
    ) {
        Column(
            Modifier
                .padding(it)
                .padding(
                    top = 12.dp,
                    start = 24.dp,
                    end = 24.dp,
                )
        ) {
            Text(
                text = stringResource(R.string.fast_vault_password_screen_title),
                style = Theme.brockmann.headings.largeTitle,
                color = Theme.colors.text.primary,
            )
            UiSpacer(16.dp)

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                WarningCard(
                    onShowMoreInfo = onShowMoreInfo,
                    modifier = Modifier
                        .onGloballyPositioned { position ->
                            hintBoxOffset = position.boundsInRoot().bottom.toInt()
                        }
                )
                UiSpacer(24.dp)
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
                ) {
                    val focusRequester = remember {
                        FocusRequester()
                    }

                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }

                    VsTextInputField(
                        textFieldState = passwordTextFieldState,
                        hint = stringResource(R.string.fast_vault_password_screen_password_hint),
                        trailingIcon = R.drawable.ic_question_mark,
                        type = VsTextInputFieldType.Password(
                            isVisible = state.isPasswordVisible,
                            onVisibilityClick = onTogglePasswordVisibilityClick
                        ),
                        focusRequester = focusRequester,
                        imeAction = ImeAction.Next,
                    )

                    VsTextInputField(
                        textFieldState = confirmPasswordTextFieldState,
                        trailingIcon = R.drawable.ic_question_mark,
                        hint = stringResource(R.string.fast_vault_password_screen_reenter_password_hint),
                        type = VsTextInputFieldType.Password(
                            isVisible = state.isConfirmPasswordVisible,
                            onVisibilityClick = onToggleConfirmPasswordVisibilityClick
                        ),
                        innerState = state.innerState,
                        footNote = state.errorMessage.asString(),
                        imeAction = ImeAction.Go,
                        onKeyboardAction = {
                            onNextClick()
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.isMoreInfoVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PasswordMoreInfo(
                text = stringResource(R.string.fast_vault_password_screen_hint),
                title = stringResource(R.string.fast_vault_password_screen_hint_title),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .offset {
                        IntOffset(
                            x = 0,
                            y = hintBoxOffset - statusBarHeight
                        )
                    }
                    .clickable(onClick = onHideMoreInfo)
            )
        }
    }
}

@Composable
private fun WarningCard(
    modifier: Modifier,
    onShowMoreInfo: () -> Unit,
) {
    val warningColor = Theme.colors.alerts.warning
    val lightWarningColor = Theme.colors.alerts.warning.copy(alpha = 0.25f)
    // TODO replace with Banner
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(lightWarningColor)
            .border(
                width = 1.dp,
                shape = RoundedCornerShape(12.dp),
                color = lightWarningColor
            )
            .padding(16.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.fast_vault_password_screen_warning),
            style = Theme.brockmann.supplementary.footnote,
            color = warningColor
        )
        UiIcon(
            R.drawable.alert,
            size = 16.dp,
            tint = warningColor,
            onClick = onShowMoreInfo
        )
    }
}

@Composable
@Preview
private fun FastVaultPasswordScreenPreview() {
    FastVaultPasswordScreen(
        state = FastVaultPasswordUiModel(
            isMoreInfoVisible = true
        ),
        passwordTextFieldState = rememberTextFieldState(),
        confirmPasswordTextFieldState = rememberTextFieldState(),
        onNextClick = {},
        onBackClick = {},
        onShowMoreInfo = {},
        onHideMoreInfo = {},
        onToggleConfirmPasswordVisibilityClick = {},
        onTogglePasswordVisibilityClick = {}
    )
}

@Composable
private fun PasswordMoreInfo(
    text: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val titleTextStyle =
        Theme.brockmann.body.s.medium.copy(
            color = Theme.colors.text.button.dark
        )
    val hintTextStyle =
        Theme.brockmann.supplementary.footnote.copy(
            color = Theme.colors.text.extraLight
        )
    val closeIcon = painterResource(R.drawable.x)
    val closeIconTint = Theme.colors.text.button.disabled
    val backgroundColor = Theme.colors.neutrals.n200
    val fontSize = hintTextStyle.fontSize.value

    Canvas(
        modifier = modifier
            .fillMaxSize()
    ) {
        val canvasWidth = size.width
        val backgroundPadding = 16.dp.toPx()
        var maxWidth = size.width - (2 * backgroundPadding)
        val lineHeight = 40f
        val spaceBetweenTitleAndHint = 10
        val lines = mutableListOf<String>()
        val pointerHeight = 50f
        val closeIconSize = 16.dp.toPx()
        val cornerRadius = 12.dp.toPx()
        val measurementTitle = textMeasurer.measure(
            text = title,
            style = titleTextStyle,
            overflow = TextOverflow.Clip
        )
        val titleHeight = measurementTitle.size.height
        var totalHeight = 0f
        val startBoxFromX = 20.dp.toPx()
        var currentLine = StringBuilder()
        text.split(" ").forEach { word ->
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val measurementHint = textMeasurer.measure(
                text = testLine,
                style = hintTextStyle,
                overflow = TextOverflow.Clip
            )

            if (measurementHint.size.width <= canvasWidth - (backgroundPadding * 2) - startBoxFromX) {
                currentLine.append(if (currentLine.isEmpty()) word else " $word")
            } else {
                if (currentLine.isNotEmpty()) {
                    val lineWidth =
                        textMeasurer.measure(
                            currentLine.toString(),
                            style = hintTextStyle
                        ).size.width
                    maxWidth = maxWidth.coerceAtLeast(lineWidth.toFloat())
                    lines.add(currentLine.toString())
                    totalHeight += fontSize + lineHeight
                    currentLine = StringBuilder(word)
                }
            }
        }

        if (currentLine.isNotEmpty()) {
            val lineWidth =
                textMeasurer.measure(currentLine.toString(), style = hintTextStyle).size.width
            maxWidth = maxWidth.coerceAtLeast(lineWidth.toFloat())
            lines.add(currentLine.toString())
            totalHeight += fontSize + lineHeight
        }
        val startPointerX = size.width - 2 * pointerHeight - backgroundPadding

        val backgroundPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = startBoxFromX,
                    top = pointerHeight,
                    right = maxWidth + (backgroundPadding * 2),
                    bottom = totalHeight + titleHeight + pointerHeight + (backgroundPadding * 2),
                    cornerRadius = CornerRadius(cornerRadius)
                )
            )
            moveTo(0f + startPointerX, pointerHeight)
            lineTo(pointerHeight + startPointerX, 0f)
            lineTo(2 * pointerHeight + startPointerX, pointerHeight)
            close()
        }
        drawPath(backgroundPath, color = backgroundColor)
        drawText(
            textMeasurer = textMeasurer,
            text = title,
            style = titleTextStyle,
            topLeft = Offset(
                x = backgroundPadding + startBoxFromX,
                y = pointerHeight + backgroundPadding
            )
        )
        var currentY =
            backgroundPadding + pointerHeight + titleHeight + spaceBetweenTitleAndHint
        lines.forEach { line ->
            drawText(
                textMeasurer = textMeasurer,
                text = line,
                style = hintTextStyle,
                topLeft = Offset(backgroundPadding + startBoxFromX, currentY)
            )
            currentY += fontSize + lineHeight
        }

        translate(
            left = maxWidth,
            top = pointerHeight - closeIconSize / 2 + backgroundPadding + titleHeight / 2
        ) {
            with(closeIcon) {
                draw(
                    size = Size(closeIconSize, closeIconSize),
                    colorFilter = ColorFilter.tint(color = closeIconTint),
                )
            }
        }

    }
}