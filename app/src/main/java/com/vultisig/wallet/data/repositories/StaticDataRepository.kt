package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.AppCurrency
import com.vultisig.wallet.data.models.AppLanguage


internal val CURRENCY_LIST: List<AppCurrency> = AppCurrency.entries.toList()
internal val LOCAL_LIST: List<AppLanguage> = AppLanguage.entries.toList()
