@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.usecases.EnableTokenUseCase
import com.vultisig.wallet.ui.utils.UiText
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class TokenNetworkSelectionDelegateCustomTokenTest {

    private val uiState = MutableStateFlow(SendFormUiModel())
    private val requestResultRepository: RequestResultRepository = mockk()
    private val enableToken: EnableTokenUseCase = mockk()

    @Test
    fun `a custom token that fails to enable shows an error instead of nothing`() = runTest {
        coEvery { requestResultRepository.request<Coin>(any()) } returns Coins.Ethereum.USDC
        coEvery { enableToken(any(), any()) } throws IllegalStateException("db write failed")
        var added: Coin? = null

        delegate(this).addCustomToken(Chain.Ethereum) { added = it }
        advanceUntilIdle()

        uiState.value.errorText shouldBe UiText.StringResource(R.string.dialog_default_error_body)
        added.shouldBeNull()
    }

    @Test
    fun `a custom token that enables is handed on without an error`() = runTest {
        coEvery { requestResultRepository.request<Coin>(any()) } returns Coins.Ethereum.USDC
        coEvery { enableToken(any(), any()) } returns null
        var added: Coin? = null

        delegate(this).addCustomToken(Chain.Ethereum) { added = it }
        advanceUntilIdle()

        uiState.value.errorText.shouldBeNull()
        added shouldBe Coins.Ethereum.USDC
    }

    private fun delegate(scope: CoroutineScope): TokenNetworkSelectionDelegate =
        TokenNetworkSelectionDelegate(
            scope = scope,
            navigator = mockk(relaxed = true),
            requestResultRepository = requestResultRepository,
            tokenRepository = mockk(relaxed = true),
            enableToken = enableToken,
            vaultRepository = mockk(relaxed = true),
            chainAccountAddressRepository = mockk(relaxed = true),
            tokenPreselectionService = mockk(relaxed = true),
            accountsLoader = mockk(relaxed = true),
            amountFractionManager = mockk(relaxed = true),
            amountManager = mockk(relaxed = true),
            vaultIdProvider = { "vault-1" },
            accounts = MutableStateFlow(emptyList<Account>()),
            selectedToken = MutableStateFlow<Coin?>(Coins.Ethereum.ETH),
            addressFieldState = TextFieldState(),
            memoFieldState = TextFieldState(),
            uiState = uiState,
            isSwitchingAccounts = MutableStateFlow(false),
            defiTypeProvider = { null },
            onRecipientPicked = {},
        )
}
