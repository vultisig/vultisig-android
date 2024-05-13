package com.vultisig.wallet.presenter.settings.faq_setting

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class FAQSettingViewModel @Inject constructor() : ViewModel() {

    val state = MutableStateFlow(
        FAQSettingUiModel(
            listOf(
                Question("How do I set up my crypto vault?"),
                Question("What cryptocurrencies are supported by Vultisig?"),
                Question("How does the crypto vault app secure my cryptocurrencies?"),
                Question("How does the crypto vault app secure my cryptocurrencies?"),
                Question("Can I recover my assets if I forget my password?"),
                Question("Are there any fees for using the crypto vault app?"),
            )
        )
    )
}