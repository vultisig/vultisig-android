package com.vultisig.wallet.presenter.keysign

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.google.gson.Gson
import com.vultisig.wallet.R
import com.vultisig.wallet.common.Utils
import com.vultisig.wallet.models.Coins
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.presenter.common.KeepScreenOn
import com.vultisig.wallet.presenter.common.TopBar
import com.vultisig.wallet.tss.TssKeyType
import com.vultisig.wallet.ui.theme.appColor
import com.vultisig.wallet.ui.theme.dimens
import com.vultisig.wallet.ui.theme.menloFamily
import wallet.core.jni.CoinType
import java.math.BigInteger
import java.util.UUID

@Composable
fun Keysign(navController: NavController, viewModel: KeysignViewModel) {
    KeepScreenOn()
    val context: Context = LocalContext.current.applicationContext
    LaunchedEffect(key1 = Unit) {
        // kick it off to generate key
        viewModel.startKeysign()
    }
    val textColor = MaterialTheme.appColor.neutral0
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(MaterialTheme.appColor.oxfordBlue800)
            .padding(
                vertical = MaterialTheme.dimens.marginMedium,
                horizontal = MaterialTheme.dimens.marginSmall
            )
    ) {
        TopBar(
            centerText = stringResource(id = R.string.keysign),
            navController = navController,
            startIcon = R.drawable.caret_left,
        )
        Spacer(modifier = Modifier.weight(1f))

        when (viewModel.currentState.value) {
            KeysignState.CreatingInstance -> {
                Text(
                    modifier = Modifier.padding(top = MaterialTheme.dimens.medium1),
                    text = "PREPARING VAULT...",
                    style = MaterialTheme.menloFamily.titleLarge,
                    color = MaterialTheme.appColor.neutral0,
                    textAlign = TextAlign.Center
                )
            }

            KeysignState.KeysignECDSA -> {
                Text(
                    modifier = Modifier.padding(top = MaterialTheme.dimens.medium1),
                    text = "Signing with ECDSA...",
                    style = MaterialTheme.menloFamily.titleLarge,
                    color = MaterialTheme.appColor.neutral0,
                    textAlign = TextAlign.Center
                )
            }

            KeysignState.KeysignEdDSA -> {
                Text(
                    modifier = Modifier.padding(top = MaterialTheme.dimens.medium1),
                    text = "Signing with EdDSA...",
                    style = MaterialTheme.menloFamily.titleLarge,
                    color = MaterialTheme.appColor.neutral0,
                    textAlign = TextAlign.Center
                )
            }

            KeysignState.KeysignFinished -> {
                Text(
                    modifier = Modifier.padding(top = MaterialTheme.dimens.medium1),
                    text = "Keysign Finished!",
                    style = MaterialTheme.menloFamily.titleLarge,
                    color = MaterialTheme.appColor.neutral0,
                    textAlign = TextAlign.Center
                )
            }

            KeysignState.ERROR -> {
                LaunchedEffect(key1 = Unit) {

                }
                Text(
                    modifier = Modifier.padding(top = MaterialTheme.dimens.medium1),
                    text = "Error! Please try again. $viewModel.errorMessage.value",
                    style = MaterialTheme.menloFamily.titleLarge,
                    color = MaterialTheme.appColor.neutral0,
                    textAlign = TextAlign.Center
                )
            }

        }
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            painter = painterResource(id = R.drawable.wifi),
            contentDescription = null,
            tint = MaterialTheme.appColor.neutral0
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
        Text(
            modifier = Modifier.padding(horizontal = MaterialTheme.dimens.large),
            text = "Keep devices on the same WiFi Network with vultisig open.",
            color = textColor,
            style = MaterialTheme.menloFamily.headlineSmall.copy(
                textAlign = TextAlign.Center, fontSize = 13.sp
            ),
        )

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium1))
    }
}

@Preview(showBackground = true, name = "Keysign Preview")
@Composable
fun PreviewKeysign() {
    Keysign(
        navController = rememberNavController(), viewModel = KeysignViewModel(
            vault = Vault("new vault"),
            keysignCommittee = listOf("device1", "device2", "device3"),
            serverAddress = "http://127.0.0.1:18080",
            sessionId = UUID.randomUUID().toString(),
            encryptionKeyHex = Utils.encryptionKeyHex,
            messagesToSign = listOf("message1", "message2", "message3"),
            keyType = TssKeyType.ECDSA,
            keysignPayload = KeysignPayload(
                coin = Coins.getCoin(
                    "RUNE",
                    "thor1q0j2z5x2g3v3x2z5x2g3v3x2z5x2g3v3x2z5x",
                    "vaultPublicKey",
                    coinType = CoinType.THORCHAIN
                )!!,
                toAddress = "",
                toAmount = BigInteger.ZERO,
                blockChainSpecific = BlockChainSpecific.THORChain(
                    BigInteger.valueOf(1024),
                    BigInteger.valueOf(0)
                ),
                utxos = emptyList(),
                vaultPublicKeyECDSA = ""
            ), gson = Gson()

        )
    )
}
