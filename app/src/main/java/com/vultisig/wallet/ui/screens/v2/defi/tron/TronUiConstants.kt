package com.vultisig.wallet.ui.screens.v2.defi.tron

import com.vultisig.wallet.ui.theme.v2.TronRed

internal val HIDE_BALANCE_CHARS = "• ".repeat(8).trim()

// Banner gradient and border are alpha variants of the Tron brand red (defined in Colors.kt)
internal val TronBannerGradientTop = TronRed.copy(alpha = 0.09f)
internal val TronBannerGradientBottom = TronRed.copy(alpha = 0f)
internal val TronBannerBorder = TronRed.copy(alpha = 0.17f)
