package com.voltix.wallet.presenter.pair

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.voltix.wallet.R
import com.voltix.wallet.app.ui.theme.appColor
import com.voltix.wallet.app.ui.theme.dimens
import com.voltix.wallet.presenter.common.TopBar
@Composable
fun Pair(navController: NavHostController) {
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier.run {
            background(MaterialTheme.appColor.oxfordBlue800)
                .padding(vertical = MaterialTheme.dimens.marginMedium,
                    horizontal = MaterialTheme.dimens.marginSmall)
        }
    ) {
        TopBar(centerText = "Pair", startIcon = R.drawable.caret_left)

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium1))
        Box(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxSize()
                .background(MaterialTheme.appColor.neutral900)
        ) {
            Box(modifier = Modifier
                .align(Center)
                .width(250.dp)
                .height(250.dp)
                .clip(RoundedCornerShape(MaterialTheme.dimens.medium1))

                .drawBehind {
                    drawRoundRect(
                        color = Color("#33e6bf".toColorInt()), style = Stroke(
                            width = 8f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(50f, 50f), 0f)
                        ), cornerRadius = CornerRadius(16.dp.toPx())
                    )
                })
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PairPreview() {
    val navController = rememberNavController()
    Pair( navController)

}