package com.vultisig.wallet.ui.models.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import com.vultisig.wallet.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

internal data class FAQSettingUiModel(val questions: List<Faq>)

internal data class Faq(val id: Int, val question: String, val answer: String)

@HiltViewModel
internal class FAQSettingViewModel @Inject constructor(@ApplicationContext context: Context) :
    ViewModel() {

    val state: MutableStateFlow<FAQSettingUiModel> =
        MutableStateFlow(
            FAQSettingUiModel(
                listOf(
                    Faq(
                        id = R.string.faq_settings_q1,
                        question = context.getString(R.string.faq_settings_q1),
                        answer = context.getString(R.string.faq_settings_a1),
                    ),
                    Faq(
                        id = R.string.faq_settings_q2,
                        question = context.getString(R.string.faq_settings_q2),
                        answer = context.getString(R.string.faq_settings_a2),
                    ),
                    Faq(
                        id = R.string.faq_settings_q3,
                        question = context.getString(R.string.faq_settings_q3),
                        answer = context.getString(R.string.faq_settings_a3),
                    ),
                    Faq(
                        id = R.string.faq_settings_q4,
                        question = context.getString(R.string.faq_settings_q4),
                        answer = context.getString(R.string.faq_settings_a4),
                    ),
                    Faq(
                        id = R.string.faq_settings_q5,
                        question = context.getString(R.string.faq_settings_q5),
                        answer = context.getString(R.string.faq_settings_a5),
                    ),
                    Faq(
                        id = R.string.faq_settings_q6,
                        question = context.getString(R.string.faq_settings_q6),
                        answer = context.getString(R.string.faq_settings_a6),
                    ),
                    Faq(
                        id = R.string.faq_settings_q7,
                        question = context.getString(R.string.faq_settings_q7),
                        answer = context.getString(R.string.faq_settings_a7),
                    ),
                    Faq(
                        id = R.string.faq_settings_q8,
                        question = context.getString(R.string.faq_settings_q8),
                        answer = context.getString(R.string.faq_settings_a8),
                    ),
                    Faq(
                        id = R.string.faq_settings_q9,
                        question = context.getString(R.string.faq_settings_q9),
                        answer = context.getString(R.string.faq_settings_a9),
                    ),
                )
            )
        )
}
