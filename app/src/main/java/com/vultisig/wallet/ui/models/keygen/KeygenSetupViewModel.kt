package com.vultisig.wallet.ui.models.keygen

import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

internal data class KeygenSetupUiModel(
    val tabIndex: Int = 0,
    val tabs: List<KeygenSetupTabUiModel> = listOf(
        KeygenSetupTabUiModel(
            title = R.string.s_of_s.asUiText("2", "2"),
            content = R.string.setup_1_device_of_vault.asUiText(),
            drawableResId = R.drawable.devices_2_2,
        ),
        KeygenSetupTabUiModel(
            title = R.string.s_of_s.asUiText("2", "3"),
            content = R.string.setup_2_device_of_vault.asUiText(),
            drawableResId = R.drawable.devices_2_3,
        ),
        KeygenSetupTabUiModel(
            title = R.string.s_of_s.asUiText("M", "N"),
            content = R.string.setup_m_device_of_vault.asUiText(),
            drawableResId = R.drawable.devices_m_n,
        ),
    ),
)

internal data class KeygenSetupTabUiModel(
    val title: UiText,
    val content: UiText,
    @DrawableRes val drawableResId: Int,
)

enum class VaultSetupType(
    val raw: Int,
    val isFast: Boolean,
) {
    SECURE(2, false), // m to n devices
    // with vultiserver
    FAST(3, true), // 1 to 1
    ACTIVE(4, true), // 2 to 1
    ;

    companion object {
        fun fromInt(value: Int): VaultSetupType = entries.first { it.raw == value }
        fun VaultSetupType.asString(): String =
            when (this) {
                SECURE -> "Secure"
                FAST -> "Fast"
                ACTIVE -> "Active"
            }
    }
}

@HiltViewModel
internal class KeygenSetupViewModel @Inject constructor() : ViewModel() {

    val uiModel = MutableStateFlow(KeygenSetupUiModel())

    fun selectTab(index: Int) {
        uiModel.update {
            it.copy(tabIndex = index)
        }
    }

}