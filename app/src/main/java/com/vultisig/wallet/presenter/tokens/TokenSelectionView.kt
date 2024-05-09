package com.vultisig.wallet.presenter.tokens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.models.Coins
import com.vultisig.wallet.presenter.common.TopBar
import com.vultisig.wallet.ui.components.TokenSelectionItem
import com.vultisig.wallet.ui.theme.appColor
import com.vultisig.wallet.ui.theme.dimens

@Composable
fun TokenSelectionView(
    navController: NavHostController
) {
    val viewModel: TokenSelectionViewModel = hiltViewModel()

    Column(
        modifier = Modifier
            .background(MaterialTheme.appColor.oxfordBlue800)
            .padding(horizontal = MaterialTheme.dimens.small2)
    ) {
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small2))

        TopBar(
            centerText = stringResource(R.string.tokens), startIcon = R.drawable.caret_left,
            navController = navController
        )

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small2))

        LazyColumn {
            items(viewModel.tokenList.value) { token ->
                TokenSelectionItem(
                    title = token.ticker,
                    subtitle = token.priceProviderID,
                    logo = Coins.getCoinLogo(logoName = token.logo),
                    isChecked = false,
                )
            }
        }
    }
}

@Preview
@Composable
fun TokenSelectionPreview() {
    TokenSelectionView(
        navController = rememberNavController()
    )
}
