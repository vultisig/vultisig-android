package com.vultisig.wallet.data.models


internal sealed class AppCurrency(
    val ticker: String,
) {
    data object USD : AppCurrency(
        ticker = "USD",
    )

    data object AUD : AppCurrency(
        ticker = "AUD",
    )
}