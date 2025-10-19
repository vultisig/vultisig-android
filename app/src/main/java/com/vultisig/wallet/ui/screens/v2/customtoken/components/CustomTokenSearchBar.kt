package com.vultisig.wallet.ui.screens.v2.customtoken.components

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.buttons.DesignType
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.CornerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.theme.Theme
import dagger.hilt.android.qualifiers.ApplicationContext

@Composable
internal fun CustomTokenSearchBar(
    state: TextFieldState,
    onPasteClick: () -> Unit,
    onSearchClick: () -> Unit,
    onCloseClick: () -> Unit,
    initialDisplay: Boolean,
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier.Companion
            .height(intrinsicSize = IntrinsicSize.Max)
    ) {

        V2Container(
            modifier = Modifier.Companion
                .weight(1f)
                .height(40.dp),
            type = ContainerType.SECONDARY,
            cornerType = CornerType.Circular,
        ) {
            Row(
                verticalAlignment = Alignment.Companion.CenterVertically,
                modifier = Modifier.Companion
                    .padding(horizontal = 12.dp)
            ) {
                AnimatedVisibility(visible = initialDisplay) {

                    Row {
                        UiIcon(
                            drawableResId = R.drawable.search_custom_token,
                            size = 16.dp,
                            tint = Theme.colors.text.button.light,
                            onClick = onSearchClick
                        )
                        UiSpacer(
                            size = 8.dp
                        )
                    }
                }


                BasicTextField(
                    state = state,
                    modifier = Modifier.Companion
                        .weight(1f)
                        .fillMaxHeight(),
                    textStyle = Theme.brockmann.supplementary.footnote.copy(
                        color = Theme.colors.text.primary,
                    ),
                    decorator = { textField ->
                        Box(
                            contentAlignment = Alignment.Companion.CenterStart
                        ) {
                            if (state.text.isEmpty()) {
                                Text(
                                    text = context.getString(R.string.custom_token_enter_contract_address),
                                    style = Theme.brockmann.supplementary.footnote,
                                    color = Theme.colors.text.extraLight
                                )
                            } else {
                                textField()
                            }
                        }
                    }
                )


                UiSpacer(
                    size = 8.dp
                )

                AnimatedContent(targetState = initialDisplay) {

                    UiIcon(
                        drawableResId = if (it)
                            R.drawable.paste_v2 else
                            R.drawable.big_close,
                        size = 16.dp,
                        tint = Theme.colors.text.light,
                        onClick = if (it) onPasteClick else onCloseClick,
                    )
                }

            }
        }


        AnimatedVisibility(initialDisplay) {

            Row {

                UiSpacer(
                    12.dp
                )

                VsCircleButton(
                    drawableResId = R.drawable.icon_search_menu,
                    onClick = onSearchClick,
                    type = VsCircleButtonType.Custom(color = Theme.colors.backgrounds.secondary),
                    modifier = Modifier.Companion,
                    size = VsCircleButtonSize.Custom(size = 40.dp),
                    designType = DesignType.Solid,
                    iconSize = 20.dp,
                    tint = Theme.colors.text.light
                )
            }


        }

    }
}

@Preview
@Composable
private fun CustomTokenSearchBarPreview1() {
    CustomTokenSearchBar(
        onPasteClick = {},
        onSearchClick = {},
        initialDisplay = false,
        state = rememberTextFieldState(),
        onCloseClick = {},
    )
}


@Preview
@Composable
fun CustomTokenSearchBarPreview2() {
    CustomTokenSearchBar(
        onPasteClick = {},
        onSearchClick = {},
        initialDisplay = false,
        state = rememberTextFieldState(),
        onCloseClick = {},
    )
}