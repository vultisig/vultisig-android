package com.vultisig.wallet.ui.models.send

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import com.vultisig.wallet.R.string
import com.vultisig.wallet.data.blockchain.tron.TronResourceType
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.ui.models.send.AmountFraction.F100
import com.vultisig.wallet.ui.models.send.AmountFraction.F25
import com.vultisig.wallet.ui.models.send.AmountFraction.F50
import com.vultisig.wallet.ui.models.send.AmountFraction.F75
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import java.math.BigInteger

@Immutable
internal data class TokenBalanceUiModel(
    val model: SendSrc,
    val title: String,
    val balance: String?,
    val fiatValue: String?,
    val isNativeToken: Boolean,
    val isLayer2: Boolean,
    val tokenStandard: String?,
    val tokenLogo: ImageModel,
    @param:DrawableRes val chainLogo: Int,
)

sealed class AmountFraction(val title: UiText, val value: Float) {
    data object F25 : AmountFraction(title = "25%".asUiText(), value = 0.25f)

    data object F50 : AmountFraction(title = "50%".asUiText(), value = 0.5f)

    data object F75 : AmountFraction(title = "75%".asUiText(), value = 0.75f)

    data object F100 : AmountFraction(title = string.send_screen_max.asUiText(), value = 1f)
}

@Immutable
internal data class SendFormUiModel(
    val selectedCoin: TokenBalanceUiModel? = null,
    val fiatCurrency: String = "",

    // src data
    val srcAddress: String = "",
    val srcVaultName: String = "",

    // dst data
    val isDstAddressComplete: Boolean = false,

    // fees
    val totalGas: UiText = UiText.Empty,
    val gasTokenBalance: UiText? = null,
    val estimatedFee: UiText = UiText.Empty,

    // type
    val defiType: DeFiNavActions? = null,
    val slippage: String = "1.0",
    val isAutocompound: Boolean = false,

    // errors
    val errorText: UiText? = null,
    val dstAddressError: UiText? = null,
    val tokenAmountError: UiText? = null,
    val reapingError: UiText? = null,
    val bondProviderError: UiText? = null,
    val hasMemo: Boolean = false,
    val showGasFee: Boolean = true,
    val hasGasSettings: Boolean = false,
    val showGasSettings: Boolean = false,
    val specific: BlockChainSpecificAndUtxo? = null,
    val expandedSection: SendSections = SendSections.Asset,
    val usingTokenAmountInput: Boolean = true,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isAmountSelectionLoading: Boolean = false,
    val selectedAmountFraction: AmountFraction? = null,
    val amountFractionEntries: List<AmountFraction> = listOf(F25, F50, F75, F100),

    // Tron freeze/unfreeze
    val tronResourceType: TronResourceType? = null,
    val tronBalanceAvailableOverride: String? = null,
    val isTronFrozenBalancesLoading: Boolean = false,
    val hasTronFrozenBalancesError: Boolean = false,
)

internal data class SendSrc(val address: Address, val account: Account)

internal enum class SendSections {
    Asset,
    Address,
    Amount,
}

internal enum class SendFocusField {
    ADDRESS,
    AMOUNT,
}

enum class AddressBookType {
    OUTPUT,
    PROVIDER,
}

internal sealed class GasSettings {
    data class Eth(val baseFee: BigInteger, val priorityFee: BigInteger, val gasLimit: BigInteger) :
        GasSettings()

    data class UTXO(val byteFee: BigInteger) : GasSettings()
}

internal data class InvalidTransactionDataException(val text: UiText) : Exception()
