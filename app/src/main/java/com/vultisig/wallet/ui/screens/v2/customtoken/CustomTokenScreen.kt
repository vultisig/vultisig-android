package com.vultisig.wallet.ui.screens.v2.customtoken

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.bottomsheets.V2BottomSheet
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.models.CustomTokenUiModel
import com.vultisig.wallet.ui.models.CustomTokenViewModel
import com.vultisig.wallet.ui.screens.v2.customtoken.components.CustomTokenSearchBar
import com.vultisig.wallet.ui.screens.v2.customtoken.components.LoadingSearchCustomToken
import com.vultisig.wallet.ui.screens.v2.customtoken.components.SearchTokenResult
import com.vultisig.wallet.ui.screens.v2.customtoken.components.TokenNotFoundError
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsClipboardService


@Composable
internal fun CustomTokenScreen(
    viewModel: CustomTokenViewModel = hiltViewModel(),
) {
    val uiModel by viewModel.uiModel.collectAsState()
    val textToPaste by VsClipboardService.getClipboardData()
    CustomTokenScreen(
        state = uiModel,
        searchFieldState = viewModel.searchFieldState,
        onPasteClick = { viewModel.pasteToSearchField(textToPaste) },
        onSearchClick = viewModel::searchCustomToken,
        onAddTokenClick = viewModel::addCoinToTempRepo,
        onBackClick = viewModel::back,
        onCloseClick = viewModel::close
    )
}

@Composable
private fun CustomTokenScreen(
    state: CustomTokenUiModel,
    searchFieldState: TextFieldState,
    onBackClick: () -> Unit,
    onPasteClick: () -> Unit,
    onSearchClick: () -> Unit,
    onAddTokenClick: () -> Unit,
    onCloseClick: () -> Unit,
) {
    V2BottomSheet(
        onDismissRequest = onBackClick,
        leftAction = {
            VsCircleButton(
                drawableResId = R.drawable.big_close,
                onClick = onBackClick,
                type = VsCircleButtonType.Tertiary,
                size = VsCircleButtonSize.Small,
            )
        },
    ) {

        Column(
            Modifier
                .fillMaxSize()
                .padding(all = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            UiSpacer(
                size = 24.dp
            )
            Text(
                text = stringResource(R.string.custom_token_screen_title),
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.headings.title2,
            )

            UiSpacer(
                size = 16.dp
            )


            CustomTokenSearchBar(
                initialDisplay = state.isInitial,
                onSearchClick = onSearchClick,
                onPasteClick = onPasteClick,
                state = searchFieldState,
                onCloseClick = onCloseClick
            )
            UiSpacer(
                size = 16.dp
            )

            when {
                state.isLoading -> {
                    LoadingSearchCustomToken()
                }

                state.hasError -> {
                    TokenNotFoundError(
                        onRetryClick = onSearchClick
                    )
                }

                state.token != null -> {
                    SearchTokenResult(
                        token = state.token,
                        onAddTokenClick = onAddTokenClick,
                    )
                }
            }
        }
    }
}




@Composable
@Preview
private fun CustomTokenScreenPreview() {
    CustomTokenScreen(
        state = CustomTokenUiModel(
            chainLogo = R.drawable.chainflip, isLoading = false,
            token = Coins.Ethereum.GRT,
            hasError = false
        ),
        searchFieldState = rememberTextFieldState(),
        onPasteClick = {},
        onSearchClick = {},
        onAddTokenClick = {},
        onCloseClick = {},
        onBackClick = {}
    )
}