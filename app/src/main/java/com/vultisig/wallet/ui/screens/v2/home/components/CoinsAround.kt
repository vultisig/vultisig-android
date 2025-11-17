package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R

@Composable
internal fun BoxScope.CoinsAround() {
    Image(
        painter = painterResource(R.drawable.btc_defi_banner),
        contentDescription = null,
        modifier = Modifier
            .align(
                alignment = Alignment.TopEnd
            )
            .offset(
                x = (16).dp,
                y = (-22).dp
            )
    )

    Image(
        painter = painterResource(R.drawable.bnb_defi_banner),
        contentDescription = null,
        modifier = Modifier
            .align(
                alignment = Alignment.TopStart
            )
            .offset(
                x = (12).dp,
                y = (-9).dp
            )
    )

    Image(
        painter = painterResource(R.drawable.sol_defi_banner),
        contentDescription = null,
        modifier = Modifier
            .align(
                alignment = Alignment.TopStart
            )
            .offset(
                x = (-26).dp,
                y = (9).dp
            )
    )
    Image(
        painter = painterResource(R.drawable.eth_defi_banner),
        contentDescription = null,
        modifier = Modifier
            .align(
                alignment = Alignment.BottomStart
            )
            .offset(
                x = (4).dp,
                y = (14).dp
            )
    )

    Image(
        painter = painterResource(R.drawable.xrp_defi_banner),
        contentDescription = null,
        modifier = Modifier
            .align(
                alignment = Alignment.BottomEnd
            )
            .offset(
                x = (-4).dp,
                y = (-2).dp
            )
    )
}