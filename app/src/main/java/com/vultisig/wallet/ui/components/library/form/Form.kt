package com.vultisig.wallet.ui.components.library.form

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text2.BasicTextField2
import androidx.compose.foundation.text2.input.TextFieldLineLimits
import androidx.compose.foundation.text2.input.TextFieldState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vultisig.wallet.R
import com.vultisig.wallet.common.UiText
import com.vultisig.wallet.common.asString
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.ui.components.PercentText
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.cursorBrush


@Composable
internal fun FormTokenCard(
    selectedTitle: String,
    selectedIcon: ImageModel,
    availableToken: String,
    chainLogo :Int?,
    isExpanded: Boolean,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    FormCard {
        TokenCard(
            title = selectedTitle,
            tokenLogo = selectedIcon,
            availableToken = availableToken,
            chainLogo = chainLogo,
            actionIcon = R.drawable.caret_down,
            onClick = onClick,
        )

        AnimatedVisibility(visible = isExpanded) {
            Column {
                content()
            }
        }
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
            AsyncImage(
                model = tokenLogo,
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .padding(4.dp)
                    .align(Alignment.Center)
            )
            if (chainLogo != null)
                Image(
                    painter = painterResource(id = chainLogo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(12.dp)
                        .border(
                            width = 1.dp,
                            color = Theme.colors.neutral0,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .align(BottomEnd)
                )
        }

        UiSpacer(size = 8.dp)

        Text(
            text = tokenStandard?.let { "$title ($it)" } ?: title,
            color = Theme.colors.neutral100,
            style = Theme.menlo.body1,
        )

        UiSpacer(weight = 1f)

        Text(
            text = availableToken ?: "",
            color = Theme.colors.neutral100,
            style = Theme.menlo.body1,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FormTextFieldCard(
    title: String,
    hint: String,
    error: UiText?,
    keyboardType: KeyboardType,
    textFieldState: TextFieldState,
    onLostFocus: () -> Unit = {},
    actions: (@Composable RowScope.() -> Unit)? = null,
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
                textFieldState = textFieldState,
                actions = actions,
                onLostFocus = onLostFocus
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FormTextFieldCardWithPercentage(
    title: String,
    hint: String,
    error: UiText?,
    keyboardType: KeyboardType,
    textFieldState: TextFieldState,
    onLostFocus: () -> Unit = {},
    onPercentClick: (percent: Float) -> Unit = {},
    actions: (@Composable RowScope.() -> Unit)? = null,

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
                actions = actions,
                onLostFocus = onLostFocus
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FormTextField(
    hint: String,
    keyboardType: KeyboardType,
    textFieldState: TextFieldState,
    actions: (@Composable RowScope.() -> Unit)? = null,
    onLostFocus: () -> Unit,
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
            keyboardType = keyboardType,
            textFieldState = textFieldState,
            onLostFocus = onLostFocus,
            modifier = Modifier
                .weight(1f),
        )

        UiSpacer(size = 8.dp)

        actions?.invoke(this)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BasicFormTextField(
    hint: String,
    keyboardType: KeyboardType,
    textFieldState: TextFieldState,
    onLostFocus: () -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = Theme.menlo.body1,
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }

    BasicTextField2(
        state = textFieldState,
        lineLimits = TextFieldLineLimits.SingleLine,
        textStyle = textStyle.copy(color = Theme.colors.neutral100),
        cursorBrush = Theme.cursorBrush,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onAny = {
                focusManager.clearFocus()
            }
        ),
        modifier = modifier
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
                    color = Theme.colors.neutral100,
                    style = textStyle,
                )
            }
            textField()
        }
    )
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
            color = Theme.colors.neutral100,
            style = Theme.montserrat.body1,
        )
        UiSpacer(size = 10.dp)
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FormTitleCollapsibleTextField(
    title: String,
    modifier: Modifier = Modifier,
    isFormVisible: Boolean = false,
    hint: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    textFieldState: TextFieldState,
    actions: (@Composable RowScope.() -> Unit)? = null,
    onLostFocus: () -> Unit,
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
                color = Theme.colors.neutral100,
                style = Theme.montserrat.body1,
            )
            Icon(
                painter = painterResource(id = R.drawable.caret_down),
                contentDescription = "expand collapse",
                modifier = Modifier
                    .width(12.dp)
                    .rotate(arrowDegree.value),
                tint = Theme.colors.neutral100
            )
        }
        UiSpacer(size = 10.dp)
        AnimatedVisibility(visible = isExpanded) {
            FormCard {
                FormTextField(
                    hint,
                    keyboardType,
                    textFieldState,
                    actions,
                    onLostFocus,
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
                color = Theme.colors.neutral100,
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
            containerColor = Theme.colors.oxfordBlue600Main,
        ),
        shape = RoundedCornerShape(10.dp),
        content = content,
    )
}

@Composable
internal fun FormDetails(
    title: String,
    value: String,
) {
    Row {
        Text(
            text = title,
            color = Theme.colors.neutral100,
            style = Theme.montserrat.body1,
        )
        UiSpacer(weight = 1f)
        Text(
            text = value,
            color = Theme.colors.neutral100,
            style = Theme.menlo.body1
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
        UiSpacer(size = 8.dp)
        AnimatedContent(
            targetState = errorText,
            label = "error message"
        ) { errorMessage ->
            if (errorMessage != null)
                Text(
                    text = errorMessage.asString(),
                    color = Theme.colors.error,
                    style = Theme.menlo.body1
                )
        }

    }
}