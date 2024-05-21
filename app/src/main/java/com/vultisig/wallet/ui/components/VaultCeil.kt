package com.vultisig.wallet.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vultisig.wallet.R
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.ui.theme.appColor
import com.vultisig.wallet.ui.theme.dimens
import com.vultisig.wallet.ui.theme.montserratFamily

@Composable
fun VaultCeil(
    vault: Vault,
    onSelectVault: (vaultId: String) -> Unit,
) {
    val textColor = MaterialTheme.colorScheme.onBackground
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.appColor.oxfordBlue400),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = MaterialTheme.dimens.marginMedium,
                end = MaterialTheme.dimens.marginMedium,
                top = MaterialTheme.dimens.marginMedium,
            )
            .height(60.dp),
        onClick = {
            onSelectVault(vault.id)
        }

    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = vault.name.uppercase(),
                style = MaterialTheme.montserratFamily.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor,
                fontSize = 16.sp,
                modifier = Modifier
                    .padding(
                        start = MaterialTheme.dimens.marginMedium,
                        end = MaterialTheme.dimens.marginMedium,
                        top = MaterialTheme.dimens.marginMedium,
                        bottom = MaterialTheme.dimens.marginMedium,
                    )
                    .wrapContentHeight(align = Alignment.CenterVertically)

            )
            Image(
                painter = painterResource(R.drawable.caret_right),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = MaterialTheme.dimens.marginMedium)

            )
        }
    }
}