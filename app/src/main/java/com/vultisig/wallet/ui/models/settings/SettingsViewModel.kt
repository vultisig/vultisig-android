package com.vultisig.wallet.ui.models.settings

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.models.settings.AppLanguage
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.AppLocaleRepository
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepositoryContract
import com.vultisig.wallet.ui.models.settings.SettingsItem.*
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.theme.Colors
import com.vultisig.wallet.ui.utils.MultipleClicksDetector
import com.vultisig.wallet.ui.utils.VsAuxiliaryLinks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class SettingsUiModel(
    val items: List<SettingsGroupUiModel>,
    val hasToShowReferralCodeSheet: Boolean = false,
)

internal data class SettingsGroupUiModel(
    val title: String,
    val items: List<SettingsItem>
)

internal sealed class SettingsItem(val value: SettingsItemUiModel) {
    data object VaultSetting : SettingsItem(SettingsItemUiModel(title = "Vault Settings"))
    data object RegisterVault : SettingsItem(
        SettingsItemUiModel(
            title = "Register your Vaults!",
            backgroundColor = Colors.Default.buttons.primary
        )
    )

    data class Language(val lang: String) :
        SettingsItem(SettingsItemUiModel(title = "Language", value = lang))

    data class Currency(val curr: String) :
        SettingsItem(SettingsItemUiModel(title = "Currency", value = curr))

    data object AddressBook : SettingsItem(SettingsItemUiModel(title = "Address Book"))
    data object ReferralCode : SettingsItem(SettingsItemUiModel(title = "Referral Code"))
    data object Faq : SettingsItem(SettingsItemUiModel(title = "FAQ"))
    data object VultisigEducation :
        SettingsItem(SettingsItemUiModel(icon = null, title = "Vultisig Education"))

    data object CheckForUpdates : SettingsItem(SettingsItemUiModel("Check for updates"))
    data object ShareTheApp : SettingsItem(SettingsItemUiModel("Share The App"))
    data object Twitter : SettingsItem(SettingsItemUiModel("X"))
    data object Discord : SettingsItem(SettingsItemUiModel("Discord"))
    data object Github : SettingsItem(SettingsItemUiModel("Github"))
    data object VultisigWebsite : SettingsItem(SettingsItemUiModel("Vultisig Website"))
    data object TermsOfService : SettingsItem(SettingsItemUiModel("Terms of Service"))
    data object PrivacyPolicy : SettingsItem(SettingsItemUiModel("Privacy Policy"))
}

internal data class SettingsItemUiModel(
    val title: String,
    val icon: Int? = null,
    val value: String? = null,
    val backgroundColor: Color? = null,
)


internal data class CurrencyUnit(
    val name: String = "",
)

internal sealed interface SettingsUiEvent {
    data class OpenLink(val url: String) : SettingsUiEvent
    data object OpenGooglePlay : SettingsUiEvent
}

@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val appLocaleRepository: AppLocaleRepository,
    private val referralRepository: ReferralCodeSettingsRepositoryContract,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiEvents = Channel<SettingsUiEvent>()
    val uiEvent = _uiEvents.receiveAsFlow()

    val settingsMenu = SettingsUiModel(
        items = listOf(
            SettingsGroupUiModel(
                title = "Vault",
                items = listOf(
                    VaultSetting,
                    RegisterVault
                )
            ),
            SettingsGroupUiModel(
                title = "General",
                items = listOf(
                    Language("English"),
                    Currency("USD"),
                    AddressBook,
                    ReferralCode
                )
            ),
            SettingsGroupUiModel(
                title = "Support",
                items = listOf(
                    Faq,
                    VultisigEducation,
                    CheckForUpdates,
                    ShareTheApp,
                )
            ),
            SettingsGroupUiModel(
                title = "Vultisig Community",
                items = listOf(
                    Twitter,
                    Discord,
                    Github,
                    VultisigWebsite
                )
            ),
            SettingsGroupUiModel(
                title = "Legal",
                items = listOf(
                    PrivacyPolicy,
                    TermsOfService
                )
            )
        ),
    )

    val state = MutableStateFlow(settingsMenu)
    val vaultId = savedStateHandle.get<String>(Destination.Settings.ARG_VAULT_ID)!!
    private var hasUsedReferral = false

    private val multipleClicksDetector = MultipleClicksDetector()

    fun onSettingsItemClick(item: SettingsItem) {
        when (item) {
            AddressBook -> {
                navigateTo(Destination.AddressBook())
            }

            CheckForUpdates -> TODO()
            is Currency -> {
                navigateTo(Destination.CurrencyUnitSetting)
            }

            Discord -> sendEvent(SettingsUiEvent.OpenLink(VsAuxiliaryLinks.DISCORD))
            Faq -> navigateTo(Destination.FAQSetting)
            Github -> sendEvent(SettingsUiEvent.OpenLink(VsAuxiliaryLinks.GITHUB))
            is Language -> {
                navigateTo(Destination.LanguageSetting)
            }

            PrivacyPolicy -> sendEvent(SettingsUiEvent.OpenLink(VsAuxiliaryLinks.PRIVACY))
            ReferralCode -> onClickReferralCode()
            RegisterVault -> navigateTo(Destination.RegisterVault(vaultId))
            ShareTheApp -> sendEvent(SettingsUiEvent.OpenGooglePlay)
            TermsOfService -> sendEvent(SettingsUiEvent.OpenLink(VsAuxiliaryLinks.TERMS_OF_SERVICE))
            Twitter -> sendEvent(SettingsUiEvent.OpenLink(VsAuxiliaryLinks.TWITTER))
            VaultSetting -> {
                navigateTo(Destination.VaultSettings(vaultId))
            }

            VultisigEducation -> TODO()
            VultisigWebsite -> TODO()
        }
    }

    init {
        loadSettings()
    }


    fun loadSettings() {
        viewModelScope.launch {
            loadCurrency()
            loadAppLocale()
            loadWasReferralUsed()
        }
    }

    private fun loadWasReferralUsed() {
        viewModelScope.launch {
            hasUsedReferral = referralRepository.hasVisitReferralCode()
        }
    }

    private fun loadAppLocale() {
        viewModelScope.launch {
            appLocaleRepository.local.collect { locale: AppLanguage ->
                val items = updatedLocale(locale)
                state.update {
                    it.copy(items = items)
                }
            }
        }
    }

    private fun updatedLocale(
        locale: AppLanguage
    ) = state.value.items.map { group ->
        group.copy(
            items = group.items.map { item ->
                when (item) {
                    is Language -> item.copy(lang = locale.mainName)
                    else -> item
                }
            }
        )
    }


    private fun loadCurrency() {
        viewModelScope.launch {
            appCurrencyRepository.currency.collect { currency: AppCurrency ->
                val items = updateCurrency(currency)
                state.update {
                    it.copy(items = items)
                }
            }
        }
    }

    private fun updateCurrency(
        currency: AppCurrency
    ) = state.value.items.map { group ->
        group.copy(
            items = group.items.map { item ->
                when (item) {
                    is Currency -> item.copy(curr = currency.name)
                    else -> item
                }
            }
        )
    }

    private fun navigateTo(destination: Destination) {
        viewModelScope.launch {
            navigator.navigate(destination)
        }
    }

    private fun sendEvent(event: SettingsUiEvent) {
        viewModelScope.launch {
            _uiEvents.send(event)
        }
    }

    fun clickSecret() {
        if (multipleClicksDetector.clickAndCheckIfDetected()) {
            viewModelScope.launch {
                navigator.route(Route.Secret)
            }
        }
    }

    fun onContinueReferralBottomSheet() {
        referralRepository.visitReferralCode()
        state.update {
            it.copy(hasToShowReferralCodeSheet = false)
        }
        navigateTo(Destination.ReferralOnboarding(vaultId))
    }

    fun onClickReferralCode() {
        if (hasUsedReferral) {
            navigateTo(Destination.ReferralCode(vaultId))
        } else {
            state.update {
                it.copy(hasToShowReferralCodeSheet = !hasUsedReferral)
            }
        }
    }

    fun onDismissReferralBottomSheet() {
        state.update {
            it.copy(hasToShowReferralCodeSheet = false)
        }
    }
}