package com.vultisig.wallet.ui.screens.v2.defi.tron

import androidx.compose.ui.graphics.Color

internal val HIDE_BALANCE_CHARS = "• ".repeat(8).trim()

// Tron brand red — single source so the hex never drifts across derived tokens
private val TronRed = Color(0xFFFF060A)

// Banner gradient and border are alpha variants of the brand red
internal val TronBannerGradientTop = TronRed.copy(alpha = 0.09f)
internal val TronBannerGradientBottom = TronRed.copy(alpha = 0f)
internal val TronBannerBorder = TronRed.copy(alpha = 0.17f)
