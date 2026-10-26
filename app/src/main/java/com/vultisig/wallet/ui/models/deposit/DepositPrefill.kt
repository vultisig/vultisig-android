package com.vultisig.wallet.ui.models.deposit

/** Values a deeplink fills into the Custom deposit form; null fields stay empty. */
internal data class DepositPrefill(val ticker: String?, val amount: String?, val memo: String?)
