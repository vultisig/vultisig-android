package com.vultisig.wallet.presenter.settings.faq_setting

data class FAQSettingUiModel (val questions: List<Faq>)

data class Faq(val question: String,val answer:String)