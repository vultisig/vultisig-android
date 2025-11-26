package com.vultisig.wallet.ui.models.settings

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.models.settings.AppLanguage
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.AppLocaleRepository
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepositoryContract
import com.vultisig.wallet.ui.models.settings.SettingsItem.*
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.theme.Colors
import com.vultisig.wallet.ui.utils.UiText
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
    val showShareBottomSheet: Boolean = false,
)

internal data class SettingsGroupUiModel(
    val title: UiText,
    val items: List<SettingsItem>
)

internal sealed class SettingsItem(val value: SettingsItemUiModel, val enabled: Boolean = true) {
    data object VaultSetting : SettingsItem(
        SettingsItemUiModel(
            title = UiText.StringResource(R.string.vault_settings_title),
            leadingIcon = R.drawable.setting,
            trailingIcon = R.drawable.ic_small_caret_right
        )
    )

    data object DiscountTiers : SettingsItem(
        SettingsItemUiModel(
            title = UiText.StringResource(R.string.vault_settings_discounts),
            leadingIcon = R.drawable.coins_tier,
            trailingIcon = R.drawable.ic_small_caret_right
        )
    )

    data object RegisterVault : SettingsItem(
        SettingsItemUiModel(
            title = UiText.StringResource(R.string.settings_screen_register_your_vaults),
            backgroundColor = Colors.Default.buttons.primary,
            leadingIcon = R.drawable.register,
            trailingIcon = R.drawable.ic_small_caret_right,
            leadingIconTint =  Colors.Default.text.primary,
        )
    )

    data class Language(val lang: String) :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.settings_screen_language),
                value = lang,
                leadingIcon = R.drawable.language,
                trailingIcon = R.drawable.ic_small_caret_right
            )
        )

    data class Currency(val curr: String) :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.settings_screen_currency),
                value = curr,
                leadingIcon = R.drawable.currency,
                trailingIcon = R.drawable.ic_small_caret_right
            )
        )

    data object AddressBook : SettingsItem(
        SettingsItemUiModel(
            title = UiText.StringResource(R.string.address_book_settings_title),
            leadingIcon = R.drawable.address_book,
            trailingIcon = R.drawable.ic_small_caret_right
        )
    )

    data object ReferralCode : SettingsItem(
        SettingsItemUiModel(
            title = UiText.StringResource(R.string.referral_code_settings_title),
            leadingIcon = R.drawable.referral_code,
            trailingIcon = R.drawable.ic_small_caret_right
        ),
    )

    data object Faq : SettingsItem(
        SettingsItemUiModel(
            title = UiText.StringResource(R.string.faq_setting_screen_title),
            leadingIcon = R.drawable.faq,
            trailingIcon = R.drawable.ic_small_caret_right
        )
    )

    data object VultisigEducation :
        SettingsItem(
            value = SettingsItemUiModel(
                leadingIcon = R.drawable.vult_education,
                title = UiText.StringResource(R.string.education_settings_title),
                trailingIcon = R.drawable.ic_small_caret_right
            ),
            enabled = false
        )

    data object CheckForUpdates : SettingsItem(
        value = SettingsItemUiModel(
            title = UiText.StringResource(R.string.check_updates_settings_title),
            leadingIcon = R.drawable.check_update,
            trailingIcon = R.drawable.ic_small_caret_right
        ),
        enabled = true
    )

    data object ShareTheApp :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.settings_screen_share_the_app),
                leadingIcon = R.drawable.share_app,
                trailingIcon = R.drawable.ic_small_caret_right
            )
        )

    data object Twitter : SettingsItem(
        SettingsItemUiModel(
            title = UiText.StringResource(R.string.x_twitter),
            leadingIcon = R.drawable.x_twitter,
            trailingIcon = R.drawable.ic_small_caret_right
        )
    )

    data object Discord :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.discord),
                leadingIcon = R.drawable.discord,
                trailingIcon = R.drawable.ic_small_caret_right
            )
        )

    data object Vult :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.vult),
                leadingIcon = R.drawable.vult,
                trailingIcon = R.drawable.ic_small_caret_right
            )
        )

    data object Github :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.github),
                leadingIcon = R.drawable.githup,
                trailingIcon = R.drawable.ic_small_caret_right
            )
        )

    data object VultisigWebsite :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.vult_website_settings_title),
                leadingIcon = R.drawable.vult_website,
                trailingIcon = R.drawable.ic_small_caret_right
            )
        )

    data object TermsOfService :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.settings_screen_tos),
                leadingIcon = R.drawable.term_service,
                trailingIcon = R.drawable.ic_small_caret_right
            )
        )

    data object PrivacyPolicy :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.settings_screen_privacy_policy),
                leadingIcon = R.drawable.security,
                trailingIcon = R.drawable.ic_small_caret_right
            )
        )
}

internal data class SettingsItemUiModel(
    val title: UiText,
    val subTitle: UiText? = null,
    val leadingIcon: Int? = null,
    val trailingIcon: Int? = null,
    val leadingIconTint: Color? = null,
    val trailingSwitch: Boolean? = null,
    val value: String? = null,
    val backgroundColor: Color? = null,
)


internal data class CurrencyUnit(
    val name: String = "",
    val fullName: String = "",
)

internal sealed interface SettingsUiEvent {
    data class OpenLink(val url: String) : SettingsUiEvent
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
                title = UiText.StringResource(R.string.vault),
                items = listOf(
                    VaultSetting,
                    DiscountTiers,
                    RegisterVault
                )
            ),
            SettingsGroupUiModel(
                title = UiText.StringResource(R.string.general),
                items = listOf(
                    Language("English"),
                    Currency("USD"),
                    AddressBook,
                    ReferralCode
                )
            ),
            SettingsGroupUiModel(
                title = UiText.StringResource(R.string.support),
                items = listOf(
                    Faq,
                    VultisigEducation,
                    CheckForUpdates,
                    ShareTheApp,
                )
            ),
            SettingsGroupUiModel(
                title = UiText.StringResource(R.string.vultisig_community),
                items = listOf(
                    Twitter,
                    Vult,
                    Discord,
                    Github,
                    VultisigWebsite
                )
            ),
            SettingsGroupUiModel(
                title = UiText.StringResource(R.string.settings_screen_legal),
                items = listOf(
                    PrivacyPolicy,
                    TermsOfService
                )
            )
        ),
    )

    val state = MutableStateFlow(settingsMenu)
    val vaultId = savedStateHandle.toRoute<Route.Settings>().vaultId
    private var hasUsedReferral = false


    fun onSettingsItemClick(item: SettingsItem) {
        when (item) {
            AddressBook -> {
                viewModelScope.launch {
                    navigator.route(
                        Route.AddressBookScreen(vaultId = vaultId)
                    )
                }
            }

            CheckForUpdates -> {
                viewModelScope.launch {
                    navigator.route(
                        Route.CheckForUpdateSetting
                    )
                }
            }
            is Currency -> {
                viewModelScope.launch {
                    navigator.route(
                        Route.CurrencyUnitSetting
                    )
                }
            }

            Discord -> sendEvent(SettingsUiEvent.OpenLink(VsAuxiliaryLinks.DISCORD))
            Vult -> sendEvent(SettingsUiEvent.OpenLink(VsAuxiliaryLinks.VULT_TOKEN))
            Faq -> {
                viewModelScope.launch {
                    navigator.route(
                        Route.FAQSetting
                    )
                }
            }
            Github -> sendEvent(SettingsUiEvent.OpenLink(VsAuxiliaryLinks.GITHUB))
            is Language -> {
                viewModelScope.launch {
                    navigator.route(
                        Route.LanguageSetting
                    )
                }
            }

            PrivacyPolicy -> sendEvent(SettingsUiEvent.OpenLink(VsAuxiliaryLinks.PRIVACY))
            ReferralCode -> onClickReferralCode()
            RegisterVault -> {
                viewModelScope.launch {
                    navigator.route(
                        Route.RegisterVault(vaultId)
                    )
                }
            }
            ShareTheApp -> openShareLinkModalBottomSheet()
            TermsOfService -> sendEvent(SettingsUiEvent.OpenLink(VsAuxiliaryLinks.TERMS_OF_SERVICE))
            Twitter -> sendEvent(SettingsUiEvent.OpenLink(VsAuxiliaryLinks.TWITTER))
            VaultSetting -> {
                viewModelScope.launch {
                    navigator.route(
                        Route.VaultSettings(vaultId)
                    )
                }
            }

            VultisigEducation -> {
                // TODO: Implement education section navigation
            }

            DiscountTiers -> {
                viewModelScope.launch {
                    navigator.route(
                        Route.DiscountTiers(vaultId)
                    )
                }
            }

            VultisigWebsite -> sendEvent(SettingsUiEvent.OpenLink(VsAuxiliaryLinks.VULT_WEBSITE))
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
                    is Currency -> item.copy(curr = currency.ticker)
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

    fun back() {
        viewModelScope.launch {
            navigator.back()
        }
    }



    fun onContinueReferralBottomSheet() {
        viewModelScope.launch {
            referralRepository.visitReferralCode()
            state.update {
                it.copy(hasToShowReferralCodeSheet = false)
            }
            viewModelScope.launch {
                navigator.route(
                    Route.ReferralOnboarding(vaultId)
                )
            }
        }
    }

    fun onClickReferralCode() {
        viewModelScope.launch {
            if (hasUsedReferral) {
                navigateTo(Destination.ReferralCode(vaultId))
            } else {
                state.update {
                    it.copy(hasToShowReferralCodeSheet = !hasUsedReferral)
                }
            }
        }
    }

    fun onDismissReferralBottomSheet() {
        viewModelScope.launch {
            state.update {
                it.copy(hasToShowReferralCodeSheet = false)
            }
        }
    }

    fun onShareVaultQrClick(){
        viewModelScope.launch {
            navigator.route(
                Route.ShareVaultQr(vaultId)
            )
        }
    }

    fun onDismissShareLinkBottomSheet(){
        state.update {
            it.copy(showShareBottomSheet = false)
        }
    }

    private fun openShareLinkModalBottomSheet() {
        state.update {
            it.copy(showShareBottomSheet = true)
        }
    }
}