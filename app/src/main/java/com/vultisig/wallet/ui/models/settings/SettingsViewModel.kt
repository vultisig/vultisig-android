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
import com.vultisig.wallet.data.repositories.PreventScreenshotsRepository
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepositoryContract
import com.vultisig.wallet.ui.models.settings.SettingsItem.AddressBook
import com.vultisig.wallet.ui.models.settings.SettingsItem.CheckForUpdates
import com.vultisig.wallet.ui.models.settings.SettingsItem.Currency
import com.vultisig.wallet.ui.models.settings.SettingsItem.Discord
import com.vultisig.wallet.ui.models.settings.SettingsItem.DiscountTiers
import com.vultisig.wallet.ui.models.settings.SettingsItem.Faq
import com.vultisig.wallet.ui.models.settings.SettingsItem.Github
import com.vultisig.wallet.ui.models.settings.SettingsItem.Language
import com.vultisig.wallet.ui.models.settings.SettingsItem.Notifications
import com.vultisig.wallet.ui.models.settings.SettingsItem.PreventScreenshots
import com.vultisig.wallet.ui.models.settings.SettingsItem.PrivacyPolicy
import com.vultisig.wallet.ui.models.settings.SettingsItem.ReferralCode
import com.vultisig.wallet.ui.models.settings.SettingsItem.ShareTheApp
import com.vultisig.wallet.ui.models.settings.SettingsItem.TermsOfService
import com.vultisig.wallet.ui.models.settings.SettingsItem.Twitter
import com.vultisig.wallet.ui.models.settings.SettingsItem.VaultSetting
import com.vultisig.wallet.ui.models.settings.SettingsItem.VultisigWebsite
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.VsAuxiliaryLinks
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class SettingsUiModel(
    val items: List<SettingsGroupUiModel>,
    val hasToShowReferralCodeSheet: Boolean = false,
    val showShareBottomSheet: Boolean = false,
)

internal data class SettingsGroupUiModel(val title: UiText, val items: List<SettingsItem>)

internal sealed class SettingsItem(val value: SettingsItemUiModel, val enabled: Boolean = true) {
    data object VaultSetting :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.vault_settings_title),
                leadingIcon = R.drawable.setting,
                trailingIcon = R.drawable.ic_small_caret_right,
            )
        )

    data object DiscountTiers :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.vault_settings_discounts),
                leadingIcon = R.drawable.coins_tier,
                trailingIcon = R.drawable.ic_small_caret_right,
            )
        )

    data class Language(val lang: String) :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.settings_screen_language),
                value = lang,
                leadingIcon = R.drawable.language,
                trailingIcon = R.drawable.ic_small_caret_right,
            )
        )

    data class Currency(val curr: String) :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.settings_screen_currency),
                value = curr,
                leadingIcon = R.drawable.currency,
                trailingIcon = R.drawable.ic_small_caret_right,
            )
        )

    data object AddressBook :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.address_book_settings_title),
                leadingIcon = R.drawable.address_book,
                trailingIcon = R.drawable.ic_small_caret_right,
            )
        )

    data object ReferralCode :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.referral_code_settings_title),
                leadingIcon = R.drawable.referral_code,
                trailingIcon = R.drawable.ic_small_caret_right,
            )
        )

    data object Faq :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.faq_setting_screen_title),
                leadingIcon = R.drawable.faq,
                trailingIcon = R.drawable.ic_small_caret_right,
            )
        )

    data object CheckForUpdates :
        SettingsItem(
            value =
                SettingsItemUiModel(
                    title = UiText.StringResource(R.string.check_updates_settings_title),
                    leadingIcon = R.drawable.check_update,
                    trailingIcon = R.drawable.ic_small_caret_right,
                ),
            enabled = true,
        )

    data object ShareTheApp :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.settings_screen_share_the_app),
                leadingIcon = R.drawable.share_app,
                trailingIcon = R.drawable.ic_small_caret_right,
            )
        )

    data object Twitter :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.x_twitter),
                leadingIcon = R.drawable.x_twitter,
                trailingIcon = R.drawable.ic_small_caret_right,
            )
        )

    data object Discord :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.discord),
                leadingIcon = R.drawable.discord,
                trailingIcon = R.drawable.ic_small_caret_right,
            )
        )

    data object Github :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.github),
                leadingIcon = R.drawable.githup,
                trailingIcon = R.drawable.ic_small_caret_right,
            )
        )

    data object VultisigWebsite :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.vult_website_settings_title),
                leadingIcon = R.drawable.vult_website,
                trailingIcon = R.drawable.ic_small_caret_right,
            )
        )

    data object TermsOfService :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.settings_screen_tos),
                leadingIcon = R.drawable.term_service,
                trailingIcon = R.drawable.ic_small_caret_right,
            )
        )

    data object PrivacyPolicy :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.settings_screen_privacy_policy),
                leadingIcon = R.drawable.security,
                trailingIcon = R.drawable.ic_small_caret_right,
            )
        )

    data object Notifications :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.notifications),
                leadingIcon = R.drawable.ic_bell,
                trailingIcon = R.drawable.ic_small_caret_right,
            )
        )

    data class PreventScreenshots(val isEnabled: Boolean = false) :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.settings_screen_prevent_screenshots),
                leadingIcon = R.drawable.security,
                trailingSwitch = isEnabled,
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

internal data class CurrencyUnit(val name: String = "", val fullName: String = "")

internal sealed interface SettingsUiEvent {
    data class OpenLink(val url: String) : SettingsUiEvent
}

@HiltViewModel
internal class SettingsViewModel
@Inject
constructor(
    private val navigator: Navigator<Destination>,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val appLocaleRepository: AppLocaleRepository,
    private val referralRepository: ReferralCodeSettingsRepositoryContract,
    private val preventScreenshotsRepository: PreventScreenshotsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiEvents = Channel<SettingsUiEvent>()
    val uiEvent = _uiEvents.receiveAsFlow()

    val settingsMenu =
        SettingsUiModel(
            items =
                listOf(
                    SettingsGroupUiModel(
                        title = UiText.StringResource(R.string.vault),
                        items = listOf(VaultSetting, DiscountTiers),
                    ),
                    SettingsGroupUiModel(
                        title = UiText.StringResource(R.string.general),
                        items =
                            listOf(
                                Notifications,
                                ReferralCode,
                                Language("English"),
                                Currency("USD"),
                                AddressBook,
                            ),
                    ),
                    SettingsGroupUiModel(
                        title = UiText.StringResource(R.string.settings_screen_privacy),
                        items = listOf(PreventScreenshots()),
                    ),
                    SettingsGroupUiModel(
                        title = UiText.StringResource(R.string.support),
                        items = listOf(Faq, CheckForUpdates, ShareTheApp),
                    ),
                    SettingsGroupUiModel(
                        title = UiText.StringResource(R.string.vultisig_community),
                        items = listOf(Twitter, Discord, Github, VultisigWebsite),
                    ),
                    SettingsGroupUiModel(
                        title = UiText.StringResource(R.string.settings_screen_legal),
                        items = listOf(PrivacyPolicy, TermsOfService),
                    ),
                )
        )

    val state = MutableStateFlow(settingsMenu)
    val vaultId = savedStateHandle.toRoute<Route.Settings>().vaultId
    private var hasUsedReferral = false

    fun onSettingsItemClick(item: SettingsItem) {
        when (item) {
            AddressBook -> {
                viewModelScope.launch {
                    navigator.route(Route.AddressBookScreen(vaultId = vaultId))
                }
            }

            CheckForUpdates -> {
                viewModelScope.launch { navigator.route(Route.CheckForUpdateSetting) }
            }
            is Currency -> {
                viewModelScope.launch { navigator.route(Route.CurrencyUnitSetting) }
            }

            Discord -> sendEvent(SettingsUiEvent.OpenLink(VsAuxiliaryLinks.DISCORD))
            Faq -> {
                viewModelScope.launch { navigator.route(Route.FAQSetting) }
            }
            Github -> sendEvent(SettingsUiEvent.OpenLink(VsAuxiliaryLinks.GITHUB))
            is Language -> {
                viewModelScope.launch { navigator.route(Route.LanguageSetting) }
            }

            PrivacyPolicy -> sendEvent(SettingsUiEvent.OpenLink(VsAuxiliaryLinks.PRIVACY))
            ReferralCode -> onClickReferralCode()
            ShareTheApp -> openShareLinkModalBottomSheet()
            TermsOfService -> sendEvent(SettingsUiEvent.OpenLink(VsAuxiliaryLinks.TERMS_OF_SERVICE))
            Twitter -> sendEvent(SettingsUiEvent.OpenLink(VsAuxiliaryLinks.TWITTER))
            VaultSetting -> {
                viewModelScope.launch { navigator.route(Route.VaultSettings(vaultId)) }
            }

            DiscountTiers -> {
                viewModelScope.launch { navigator.route(Route.DiscountTiers(vaultId)) }
            }

            Notifications -> {
                viewModelScope.launch { navigator.route(Route.NotificationSettings) }
            }

            VultisigWebsite -> sendEvent(SettingsUiEvent.OpenLink(VsAuxiliaryLinks.VULT_WEBSITE))

            is PreventScreenshots -> {
                viewModelScope.launch {
                    val newValue = !item.isEnabled
                    preventScreenshotsRepository.setEnabled(newValue)
                }
            }
        }
    }

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            loadCurrency()
            loadAppLocale()
            loadPreventScreenshots()
            loadWasReferralUsed()
        }
    }

    private fun loadWasReferralUsed() {
        viewModelScope.launch { hasUsedReferral = referralRepository.hasVisitReferralCode() }
    }

    private fun loadPreventScreenshots() {
        viewModelScope.launch {
            preventScreenshotsRepository.isEnabled.collect { isEnabled ->
                state.update { current ->
                    current.copy(
                        items =
                            current.items.map { group ->
                                group.copy(
                                    items =
                                        group.items.map { item ->
                                            when (item) {
                                                is PreventScreenshots ->
                                                    item.copy(isEnabled = isEnabled)
                                                else -> item
                                            }
                                        }
                                )
                            }
                    )
                }
            }
        }
    }

    private fun loadAppLocale() {
        viewModelScope.launch {
            appLocaleRepository.local.collect { locale: AppLanguage ->
                val items = updatedLocale(locale)
                state.update { it.copy(items = items) }
            }
        }
    }

    private fun updatedLocale(locale: AppLanguage) =
        state.value.items.map { group ->
            group.copy(
                items =
                    group.items.map { item ->
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
                state.update { it.copy(items = items) }
            }
        }
    }

    private fun updateCurrency(currency: AppCurrency) =
        state.value.items.map { group ->
            group.copy(
                items =
                    group.items.map { item ->
                        when (item) {
                            is Currency -> item.copy(curr = currency.ticker)
                            else -> item
                        }
                    }
            )
        }

    private fun navigateTo(destination: Destination) {
        viewModelScope.launch { navigator.navigate(destination) }
    }

    private fun sendEvent(event: SettingsUiEvent) {
        viewModelScope.launch { _uiEvents.send(event) }
    }

    fun back() {
        viewModelScope.launch { navigator.back() }
    }

    fun onContinueReferralBottomSheet() {
        viewModelScope.launch {
            referralRepository.visitReferralCode()
            referralRepository.setAsShown()
            state.update { it.copy(hasToShowReferralCodeSheet = false) }
            viewModelScope.launch { navigator.route(Route.ReferralOnboarding(vaultId)) }
        }
    }

    fun onClickReferralCode() {
        viewModelScope.launch {
            if (hasUsedReferral || referralRepository.isShown()) {
                navigateTo(Destination.ReferralCode(vaultId))
            } else {
                state.update { it.copy(hasToShowReferralCodeSheet = true) }
            }
        }
    }

    fun onDismissReferralBottomSheet() {
        viewModelScope.launch {
            referralRepository.setAsShown()
            state.update { it.copy(hasToShowReferralCodeSheet = false) }
        }
    }

    fun onShareVaultQrClick() {
        viewModelScope.launch { navigator.route(Route.ShareVaultQr(vaultId)) }
    }

    fun onDismissShareLinkBottomSheet() {
        state.update { it.copy(showShareBottomSheet = false) }
    }

    private fun openShareLinkModalBottomSheet() {
        state.update { it.copy(showShareBottomSheet = true) }
    }
}
