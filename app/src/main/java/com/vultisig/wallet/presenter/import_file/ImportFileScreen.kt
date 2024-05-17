package com.vultisig.wallet.presenter.import_file

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.presenter.import_file.ImportFileEvent.FileSelected
import com.vultisig.wallet.presenter.import_file.ImportFileEvent.OnContinueClick
import com.vultisig.wallet.presenter.import_file.ImportFileEvent.RemoveSelectedFile
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.dimens

@Composable
fun ImportFileScreen(navController: NavHostController) {

    val viewmodel = hiltViewModel<ImportFileViewModel>()
    val uiModel by viewmodel.uiModel.collectAsState()
    val appColor = Theme.colors
    val menloFamily = Theme.menlo
    val montserratFamily = Theme.montserrat

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            viewmodel.onEvent(FileSelected(uri))
        }


    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(appColor.oxfordBlue800)
            .padding(
                vertical = MaterialTheme.dimens.marginMedium,
                horizontal = MaterialTheme.dimens.marginExtraLarge
            )
    ) {
        TopBar(centerText = "Import", navController = navController)
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "Enter your previously created vault share",
            color = appColor.neutral0,
            style = menloFamily.bodyMedium
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small3))
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(MaterialTheme.dimens.small2))
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
            .clickable {
                launcher.launch("application/octet-stream")
            }
            .drawBehind {
                drawRoundRect(
                    color = Color("#33e6bf".toColorInt()), style = Stroke(
                        width = 8f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(40f, 20f), 0f)
                    ), cornerRadius = CornerRadius(16.dp.toPx())
                )
            }

        ) {
            Column(Modifier.align(Center), horizontalAlignment = CenterHorizontally) {
                Image(
                    painterResource(id = R.drawable.file), contentDescription = "file"
                )
                Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium1))
                Text(
                    text = "Upload file, text or image",
                    color = appColor.neutral0,
                    style = menloFamily.body3
                )
            }
        }
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium1))
        if (uiModel.fileName != null)
            Row(verticalAlignment = CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.file),
                    contentDescription = "file icon",
                    modifier = Modifier.width(MaterialTheme.dimens.medium1)
                )
                Spacer(modifier = Modifier.width(MaterialTheme.dimens.small1))
                Text(
                    text = uiModel.fileName!!,
                    color = appColor.neutral0,
                    style = menloFamily.bodyMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                IconButton(onClick = {
                    viewmodel.onEvent(RemoveSelectedFile)
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.x),
                        contentDescription = "X",
                        tint = appColor.neutral0
                    )
                }

            }
        Spacer(modifier = Modifier.weight(1.0F))
        Button(
            onClick = {
                viewmodel.onEvent(OnContinueClick)
            },
            enabled = uiModel.fileName != null,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors()
                .copy(disabledContainerColor = MaterialTheme.colorScheme.surfaceDim),
        ) {
            Text(
                text = "Continue",
                color = MaterialTheme.colorScheme.onPrimary,
                style = montserratFamily.titleMedium,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ImportFilePreview() {
    val navController = rememberNavController()
    ImportFileScreen(navController)

}