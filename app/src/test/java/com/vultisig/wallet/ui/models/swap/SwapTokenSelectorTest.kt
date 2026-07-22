@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class SwapTokenSelectorTest {

    private lateinit var navigator: Navigator<Destination>
    private lateinit var accountsRepository: AccountsRepository
    private lateinit var requestResultRepository: RequestResultRepository
    private lateinit var accountToTokenBalanceUiModelMapper: AccountToTokenBalanceUiModelMapper
    private lateinit var selector: SwapTokenSelector

    @BeforeEach
    fun setUp() {
        navigator = mockk(relaxed = true)
        accountsRepository = mockk(relaxed = true)
        requestResultRepository = mockk(relaxed = true)
        accountToTokenBalanceUiModelMapper = mockk(relaxed = true)
        selector =
            SwapTokenSelector(
                navigator = navigator,
                accountsRepository = accountsRepository,
                requestResultRepository = requestResultRepository,
                accountToTokenBalanceUiModelMapper = accountToTokenBalanceUiModelMapper,
            )

        // No selection made — request() returns null, so checkTokenSelectionResponse()
        // short-circuits.
        coEvery { requestResultRepository.request<Any?>(any()) } returns null
    }

    @Test
    fun `navigateToSelectToken requests a response keyed by the same id it navigated with, not the constant target arg`() =
        runTest {
            val routeSlot = slot<Any>()
            coEvery { navigator.route(capture(routeSlot)) } returns Unit

            selector.navigateToSelectToken(
                targetArg = SwapTokenSelector.ARG_SELECTED_DST_TOKEN_ID,
                vaultId = VAULT_ID,
                selectedSrc = sendSrc(),
                selectedDst = null,
                selectedSrcId = MutableStateFlow(null),
                selectedDstId = MutableStateFlow(null),
                addresses = MutableStateFlow(emptyList()),
                uiState = MutableStateFlow(SwapFormUiModel()),
                isSelectionQuotable = { true },
            )

            val navigatedRequestId = (routeSlot.captured as Route.SelectAsset).requestId
            assertNotEquals(SwapTokenSelector.ARG_SELECTED_DST_TOKEN_ID, navigatedRequestId)
            coVerify(exactly = 1) { requestResultRepository.request<Any?>(navigatedRequestId) }
        }

    @Test
    fun `two visits to the picker use different request ids`() = runTest {
        val routeSlot = slot<Any>()
        coEvery { navigator.route(capture(routeSlot)) } returns Unit

        selector.navigateToSelectToken(
            targetArg = SwapTokenSelector.ARG_SELECTED_DST_TOKEN_ID,
            vaultId = VAULT_ID,
            selectedSrc = sendSrc(),
            selectedDst = null,
            selectedSrcId = MutableStateFlow(null),
            selectedDstId = MutableStateFlow(null),
            addresses = MutableStateFlow(emptyList()),
            uiState = MutableStateFlow(SwapFormUiModel()),
            isSelectionQuotable = { true },
        )
        val firstRequestId = (routeSlot.captured as Route.SelectAsset).requestId

        selector.navigateToSelectToken(
            targetArg = SwapTokenSelector.ARG_SELECTED_DST_TOKEN_ID,
            vaultId = VAULT_ID,
            selectedSrc = sendSrc(),
            selectedDst = null,
            selectedSrcId = MutableStateFlow(null),
            selectedDstId = MutableStateFlow(null),
            addresses = MutableStateFlow(emptyList()),
            uiState = MutableStateFlow(SwapFormUiModel()),
            isSelectionQuotable = { true },
        )
        val secondRequestId = (routeSlot.captured as Route.SelectAsset).requestId

        assertNotEquals(firstRequestId, secondRequestId)
    }

    private fun sendSrc(): SendSrc {
        val coin =
            Coin(
                chain = Chain.Ethereum,
                ticker = "ETH",
                logo = "",
                address = "0xabc",
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "",
                isNativeToken = true,
            )
        return SendSrc(
            address = Address(chain = Chain.Ethereum, address = "0xabc", accounts = emptyList()),
            account = Account(token = coin, tokenValue = null, fiatValue = null, price = null),
        )
    }

    companion object {
        private const val VAULT_ID = "vault-1"
    }
}
