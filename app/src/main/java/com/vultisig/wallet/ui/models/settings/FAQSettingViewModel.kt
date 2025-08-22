package com.vultisig.wallet.ui.models.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import com.vultisig.wallet.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

internal data class FAQSettingUiModel (val questions: List<Faq>)

internal data class Faq(val question: String,val answer:String)

@HiltViewModel
internal class FAQSettingViewModel @Inject constructor(@ApplicationContext context: Context) : ViewModel() {

    val state: MutableStateFlow<FAQSettingUiModel> = MutableStateFlow(
        FAQSettingUiModel(
            listOf(
                Faq(
                    question = "What is Vultisig?",
                    answer = "It is a secure, multi-authentication wallet based on MPC technology that is used to manage digital assets. Transactions require approval from multiple devices."
                ),
                Faq(
                    question = "What are the benefits of using Vultisig?",
                    answer = "Vultisig offers enhanced security with multi-device authentication, support for many blockchains, easy recovery options, and no seed phrases or user tracking.",
                ),
                Faq(
                    question = "Can I recover my assets if I lose a device?",
                    answer = "Yes, as long as you saved and have access to your backups when creating the vault. You can import these backups on a new device to regain access to your assets.",
                ),
                Faq(
                    question = "How is Vultisig used?",
                    answer = "Vultisig securely stores and manages digital assets. All actions, such as sending or swapping, require the threshold of devices to sign transactions."
                ),
                Faq(
                    question = "What are the fees and costs?",
                    answer = "Vultisig is free to use. Only standard network fees apply to sending. And for swaps and bridges, there's a 0.5% (50 bps) fee."
                ),
                Faq(
                    question = "What cryptocurrencies are supported by Vultisig?",
                    answer = "Vultisig supports major cryptocurrencies and tokens, with over 30 chains and their tokens, currently available."
                ),
                Faq(
                    question = "Is Vultisig open source and audited?",
                    answer = "Yes, Vultisig is open source and has undergone security audits. Both the audit reports and the source code are accessible."
                ),
                Faq(
                    question = "How does Vultisig handle privacy and data protection?",
                    answer = "Vultisig does not store any user information from its mobile apps.",
                ),
                Faq(
                    question = "How does Vultisig compare to other multisig wallets?",
                    answer = "It is built on MPC technology, which eliminates the need for seed phrases and supports multiple blockchains, making Vultisig flexible and chain-agnostic."
                )
            )
        )
    )
}