package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.models.settings.AppLanguage


internal val CURRENCY_LIST: List<AppCurrency> = AppCurrency.entries.toList()
val LOCALE_LIST: List<AppLanguage> = AppLanguage.entries.toList()
