package com.vultisig.wallet.ui.components.library.form

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomEnd
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.ui.components.PercentText
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.cursorBrush
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString


@Composable
internal fun FormTokenCard(
    selectedTitle: String,
    selectedIcon: ImageModel,
    availableToken: String,
    chainLogo :Int?,
    onClick: () -> Unit,
) {
    FormCard {
        TokenCard(
            title = selectedTitle,
            tokenLogo = selectedIcon,
            availableToken = availableToken,
            chainLogo = chainLogo,
            actionIcon = R.drawable.ic_caret_right,
            onClick = onClick,
        )
    }
}

@Composable
internal fun TokenCard(
    title: String,
    tokenStandard: String? = null,
    tokenLogo: ImageModel,
    @DrawableRes chainLogo: Int? = null,
    @DrawableRes actionIcon: Int? = null,
    availableToken: String? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .padding(
                vertical = 8.dp,
                horizontal = 12.dp
            )
            .clickable(onClick = onClick),
    ) {

        Box {
            TokenLogo(
                logo = tokenLogo,
                title = title,
                modifier = Modifier
                    .size(36.dp)
                    .padding(4.dp)
                    .align(Alignment.Center),
                errorLogoModifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Theme.colors.neutrals.n100)
            )
            if (chainLogo != null)
                Image(
                    painter = painterResource(id = chainLogo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(12.dp)
                        .border(
                            width = 1.dp,
                            color = Theme.colors.neutrals.n50,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .align(BottomEnd)
                )
        }

        UiSpacer(size = 8.dp)

        Text(
            text = tokenStandard?.let { "$title ($it)" } ?: title,
            color = Theme.colors.neutrals.n100,
            style = Theme.menlo.body1,
        )

        UiSpacer(weight = 1f)

        Text(
            text = availableToken ?: "",
            color = Theme.colors.neutrals.n200,
            style = Theme.menlo.body1.copy(fontWeight = FontWeight.Bold),
        )

        if (actionIcon != null) {
            UiSpacer(size = 8.dp)

            UiIcon(
                drawableResId = actionIcon,
                size = 20.dp,
            )
        }
    }
}

@Composable
internal fun FormTextFieldCard(
    title: String,
    hint: String,
    error: UiText?,
    keyboardType: KeyboardType,
    textFieldState: TextFieldState,
    outputTransformation: OutputTransformation? = null,
    onLostFocus: () -> Unit = {},
    hintColor: Color = Theme.colors.neutrals.n100,
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    TextFieldValidator(
        errorText = error,
    ) {
        FormEntry(
            title = title,
        ) {
            FormTextField(
                hint = hint,
                keyboardType = keyboardType,
                outputTransformation = outputTransformation,
                textFieldState = textFieldState,
                content = content,
                onLostFocus = onLostFocus,
                hintColor = hintColor,
            )
        }
    }
}


@Composable
internal fun FormTextFieldCard(
    hint: String,
    error: UiText?,
    keyboardType: KeyboardType,
    textFieldState: TextFieldState,
    onLostFocus: () -> Unit = {},
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    TextFieldValidator(
        errorText = error,
    ) {
        FormCard {
            FormTextField(
                hint = hint,
                keyboardType = keyboardType,
                textFieldState = textFieldState,
                content = content,
                onLostFocus = onLostFocus
            )
        }
    }
}


@Composable
internal fun FormTextFieldCardWithPercentage(
    title: String,
    hint: String,
    error: UiText?,
    keyboardType: KeyboardType,
    textFieldState: TextFieldState,
    outputTransformation: OutputTransformation? = null,
    onLostFocus: () -> Unit = {},
    onPercentClick: (percent: Float) -> Unit = {},
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    TextFieldValidator(
        errorText = error,
    ) {
        FormEntryWithPercentage(
            title = title,
            onPercentClick = onPercentClick
        ) {
            FormTextField(
                hint = hint,
                keyboardType = keyboardType,
                textFieldState = textFieldState,
                outputTransformation = outputTransformation,
                content = content,
                onLostFocus = onLostFocus
            )
        }
    }
}


@Composable
internal fun FormTextField(
    hint: String,
    keyboardType: KeyboardType,
    textFieldState: TextFieldState,
    hintColor: Color = Theme.colors.neutrals.n100,
    outputTransformation: OutputTransformation? = null,
    onLostFocus: () -> Unit,
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .padding(
                horizontal = 12.dp,
                vertical = 14.dp
            ),
    ) {
        BasicFormTextField(
            hint = hint,
            hintColor = hintColor,
            keyboardType = keyboardType,
            textFieldState = textFieldState,
            outputTransformation = outputTransformation,
            onLostFocus = onLostFocus,
            modifier = Modifier
                .weight(1f),
        )

        UiSpacer(size = 8.dp)

        content?.invoke(this)
    }
}


@Composable
internal fun BasicFormTextField(
    hint: String,
    keyboardType: KeyboardType,
    textFieldState: TextFieldState,
    onLostFocus: () -> Unit,
    modifier: Modifier = Modifier,
    outputTransformation: OutputTransformation? = null,
    textStyle: TextStyle = Theme.menlo.body1,
    hintColor: Color = Theme.colors.neutrals.n100,
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }

    BasicTextField(
        state = textFieldState,
        lineLimits = TextFieldLineLimits.SingleLine,
        textStyle = textStyle.copy(color = Theme.colors.neutrals.n100),
        cursorBrush = Theme.cursorBrush,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Done,
        ),
        outputTransformation = outputTransformation,
        onKeyboardAction = {
            focusManager.clearFocus()
        },
        modifier = modifier
             .onFocusEvent {
                if (isFocused != it.isFocused ) {
                    isFocused = it.isFocused
                    if (!isFocused) {
                        onLostFocus()
                    }
                }
            },
        decorator = { textField ->
            if (textFieldState.text.isEmpty()) {
                Text(
                    text = hint,
                    color = hintColor,
                    style = textStyle,
                )
            }
            textField()
        }
    )
}


@Composable
internal fun FormBasicSecureTextField(
    hint: String,
    isObfuscationMode: Boolean,
    textFieldState: TextFieldState,
    error: UiText?,
    onLostFocus: () -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = Theme.menlo.body1,
    content: @Composable (RowScope.() -> Unit)?
) {
    var isFocused by remember { mutableStateOf(false) }

    TextFieldValidator(
        errorText = error,
    ) {
        FormCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 12.dp,
                        vertical = 6.dp
                    ),
            ) {
                Row(modifier = Modifier.weight(1f)) {
                    BasicSecureTextField(
                        state = textFieldState,
                        textStyle = textStyle.copy(color = Theme.colors.neutrals.n100),
                        cursorBrush = Theme.cursorBrush,
                        textObfuscationMode = if (isObfuscationMode)
                            TextObfuscationMode.RevealLastTyped else TextObfuscationMode.Visible,
                        modifier = modifier
                            .fillMaxWidth()
                            .onFocusEvent {
                                if (isFocused != it.isFocused) {
                                    isFocused = it.isFocused
                                    if (!isFocused) {
                                        onLostFocus()
                                    }
                                }
                            },
                        decorator = { textField ->
                            if (textFieldState.text.isEmpty()) {
                                Text(
                                    text = hint,
                                    color = Theme.colors.neutrals.n100,
                                    style = textStyle,
                                )
                            }
                            textField()
                        }
                    )
                }

                content?.invoke(this)
            }
        }
    }
}


@Composable
internal fun FormEntry(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    FormTitleContainer(
        title = title,
        modifier = modifier,
    ) {
        FormCard(content = content)
    }
}

@Composable
internal fun FormEntryWithPercentage(
    title: String,
    modifier: Modifier = Modifier,
    onPercentClick: (percent: Float) -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    FormTitleContainerWithPercentage(
        title = title,
        modifier = modifier,
        onPercentClick = { percent ->
            onPercentClick(percent / 100f)
        },
    ) {
        FormCard(content = content)
    }
}

@Composable
internal fun FormTitleContainer(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
    ) {
        Text(
            text = title,
            color = Theme.colors.neutrals.n100,
            style = Theme.montserrat.body1,
        )
        UiSpacer(size = 10.dp)
        content()
    }
}


@Composable
internal fun FormTitleCollapsibleTextField(
    title: String,
    modifier: Modifier = Modifier,
    isFormVisible: Boolean = false,
    hint: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    textFieldState: TextFieldState,
    onLostFocus: () -> Unit,
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    var isExpanded by remember {
        mutableStateOf(isFormVisible)
    }
    val arrowDegree = animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "rotate arrow"
    )
    Column(
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clickable { isExpanded = !isExpanded }
        ) {
            Text(
                text = title,
                color = Theme.colors.neutrals.n100,
                style = Theme.montserrat.body1,
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_caret_down),
                contentDescription = "expand collapse",
                modifier = Modifier
                    .width(12.dp)
                    .rotate(arrowDegree.value),
                tint = Theme.colors.neutrals.n100
            )
        }
        UiSpacer(size = 10.dp)
        AnimatedVisibility(visible = isExpanded) {
            FormCard {
                FormTextField(
                    hint = hint,
                    keyboardType = keyboardType,
                    textFieldState = textFieldState,
                    content = content,
                    onLostFocus = onLostFocus,
                )
            }
        }
    }
}

@Composable
internal fun FormTitleContainerWithPercentage(
    title: String,
    modifier: Modifier = Modifier,
    onPercentClick: (percent: Int) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
    ) {

        Row(
            modifier = Modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Theme.colors.neutrals.n100,
                style = Theme.montserrat.body1,
            )
            UiSpacer(weight = 1f)
            PercentText(
                25,
                onPercentClick
            )
            UiSpacer(size = 10.dp)
            PercentText(
                50,
                onPercentClick
            )
        }

        UiSpacer(size = 10.dp)

        content()
    }
}

@Composable
internal fun FormCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Theme.colors.backgrounds.secondary,
        ),
        shape = RoundedCornerShape(10.dp),
        content = content,
    )
}

@Composable
internal fun FormDetails(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
    ) {
        Text(
            text = title,
            color = Theme.colors.neutrals.n100,
            style = Theme.montserrat.body1,
        )
        UiSpacer(weight = 1f)
        Text(
            text = value,
            color = Theme.colors.neutrals.n100,
            style = Theme.menlo.body1
        )
    }
}

@Composable
internal fun FormDetails2(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            color = Theme.colors.text.extraLight,
            style = Theme.brockmann.supplementary.caption,
            textAlign = TextAlign.Start,
        )

        if (placeholder != null) {
            placeholder()
        } else {
            Text(
                text = value,
                color = Theme.colors.text.light,
                style = Theme.brockmann.supplementary.caption,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
internal fun FormDetails2(
    title: AnnotatedString,
    value: AnnotatedString,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            color = Theme.colors.text.extraLight,
            style = Theme.brockmann.supplementary.caption,
            textAlign = TextAlign.Start,
        )

        if (placeholder != null) {
            placeholder()
        } else {
            Text(
                text = value,
                color = Theme.colors.text.light,
                style = Theme.brockmann.supplementary.caption,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
internal fun FormDetails(
    title: AnnotatedString,
    value: AnnotatedString,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Theme.colors.neutrals.n100,
            style = Theme.montserrat.body1,
        )
        Text(
            text = value,
            color = Theme.colors.neutrals.n100,
            style = Theme.menlo.body1,
            textAlign = TextAlign.End
        )
    }
}

@Composable
internal fun FormError(
    errorMessage: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        UiHorizontalDivider()

        Text(
            text = errorMessage,
            color = Theme.colors.alerts.error,
            style = Theme.brockmann.supplementary.footnote,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}


@Composable
internal fun TextFieldValidator(
    errorText: UiText?,
    content: @Composable () -> Unit,
) {
    Column {
        content()
        AnimatedContent(
            targetState = errorText,
            label = "error message"
        ) { errorMessage ->
            if (errorMessage != null) {
                Column {
                    UiSpacer(size = 8.dp)
                    Text(
                        text = errorMessage.asString(),
                        color = Theme.colors.backgrounds.amber,
                        style = Theme.menlo.body1
                    )
                }
            }
        }

    }
}