package com.vultisig.wallet.ui.models.deposit.submit

import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.ui.models.deposit.DepositFieldStates
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.deposit.load.DepositAmountHelper
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope

/**
 * The form's real amount gate for [chain], so a strategy test runs the same amount + gas check
 * `DepositFormViewModel` hands the strategy.
 */
internal fun amountGate(chain: Chain, fields: DepositFieldStates): DepositAmountHelper =
    DepositAmountHelper(
        mapTokenValueToStringWithUnit = mockk(relaxed = true),
        gasFeeHelper = mockk(relaxed = true),
        scope = TestScope(),
        fields = fields,
        appCurrency = MutableStateFlow(AppCurrency.USD),
        state = MutableStateFlow(DepositFormUiModel()),
        chain = { chain },
        vaultId = { GATE_VAULT_ID },
    )

internal fun gateCoin(
    chain: Chain,
    ticker: String,
    address: String,
    isNative: Boolean = true,
    decimal: Int = 8,
): Coin =
    Coin(
        chain = chain,
        ticker = ticker,
        logo = "",
        address = address,
        decimal = decimal,
        hexPublicKey = "",
        priceProviderID = "",
        contractAddress = if (isNative) "" else ticker.lowercase(),
        isNativeToken = isNative,
    )

internal fun gateAccount(coin: Coin, balance: BigInteger?): Account =
    Account(
        token = coin,
        tokenValue = balance?.let { TokenValue(it, coin) },
        fiatValue = null,
        price = null,
    )

internal fun gateAddress(vararg accounts: Account): Address =
    Address(
        chain = accounts.first().token.chain,
        address = accounts.first().token.address,
        accounts = accounts.toList(),
    )

internal fun gateEstimatedFee(gasFee: TokenValue) =
    EstimatedGasFee(
        formattedFiatValue = "$0.01",
        formattedTokenValue = "0.0001",
        tokenValue = gasFee,
        fiatValue = mockk(relaxed = true),
    )

internal fun assertInsufficientBalance(error: InvalidTransactionDataException) {
    val text = error.text
    assertTrue(text is UiText.StringResource)
    assertEquals(R.string.send_error_insufficient_balance, text.resId)
}

internal const val GATE_VAULT_ID = "vault-1"
