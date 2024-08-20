package com.vultisig.wallet.presenter.settings.faq_setting

import android.content.Context
import androidx.lifecycle.ViewModel
import com.vultisig.wallet.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class FAQSettingViewModel @Inject constructor(@ApplicationContext context: Context) : ViewModel() {

    val state: MutableStateFlow<FAQSettingUiModel> = MutableStateFlow(
        FAQSettingUiModel(
            listOf(
                Faq(
                    context.getString(R.string.faq_settings_q1),
                    context.getString(R.string.faq_settings_a1)
                ),
                Faq(
                    context.getString(R.string.faq_settings_q2),
                    context.getString(R.string.faq_settings_a2)
                ),
                Faq(
                    context.getString(R.string.faq_settings_q3),
                    context.getString(R.string.faq_settings_a3)
                ),
                Faq(
                    context.getString(R.string.faq_settings_q4),
                    context.getString(R.string.faq_settings_a4)
                ),
                Faq(
                    context.getString(R.string.faq_settings_q5),
                    context.getString(R.string.faq_settings_a5)
                ),
                Faq(
                    context.getString(R.string.faq_settings_q6),
                    context.getString(R.string.faq_settings_a6)
                ),
            )
        )
    )
}