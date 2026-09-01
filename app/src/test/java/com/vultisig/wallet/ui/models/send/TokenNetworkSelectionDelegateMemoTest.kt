@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalStdlibApi::class)

package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class TokenNetworkSelectionDelegateMemoTest {

    private val selectedToken = MutableStateFlow<Coin?>(Coins.Ethereum.ETH)
    private val memoFieldState = TextFieldState()

    @Test
    fun `selecting a token whose signer drops memos clears the memo`() = runTest {
        memoFieldState.setTextAndPlaceCursorAtEnd("exchange memo")

        delegate().selectToken(Coins.Ethereum.USDC)

        assertEquals("", memoFieldState.text.toString())
    }

    @Test
    fun `selecting a token whose signer carries memos keeps the memo`() = runTest {
        memoFieldState.setTextAndPlaceCursorAtEnd("exchange memo")

        delegate().selectToken(Coins.Ton.USDT)

        assertEquals("exchange memo", memoFieldState.text.toString())
    }

    private fun delegate(): TokenNetworkSelectionDelegate =
        TokenNetworkSelectionDelegate(
            scope = mockk(relaxed = true),
            navigator = mockk(relaxed = true),
            requestResultRepository = mockk(relaxed = true),
            tokenRepository = mockk(relaxed = true),
            vaultRepository = mockk(relaxed = true),
            chainAccountAddressRepository = mockk(relaxed = true),
            tokenPreselectionService = mockk(relaxed = true),
            accountsLoader = mockk(relaxed = true),
            amountFractionManager = mockk(relaxed = true),
            amountManager = mockk(relaxed = true),
            vaultIdProvider = { null },
            accounts = MutableStateFlow(emptyList<Account>()),
            selectedToken = selectedToken,
            addressFieldState = TextFieldState(),
            memoFieldState = memoFieldState,
            uiState = MutableStateFlow(SendFormUiModel()),
            isSwitchingAccounts = MutableStateFlow(false),
            defiTypeProvider = { null },
            expandSection = {},
        )
}
