package com.vultisig.wallet.ui.models.keygen

import androidx.annotation.DrawableRes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.common.UiText
import com.vultisig.wallet.common.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

internal data class KeygenSetupUiModel(
    val tabIndex: Int = 0,
    val tabs: List<KeygenSetupTabUiModel> = listOf(
        KeygenSetupTabUiModel(
            title = R.string.s_of_s_vault.asUiText("2", "2"),
            content = R.string.setup_device_of_vault.asUiText("2"),
            drawableResId = R.drawable.devices_2_2,
        ),
        KeygenSetupTabUiModel(
            title = R.string.s_of_s_vault.asUiText("2", "3"),
            content = R.string.setup_device_of_vault.asUiText("3"),
            drawableResId = R.drawable.devices_2_3,
        ),
        KeygenSetupTabUiModel(
            title = R.string.s_of_s_vault.asUiText("M", "N"),
            content = R.string.setup_device_of_vault.asUiText("N"),
            drawableResId = R.drawable.devices_m_n,
        ),
    ),
)

internal data class KeygenSetupTabUiModel(
    val title: UiText,
    val content: UiText,
    @DrawableRes val drawableResId: Int,
)

enum class VaultSetupType(val raw: Int) {
    TWO_OF_TWO(0),
    TWO_OF_THREE(1),
    M_OF_N(2);

    companion object {
        fun fromInt(value: Int) = entries.first { it.raw == value }
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