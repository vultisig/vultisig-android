package com.vultisig.wallet.presenter.settings.faq_setting

data class FAQSettingUiModel (val questions: List<Question>)

data class Question(val text: String)