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
import com.vultisig.wallet.ui.models.settings.SettingsItem.AddressBook
import com.vultisig.wallet.ui.models.settings.SettingsItem.CheckForUpdates
import com.vultisig.wallet.ui.models.settings.SettingsItem.Currency
import com.vultisig.wallet.ui.models.settings.SettingsItem.Discord
import com.vultisig.wallet.ui.models.settings.SettingsItem.Education
import com.vultisig.wallet.ui.models.settings.SettingsItem.DiscountTiers
import com.vultisig.wallet.ui.models.settings.SettingsItem.Faq
import com.vultisig.wallet.ui.models.settings.SettingsItem.Github
import com.vultisig.wallet.ui.models.settings.SettingsItem.HomeDisplay
import com.vultisig.wallet.ui.models.settings.SettingsItem.Language
import com.vultisig.wallet.ui.models.settings.SettingsItem.Notifications
import com.vultisig.wallet.ui.models.settings.SettingsItem.PrivacyPolicy
import com.vultisig.wallet.ui.models.settings.SettingsItem.RateTheApp
import com.vultisig.wallet.ui.models.settings.SettingsItem.ShareTheApp
import com.vultisig.wallet.ui.models.settings.SettingsItem.TermsOfService
import com.vultisig.wallet.ui.models.settings.SettingsItem.Twitter
import com.vultisig.wallet.ui.models.settings.SettingsItem.VaultSetting
import com.vultisig.wallet.ui.models.settings.SettingsItem.VultisigWebsite
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.MultipleClicksDetector
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.VsAuxiliaryLinks
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.TimeSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class SettingsUiModel(
    val items: List<SettingsGroupUiModel>,
    val showShareBottomSheet: Boolean = false,
)

/** A titled settings section: optional link [tiles] above a card of [items]. */
internal data class SettingsGroupUiModel(
    val title: UiText,
    val items: List<SettingsItem>,
    val tiles: List<SettingsItem> = emptyList(),
)

internal sealed class SettingsItem(val value: SettingsItemUiModel, val enabled: Boolean = true) {
    data object VaultSetting :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.vault_settings_title),
                leadingIcon = R.drawable.slider,
                trailingIcon = R.drawable.icon_chevron_right_small,
            )
        )

    data object DiscountTiers :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.vault_settings_discounts),
                leadingIcon = R.drawable.cards_filled,
                trailingIcon = R.drawable.icon_chevron_right_small,
            )
        )

    data class Language(val lang: String) :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.settings_screen_language),
                value = lang,
                leadingIcon = R.drawable.language,
                trailingIcon = R.drawable.icon_chevron_right_small,
            )
        )

    data class Currency(val curr: String) :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.settings_screen_currency),
                value = curr,
                leadingIcon = R.drawable.circle_dollar,
                trailingIcon = R.drawable.icon_chevron_right_small,
            )
        )

    data object HomeDisplay :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.home_display),
                leadingIcon = R.drawable.settings_wallet_filled,
                trailingIcon = R.drawable.icon_chevron_right_small,
            )
        )

    data object AddressBook :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.address_book_settings_title),
                leadingIcon = R.drawable.book_bookmark_filled,
                trailingIcon = R.drawable.icon_chevron_right_small,
            )
        )


    data object Faq :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.faq_setting_screen_title),
                leadingIcon = R.drawable.square_question,
                trailingIcon = R.drawable.icon_chevron_right_small,
            )
        )

    data object CheckForUpdates :
        SettingsItem(
            value =
                SettingsItemUiModel(
                    title = UiText.StringResource(R.string.check_updates_settings_title),
                    leadingIcon = R.drawable.cloud_upload,
                    trailingIcon = R.drawable.icon_chevron_right_small,
                ),
            enabled = true,
        )

    data object Education :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.settings_screen_vultisig_education),
                leadingIcon = R.drawable.book_bookmark_filled,
                trailingIcon = R.drawable.icon_chevron_right_small,
            )
        )

    /**
     * Links out to the Play listing instead of triggering the in-app review flow: Play's policy
     * forbids a button from launching that flow, and the flow silently no-ops once its quota is
     * spent, which would leave the row looking broken.
     */
    data object RateTheApp :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.settings_screen_rate_the_app),
                leadingIcon = R.drawable.sparkle,
                // The sparkle is two-tone; tinting would flatten it.
                leadingIconTint = Color.Unspecified,
                trailingIcon = R.drawable.icon_chevron_right_small,
            )
        )

    data object ShareTheApp :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.settings_screen_share_the_app),
                leadingIcon = R.drawable.share_up_right_2,
                trailingIcon = R.drawable.icon_chevron_right_small,
            )
        )

    data object Twitter :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.settings_community_x),
                leadingIcon = R.drawable.socials_twitter,
            )
        )

    data object Discord :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.discord),
                leadingIcon = R.drawable.socials_discord,
            )
        )

    data object Github :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.github),
                leadingIcon = R.drawable.socials_github,
            )
        )

    data object VultisigWebsite :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.settings_community_website),
                leadingIcon = R.drawable.globe_2,
            )
        )

    data object TermsOfService :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.settings_screen_tos),
                leadingIcon = R.drawable.note,
                trailingIcon = R.drawable.arrows_chevron_right,
            )
        )

    data object PrivacyPolicy :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.settings_screen_privacy_policy),
                leadingIcon = R.drawable.shield_filled,
                trailingIcon = R.drawable.arrows_chevron_right,
            )
        )

    data object Notifications :
        SettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.notifications),
                leadingIcon = R.drawable.bell_filled,
                trailingIcon = R.drawable.icon_chevron_right_small,
            )
        )


}

internal data class SettingsItemUiModel(
    val title: UiText,
    val titleBadge: UiText? = null,
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

/** The main settings list before the language and currency values load. */
internal val defaultSettingsMenu =
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
                            Language("English"),
                            Currency("USD"),
                            HomeDisplay,
                            AddressBook,
                        ),
                ),
                SettingsGroupUiModel(
                    title = UiText.StringResource(R.string.vultisig_community),
                    tiles = listOf(Twitter, Discord, Github, VultisigWebsite),
                    items = listOf(RateTheApp, ShareTheApp),
                ),
                SettingsGroupUiModel(
                    title = UiText.StringResource(R.string.support),
                    items = listOf(Faq, CheckForUpdates, Education),
                ),
                SettingsGroupUiModel(
                    title = UiText.StringResource(R.string.settings_screen_legal),
                    items = listOf(PrivacyPolicy, TermsOfService),
                ),
            )
    )

@HiltViewModel
internal class SettingsViewModel
@Inject
constructor(
    private val navigator: Navigator<Destination>,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val appLocaleRepository: AppLocaleRepository,
    savedStateHandle: SavedStateHandle,
    timeSource: TimeSource,
) : ViewModel() {
    private val _uiEvents = Channel<SettingsUiEvent>()
    val uiEvent = _uiEvents.receiveAsFlow()

    val state = MutableStateFlow(defaultSettingsMenu)
    val vaultId = savedStateHandle.toRoute<Route.Settings>().vaultId
    private val multipleClicksDetector = MultipleClicksDetector(timeSource)

    /**
     * Opens the hidden secret settings screen once a rapid multi-tap on the version text is
     * detected.
     */
    fun onVersionClick() {
        if (multipleClicksDetector.clickAndCheckIfDetected()) {
            viewModelScope.launch { navigator.route(Route.Secret) }
        }
    }

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
            Education -> sendEvent(SettingsUiEvent.OpenLink(VsAuxiliaryLinks.VULTISIG_DOCS))
            Faq -> {
                viewModelScope.launch { navigator.route(Route.FAQSetting) }
            }
            Github -> sendEvent(SettingsUiEvent.OpenLink(VsAuxiliaryLinks.GITHUB))
            is Language -> {
                viewModelScope.launch { navigator.route(Route.LanguageSetting) }
            }

            HomeDisplay -> {
                viewModelScope.launch { navigator.route(Route.HomeDisplaySetting) }
            }

            PrivacyPolicy -> sendEvent(SettingsUiEvent.OpenLink(VsAuxiliaryLinks.PRIVACY))
            RateTheApp -> sendEvent(SettingsUiEvent.OpenLink(VsAuxiliaryLinks.GOOGLE_PLAY))
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
        }
    }

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            loadCurrency()
            loadAppLocale()
        }
    }

    private fun loadAppLocale() {
        viewModelScope.launch {
            appLocaleRepository.local.collect { locale: AppLanguage ->
                // Map inside update (not over a state.value snapshot) so a concurrent emission —
                // e.g. the currency — isn't dropped by this write.
                state.update { it.copy(items = updatedLocale(it.items, locale)) }
            }
        }
    }

    private fun updatedLocale(groups: List<SettingsGroupUiModel>, locale: AppLanguage) =
        groups.map { group ->
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
                // Map inside update (not over a state.value snapshot) so a concurrent emission —
                // e.g. the language — isn't dropped by this write.
                state.update { it.copy(items = updateCurrency(it.items, currency)) }
            }
        }
    }

    private fun updateCurrency(groups: List<SettingsGroupUiModel>, currency: AppCurrency) =
        groups.map { group ->
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

    private fun sendEvent(event: SettingsUiEvent) {
        viewModelScope.launch { _uiEvents.send(event) }
    }

    fun back() {
        viewModelScope.launch { navigator.back() }
    }

    fun onDismissShareLinkBottomSheet() {
        state.update { it.copy(showShareBottomSheet = false) }
    }

    private fun openShareLinkModalBottomSheet() {
        state.update { it.copy(showShareBottomSheet = true) }
    }
}
