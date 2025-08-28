package com.vultisig.wallet.ui.models.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.BuildConfig
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class CheckForUpdateUiModel(
    val isUpdateAvailable: Boolean = true,
    val currentVersion: String = "",
)

@HiltViewModel
internal class CheckForUpdateViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val state = MutableStateFlow(
        CheckForUpdateUiModel(
            currentVersion = context.getString(
                R.string.keysign_app_version_text,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE.toString(),
            )
        )
    )

    init {
        checkUpdate()
    }

    private fun checkUpdate() {

    }


    fun update() {

    }


    fun back() {
        viewModelScope.launch {
            navigator.back()
        }
    }
}