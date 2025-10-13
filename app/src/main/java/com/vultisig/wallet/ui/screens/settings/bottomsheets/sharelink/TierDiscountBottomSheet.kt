package com.vultisig.wallet.ui.screens.settings.bottomsheets.sharelink

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.screens.settings.TierType
import com.vultisig.wallet.ui.theme.Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TierDiscountBottomSheet(
    tier: TierType,
    onContinue: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Theme.colors.backgrounds.secondary,
        shape = RoundedCornerShape(24.dp),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
    ) {
        TierDiscounBottomSheetContent(
            onContinue = onContinue,
        )
    }
}

@Composable
internal fun TierDiscounBottomSheetContent(
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(32.dp)
            .fillMaxWidth()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.tier_bronze_bottomsheet),
                contentDescription = "image",
            )
        }

        UiSpacer(32.dp)

        Text(
            text = "Unlock Bronze Tier",
            style = Theme.brockmann.headings.title1,
            textAlign = TextAlign.Center,
            color = Theme.colors.text.primary,
        )

        UiSpacer(32.dp)

        Text(
            text = stringResource(R.string.vault_tier_bronze_description),
            style = Theme.brockmann.body.s.regular,
            textAlign = TextAlign.Center,
            color = Theme.colors.text.primary,
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
        )
    }
}