package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.AppCurrency
import com.vultisig.wallet.data.models.AppLanguage
import com.vultisig.wallet.models.Chain


internal val CURRENCY_LIST: List<AppCurrency> = AppCurrency.entries.toList()
internal val LOCALE_LIST: List<AppLanguage> = AppLanguage.entries.toList()
internal val DEFAULT_CHAINS_LIST: List<Chain> =
    listOf(Chain.thorChain, Chain.bitcoin, Chain.bscChain, Chain.ethereum, Chain.solana)
